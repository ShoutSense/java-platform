package com.shoutsense.workflow.functions;

import java.util.Map;

import javax.ws.rs.core.Response.Status;

import com.amazonaws.serverless.proxy.internal.model.ApiGatewayRequestContext;
import com.amazonaws.serverless.proxy.internal.model.ApiGatewayRequestIdentity;
import com.amazonaws.serverless.proxy.internal.model.AwsProxyRequest;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.shoutsense.auth.utils.CognitoUtils;
import com.shoutsense.workflow.utils.GatewayResponse;

public class CampaignCreationLambda implements RequestHandler<AwsProxyRequest, GatewayResponse> {

	@Override
	public final GatewayResponse handleRequest(AwsProxyRequest input, Context context) {
		context.getLogger().log("Input(" + input.getClass() + "): " + input);

		ApiGatewayRequestContext requestContext = input.getRequestContext();
		String body = input.getBody();
		System.out.println("body: " + body);
		JsonNode bodyJson = Jackson.jsonNodeOf(body);
		String tweetText = bodyJson.get("tweet_text").asText();
		long now = System.currentTimeMillis();
		ApiGatewayRequestIdentity identityInfo = requestContext.getIdentity();
		System.out.println(
				"identityInfo: " + identityInfo.getUser() + "::" + identityInfo.getCognitoAuthenticationType() + "::"
						+ identityInfo.getUserArn() + "::" + identityInfo.getCognitoIdentityId());
		String cognitoIdentity = identityInfo.getCognitoIdentityId();
		String cognitoIdentityPool = identityInfo.getCognitoIdentityPoolId();
		String campaignId = Hashing.sha256().hashString(now + "_" + cognitoIdentity + "_" + tweetText, Charsets.UTF_8)
				.toString().substring(0, 16);

		String advertiserAccountId = CognitoUtils.fetchAdvertiserAccount(cognitoIdentity, cognitoIdentityPool);
		if (advertiserAccountId == null) {
			return GatewayResponse.badRequest("user appears to be misconfigured, abandoned request");
		}
		AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
		PutItemRequest putItemRequest = new PutItemRequest();
		putItemRequest.setTableName("campaigns");
		AttributeValue nowAttribute = new AttributeValue().withN("" + now);
		putItemRequest.addItemEntry("creationTimestamp", nowAttribute);
		putItemRequest.addItemEntry("campaignId", new AttributeValue(campaignId));
		putItemRequest.addItemEntry("advertiserAccountId", new AttributeValue(advertiserAccountId));
		putItemRequest.addItemEntry("status", new AttributeValue("NEW"));
		dynamoDBClient.putItem(putItemRequest);

		Map<String, String> headers = Maps.newHashMap();

		// since we have lambda-proxy enabled, you need to set the CORS headers manually
		headers.put("Access-Control-Allow-Origin", "*");
		headers.put("Content-Type", "application/json");
		Map<String, String> response = Maps.newHashMap();
		response.put("new_campaign_id", campaignId);
		return new GatewayResponse(response, headers, Status.OK, false);

	}
}

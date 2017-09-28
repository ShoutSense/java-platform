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
import com.google.common.collect.Maps;
import com.shoutsense.auth.utils.CognitoUtils;
import com.shoutsense.workflow.utils.GatewayResponse;

public class AdvertiserAccountValidationRequestRecorderLambda
		implements RequestHandler<AwsProxyRequest, GatewayResponse> {

	@Override
	public final GatewayResponse handleRequest(AwsProxyRequest input, Context context) {
		context.getLogger().log("Input(" + input.getClass() + "): " + input);

		ApiGatewayRequestContext requestContext = input.getRequestContext();
		String body = input.getBody();
		System.out.println("body: " + body);
		JsonNode bodyJson = Jackson.jsonNodeOf(body);
		String transactionAddress = bodyJson.get("transaction").asText();
		String blockchainNetwork = bodyJson.get("network").asText();
		String transactionType = bodyJson.get("type").asText();
		if (!transactionType.equals("advertiser_account_validation")) {
			return GatewayResponse.badRequest("only type 'advertiser_account_validation' allowed");
		}
		long now = System.currentTimeMillis();
		ApiGatewayRequestIdentity identityInfo = requestContext.getIdentity();
		System.out.println(
				"identityInfo: " + identityInfo.getUser() + "::" + identityInfo.getCognitoAuthenticationType() + "::"
						+ identityInfo.getUserArn() + "::" + identityInfo.getCognitoIdentityId());
		String cognitoIdentity = identityInfo.getCognitoIdentityId();
		String cognitoIdentityPool = identityInfo.getCognitoIdentityPoolId();
		String advertiserAccountId = CognitoUtils.fetchAdvertiserAccount(cognitoIdentity, cognitoIdentityPool);
		if (advertiserAccountId == null) {
			return GatewayResponse.badRequest("user appears to be misconfigured, abandoned request");
		}
		AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
		PutItemRequest putItemRequest = new PutItemRequest();
		putItemRequest.setTableName("transactions");
		AttributeValue nowAttribute = new AttributeValue().withN("" + now);
		putItemRequest.addItemEntry("creationTimestamp", nowAttribute);
		putItemRequest.addItemEntry("address", new AttributeValue(transactionAddress));
		putItemRequest.addItemEntry("network", new AttributeValue(blockchainNetwork));
		putItemRequest.addItemEntry("type", new AttributeValue(transactionType));
		putItemRequest.addItemEntry("userCognitoId", new AttributeValue(cognitoIdentity));
		putItemRequest.addItemEntry("userCognitoIdPool", new AttributeValue(cognitoIdentityPool));
		Map<String, String> metadataMap = Maps.newHashMap();
		metadataMap.put("advertiser_account", advertiserAccountId);
		String metadata = Jackson.toJsonString(metadataMap);
		putItemRequest.addItemEntry("metadata", new AttributeValue(metadata));
		putItemRequest.addItemEntry("status", new AttributeValue("NEW"));
		dynamoDBClient.putItem(putItemRequest);

		// since we have lambda-proxy enabled, you need to set the CORS headers manually
		Map<String, String> headers = Maps.newHashMap();
		headers.put("Access-Control-Allow-Origin", "*");
		headers.put("Content-Type", "application/json");
		Map<String, String> response = Maps.newHashMap();
		response.put("message", "transaction will be monitored");
		return new GatewayResponse(response, headers, Status.OK, false);
	}
}

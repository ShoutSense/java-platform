package com.shoutsense.workflow.functions;

import java.util.Map;
import java.util.function.Consumer;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanFilter;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class CampaignFundingMonitorLambda implements RequestHandler<Map<String, Object>, Object> {

	@SuppressWarnings("unchecked")
	@Override
	public final Object handleRequest(Map<String, Object> input, Context context) {
		context.getLogger().log("Input(" + input.getClass() + "): " + input);

		AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
		DynamoDB db = new DynamoDB(dynamoDBClient);
		Table campaignsTable = db.getTable("campaigns");
		Index statusIndex = campaignsTable.getIndex("status-creationTimestamp-index");

		ScanFilter filter = new ScanFilter("status").eq("NEW");
		ItemCollection<ScanOutcome> results = statusIndex.scan(filter);
		if (results != null) {
			results.forEach(new Consumer<Item>() {

				@Override
				public void accept(Item item) {
					String campaignId = item.getString("campaignId");
					/*
					 * CloseableHttpClient httpclient = HttpClientBuilder.create().build();
					 * HttpGet httpGet = new HttpGet("http://names.example.com/api/");
					 * CloseableHttpResponse response = httpclient.execute(httpGet);
					 * try {
					 * HttpEntity entity = response.getEntity();
					 * InputStream inputStream = entity.getContent();
					 * ObjectMapper mapper = new ObjectMapper();
					 * Map<String, String> jsonMap = mapper.readValue(inputStream, Map.class);
					 * String name = jsonMap.get("name");
					 * EntityUtils.consume(entity);
					 * return name;
					 * }
					 * finally {
					 * response.close();
					 * }
					 */
				}
			});
		}
		return null;
	}
}

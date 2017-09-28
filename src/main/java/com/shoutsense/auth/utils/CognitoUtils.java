package com.shoutsense.auth.utils;

import java.util.List;

import com.amazonaws.services.cognitosync.AmazonCognitoSync;
import com.amazonaws.services.cognitosync.AmazonCognitoSyncClientBuilder;
import com.amazonaws.services.cognitosync.model.Dataset;
import com.amazonaws.services.cognitosync.model.ListDatasetsRequest;
import com.amazonaws.services.cognitosync.model.ListDatasetsResult;
import com.amazonaws.services.cognitosync.model.ListRecordsRequest;
import com.amazonaws.services.cognitosync.model.ListRecordsResult;
import com.amazonaws.services.cognitosync.model.Record;

public class CognitoUtils {

	private CognitoUtils() {
	}

	public static String fetchAdvertiserAccount(String cognitoIdentity, String cognitoIdentityPool) {
		AmazonCognitoSync cognitoClient = AmazonCognitoSyncClientBuilder.defaultClient();
		ListDatasetsRequest listDatasetsRequest = new ListDatasetsRequest();
		listDatasetsRequest.setIdentityId(cognitoIdentity);
		listDatasetsRequest.setIdentityPoolId(cognitoIdentityPool);
		ListDatasetsResult datasetsResult = cognitoClient.listDatasets(listDatasetsRequest);
		List<Dataset> datasets = datasetsResult.getDatasets();
		for (Dataset dataset : datasets) {
			if (dataset.getDatasetName().equals("ShoutsensePublicDataset")) {
				ListRecordsRequest listRecordsRequest = new ListRecordsRequest();
				listRecordsRequest.setDatasetName("ShoutsensePublicDataset");
				listRecordsRequest.setIdentityId(cognitoIdentity);
				listRecordsRequest.setIdentityPoolId(cognitoIdentityPool);
				ListRecordsResult recordsResponse = cognitoClient.listRecords(listRecordsRequest);
				List<Record> records = recordsResponse.getRecords();
				for (Record record : records) {
					if (record.getKey().equals("advertiser_account_id")) {
						String advertiserAccountId = record.getValue();
						return advertiserAccountId;
					}
				}
			}
		}
		return null;
	}
}

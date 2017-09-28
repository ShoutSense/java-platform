package com.shoutsense.auth.functions;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CognitoEvent;
import com.amazonaws.services.lambda.runtime.events.CognitoEvent.DatasetRecord;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class UpdateDatasetWithAdditionalCredentialInfoLambda implements RequestHandler<CognitoEvent, Object> {

	private static TwitterFactory twitterFactory;

	private static void setupTwitter() {
		if (twitterFactory != null) {
			return;
		}
		ConfigurationBuilder cb = new ConfigurationBuilder();
		String consumerKey = System.getenv("twitter_consumerKey");
		String consumerSecret = System.getenv("twitter_consumerSecret");

		// @formatter:off
		cb.setDebugEnabled(true)
			.setApplicationOnlyAuthEnabled(false)
			.setOAuthConsumerKey(consumerKey)
			.setOAuthConsumerSecret(consumerSecret);
		//@formatter:on

		Configuration conf = cb.build();
		twitterFactory = new TwitterFactory(conf);
	}

	@Override
	public final Object handleRequest(CognitoEvent event, Context context) {

		context.getLogger().log("cognito sync event: " + event);
		try {
			System.out.println(
					"update creds event - id: " + event.getIdentityId() + " dataset: " + event.getDatasetName()
							+ " event type: " + event.getEventType());
			if (event.getDatasetName().equals("ShoutsensePublicDataset")) {
				System.out.println("dataset: " + event.getDatasetRecords());
				Map<String, DatasetRecord> records = event.getDatasetRecords();
				if (records != null) {
					DatasetRecord tokenRecord = records.get("twitter_accesstoken");
					if (tokenRecord != null) {
						String twitterAccessToken = tokenRecord.getNewValue();
						if (twitterAccessToken != null && twitterAccessToken.length() != 0) {
							String twitterScreenname = getTwitterScreenname(event, context, twitterAccessToken);

							// set the twitter screen name
							DatasetRecord record = records.get("twitter_screenname");
							if (record == null) {
								record = new DatasetRecord();
								records.put("twitter_screenname", record);
							}
							record.setNewValue(twitterScreenname);

							String cognitoId = event.getIdentityId();

							// set the advertiser account id if it hasn't been validated yet
							record = records.get("advertiser_account_validated");
							if (record == null) {
								record = new DatasetRecord();
								records.put("advertiser_account_validated", record);
							}
							String validatedString = record.getNewValue();
							if (validatedString == null || !validatedString.equals("true")) {
								record.setNewValue("false");

								String advertiserAccountId = "0x" + Hashing.sha256()
										.hashString("advertiser-account-" + cognitoId, Charsets.UTF_8).toString();
								record = records.get("advertiser_account_id");
								if (record == null) {
									record = new DatasetRecord();
									records.put("advertiser_account_id", record);
								}
								record.setNewValue(advertiserAccountId);
							}

							// set the publisher account id
							record = records.get("publisher_account_validated");
							if (record == null) {
								record = new DatasetRecord();
								records.put("publisher_account_validated", record);
							}
							validatedString = record.getNewValue();
							if (validatedString == null || !validatedString.equals("true")) {
								record.setNewValue("false");
								String publisherAccountId = "0x" + Hashing.sha256()
										.hashString("publisher-account-" + cognitoId, Charsets.UTF_8).toString();
								record = records.get("publisher_account_id");
								if (record == null) {
									record = new DatasetRecord();
									records.put("publisher_account_id", record);
								}
								record.setNewValue(publisherAccountId);
							}
						}
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return event;
	}

	private String getTwitterScreenname(CognitoEvent event, Context context, String twitterAccessToken)
			throws IllegalStateException, TwitterException {
		setupTwitter();
		String[] tokens = twitterAccessToken.split(";");
		System.out.println("twitter tokens: " + tokens[0] + " AND " + tokens[1]);
		AccessToken accessToken = new AccessToken(tokens[0], tokens[1]);
		Twitter twitter = twitterFactory.getInstance(accessToken);
		String screenName = twitter.getScreenName();
		System.out.println("twitter screenname: " + screenName);

		return screenName;
	}
}

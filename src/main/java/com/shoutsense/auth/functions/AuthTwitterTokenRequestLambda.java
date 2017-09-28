package com.shoutsense.auth.functions;

import java.util.Map;

import javax.ws.rs.core.Response.Status;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.shoutsense.workflow.utils.GatewayResponse;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class AuthTwitterTokenRequestLambda implements RequestHandler<Map<String, Object>, GatewayResponse> {

	private static TwitterFactory twitterFactory;
	private static String tokenRequestRedirectUrl;
	private static String tokenAuthorizedRedirectUrl;

	private static void setupTwitter() {
		if (twitterFactory != null) {
			return;
		}
		tokenRequestRedirectUrl = System.getenv("twitter_tokenRequestRedirectUrl");
		tokenAuthorizedRedirectUrl = System.getenv("twitter_tokenAuthorizedRedirectUrl");
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
	public final GatewayResponse handleRequest(Map<String, Object> input, Context context) {
		context.getLogger().log("Input(" + input.getClass() + "): " + input);

		setupTwitter();

		Twitter twitter = twitterFactory.getInstance();

		if (input.get("path").equals("/auth/twitter/request_token")) {
			return fetchRequestToken(twitter, input, context);
		}
		if (input.get("path").equals("/auth/twitter/request_token_redirect")) {
			return validateAccessToken(twitter, input, context);
		}
		return GatewayResponse.notFound("not valid path: " + input.get("path"));
	}

	private GatewayResponse validateAccessToken(Twitter twitter, Map<String, Object> input, Context context) {
		@SuppressWarnings("unchecked")
		Map<String, String> queryStringParameters = (Map<String, String>) input.get("queryStringParameters");
		String oauthToken = queryStringParameters.get("oauth_token");
		String oauthVerifier = queryStringParameters.get("oauth_verifier");

		try {
			AccessToken accessToken = twitter
					.getOAuthAccessToken(new RequestToken(oauthToken, oauthVerifier), oauthVerifier);
			System.out.println(
					"AccessToken => " + accessToken + "\nPublicToken: " + accessToken.getToken() + "\nPrivateToken: "
							+ accessToken.getTokenSecret());
			Map<String, String> headers = Maps.newHashMap();
			headers.put(
					"Location", tokenAuthorizedRedirectUrl + "?oauth_token=" + accessToken.getToken()
							+ "&oauth_verifier=" + accessToken.getTokenSecret());

			Map<String, String> response = Maps.newHashMap();
			response.put("access_token", accessToken.getToken() + "," + accessToken.getTokenSecret());
			response.put("oauth_token", accessToken.getToken());
			response.put("oauth_verifier", accessToken.getTokenSecret());
			return new GatewayResponse(response, headers, Status.FOUND, false);
		}
		catch (TwitterException e) {
			return GatewayResponse.internalError("Exception Occurred", e);
		}
	}

	private final GatewayResponse fetchRequestToken(Twitter twitter, Map<String, Object> input, Context context) {
		try {

			String oauthToken = null;
			String oauthVerifier = null;

			@SuppressWarnings("unchecked")
			Map<String, Object> bodyParameters = new ObjectMapper().readValue((String) input.get("body"), Map.class);

			if (bodyParameters != null) {
				oauthToken = (String) bodyParameters.get("oauth_token");
				oauthVerifier = (String) bodyParameters.get("oauth_verifier");
			}

			if (oauthToken == null || oauthVerifier == null) {
				System.out.println("Fetching Request Token...");

				RequestToken requestToken = twitter.getOAuthRequestToken(tokenRequestRedirectUrl);

				System.out.println(
						"RequestToken => " + requestToken + "\nAuthenticationURL: "
								+ requestToken.getAuthenticationURL() + "\nAuthorizationURL: "
								+ requestToken.getAuthorizationURL());

				oauthToken = requestToken.getToken();
				oauthVerifier = requestToken.getTokenSecret();
			}

			Map<String, String> headers = Maps.newHashMap();

			// since we have lambda-proxy enabled, you need to set the CORS headers manually
			headers.put("Access-Control-Allow-Origin", "*");
			headers.put("Access-Control-Allow-Credentials", "true");
			headers.put("Content-Type", "application/json");

			Map<String, String> response = Maps.newHashMap();
			if (oauthVerifier != null) {
				response.put("access_token", oauthToken + ";" + oauthVerifier);
				response.put("oauth_token", oauthToken);
				response.put("oauth_verifier", oauthVerifier);
			}
			else {
				response.put("oauth_token", oauthToken);
			}

			// response.put("access_token", oauthToken);
			// return new GatewayResponse(response, headers, Status.FOUND, false);
			return new GatewayResponse(response, headers, Status.OK, false);
		}
		catch (Exception e) {
			return GatewayResponse.internalError("Exception Occurred", e);
		}

	}

}

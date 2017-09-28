package com.shoutsense.workflow.utils;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;

import com.amazonaws.util.json.Jackson;
import com.google.common.collect.Maps;

/**
 * The response that will be the passed back to the API Gateway.
 * <p>
 * The implementation depends on the AWS API Gateway response template and
 * is designed to get serialized to it.
 *
 */
public final class GatewayResponse {

	private final Object body;
	private final Map<String, String> headers;
	private final int statusCode;
	private final boolean base64Encoded;

	public GatewayResponse(Object body, Map<String, String> headers, StatusType statusType, boolean base64Encoded) {
		requireNonNull(headers);
		requireNonNull(statusType);
		this.statusCode = statusType.getStatusCode();
		this.body = body;
		this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
		this.base64Encoded = base64Encoded;
	}

	public String getBody() {
		return Jackson.toJsonString(body);
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public int getStatusCode() {
		return statusCode;
	}

	// APIGW expects the property to be called "isBase64Encoded"
	public boolean isIsBase64Encoded() {
		return base64Encoded;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (other == null) {
			return false;
		}
		if (!getClass().equals(other.getClass())) {
			return false;
		}
		GatewayResponse castOther = (GatewayResponse) other;
		return Objects.equals(body, castOther.body) && Objects.equals(headers, castOther.headers)
				&& Objects.equals(statusCode, castOther.statusCode)
				&& Objects.equals(base64Encoded, castOther.base64Encoded);
	}

	@Override
	public int hashCode() {
		return Objects.hash(body, headers, statusCode, base64Encoded);
	}

	@Override
	public String toString() {
		return "GatewayResponse [body=" + body + ", headers=" + headers + ", statusCode=" + statusCode
				+ ", base64Encoded=" + base64Encoded + "]";
	}

	public static GatewayResponse badRequest(String message) {
		return new GatewayResponse(message, Maps.newHashMap(), Status.BAD_REQUEST, false);
	}

	public static GatewayResponse methodNotAllowed(String method, Object caller) {
		return new GatewayResponse("only " + method + " supported for " + caller.getClass(), Maps.newHashMap(),
				Status.METHOD_NOT_ALLOWED, false);
	}

	public static GatewayResponse ok(Object responseObject) {
		return new GatewayResponse(responseObject, Maps.newHashMap(), Status.OK, false);
	}

	public static GatewayResponse internalError(String errorMessage) {
		return internalError(errorMessage, null);
	}

	public static GatewayResponse internalError(String errorMessage, Exception e) {
		if (e != null) {
			e.printStackTrace();
		}
		return new GatewayResponse(errorMessage, Maps.newHashMap(), Status.INTERNAL_SERVER_ERROR, false);
	}

	public static GatewayResponse notFound(String message) {
		return new GatewayResponse(message, Maps.newHashMap(), Status.NOT_FOUND, false);
	}
}

package com.shoutsense.blockchain.functions;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.joda.time.Instant;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.infura.InfuraHttpService;
import org.web3j.utils.Numeric;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.CharStreams;
import com.shoutsense.blockchain.contracts.AdvertiserAccount;
import com.shoutsense.blockchain.contracts.ShoutSense;
import com.shoutsense.blockchain.utils.ContractUtils;

public class TransactionValidationMonitorLambda implements RequestHandler<Object, Object>, Consumer<Item> {

	private static Web3j web3j;
	private static String ethereumNetwork;
	private static BigInteger advertiserAccountCreationGasLimit;
	private static AmazonDynamoDB dbClient;
	private static DynamoDB db;
	private static Table transactionTable;
	private static ShoutSense shoutsenseInstance;

	private static void initialize() throws IOException, InterruptedException, ExecutionException {
		ethereumNetwork = System.getenv("ethereum_network");
		if (ethereumNetwork == null) {
			throw new NullPointerException("'ethereum_network' must be provided as an environment variable");
		}
		String infuraToken = System.getenv("infura_token");
		if (infuraToken == null) {
			throw new NullPointerException("'infura_token' must be provided as an environment variable");
		}
		String advertiserAccountCreationGasLimitStr = System.getenv("gaslimit_advertiser_account_creation");
		advertiserAccountCreationGasLimit = new BigInteger(advertiserAccountCreationGasLimitStr);

		web3j = Web3j.build(new InfuraHttpService("https://" + ethereumNetwork + ".infura.io/" + infuraToken));

		dbClient = AmazonDynamoDBClientBuilder.defaultClient();
		db = new DynamoDB(dbClient);
		String contractsFile;
		AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		if (ethereumNetwork.equals("ropsten")) {
			contractsFile = "deployed_contracts_development_infura_" + ethereumNetwork + ".json";
		}
		else {
			contractsFile = "deployed_contracts_" + ethereumNetwork + ".json";
		}
		S3Object contractsJsonObject = s3.getObject("shoutsense-private", contractsFile);
		S3ObjectInputStream contractsJsonObjectContent = contractsJsonObject.getObjectContent();
		String deployedContractsJsonStr = CharStreams.toString(new InputStreamReader(contractsJsonObjectContent));

		JsonNode deployedContractsJson = Jackson.jsonNodeOf(deployedContractsJsonStr);
		String shoutsenseAddress = deployedContractsJson.get("shoutsenseAddress").asText();
		transactionTable = db.getTable("transactions");
		Address shoutsenseContractAddress = new Address(shoutsenseAddress);
		shoutsenseInstance = ContractUtils.loadShoutSense(shoutsenseContractAddress, web3j);
		System.out.println("Shoutsense contract: " + shoutsenseInstance.getContractAddress());
		Uint256 deploymentTimeUint = shoutsenseInstance.getContractDeploymentTime().get();
		long deploymentTime = deploymentTimeUint.getValue().longValue();
		Instant deploymentInstant = new Instant(deploymentTime * 1000);
		System.out.println("Contract deployed at " + deploymentInstant);

	}

	@Override
	public final Object handleRequest(Object input, Context context) {
		context.getLogger().log("TransactionValidationMonitor - Input(" + input.getClass() + "): " + input);

		try {

			initialize();
		}
		catch (InterruptedException | ExecutionException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		Index index = transactionTable.getIndex("status-creationTimestamp-index");
		KeyAttribute hashKey = new KeyAttribute("status", "NEW");
		ItemCollection<QueryOutcome> items = index.query(hashKey);
		items.forEach(this);
		return null;
	}

	@Override
	public void accept(Item row) {
		boolean transactionIsValid = false;
		String transactionAddress = row.getString("address");
		String blockchainNetwork = row.getString("network");
		if (blockchainNetwork == null || !blockchainNetwork.equals(ethereumNetwork)) {
			System.out.println(
					"transaction " + transactionAddress + " is not on " + ethereumNetwork + ", but on "
							+ blockchainNetwork + " instead, skipping.");
			return;
		}
		try {
			System.out.println("getting receipt for " + transactionAddress);
			TransactionReceipt receipt = web3j.ethGetTransactionReceipt(transactionAddress).send().getResult();
			if (receipt.getBlockNumber() == null) {
				System.out.println("transaction " + transactionAddress + " has not been mined yet");
				return;
			}
			BigInteger gasUsed = receipt.getGasUsed();
			transactionIsValid = gasUsed.compareTo(advertiserAccountCreationGasLimit) == -1;
			if (!transactionIsValid) {
				System.out.println(
						"transaction " + transactionAddress + " is not valid, all gas consumed: " + row.toJSONPretty());
				return;
			}
			System.out.println("transaction " + transactionAddress + " is valid");
			String transactionType = row.getString("type");
			switch (transactionType) {
			case "advertiser_account_validation":
				String statusToSet = validateAdvertiserAccount(transactionAddress, receipt, row);
				updateRowStatus(transactionAddress, receipt, statusToSet);
				return;

			default:
				return;
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void updateRowStatus(String transactionAddress, TransactionReceipt receipt, String statusToSet) {
		if (statusToSet == null) {
			return;
		}
		AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
		UpdateItemRequest updateItemRequest = new UpdateItemRequest();
		updateItemRequest.setTableName("transactions");
		updateItemRequest.addKeyEntry("address", new AttributeValue(transactionAddress));
		AttributeValueUpdate nowAttribute = new AttributeValueUpdate()
				.withValue(new AttributeValue().withN("" + System.currentTimeMillis()));
		updateItemRequest.addAttributeUpdatesEntry("statusTimestamp_" + statusToSet, nowAttribute);
		AttributeValueUpdate statusAttribute = new AttributeValueUpdate().withValue(new AttributeValue(statusToSet));
		updateItemRequest.addAttributeUpdatesEntry("status", statusAttribute);
		dynamoDBClient.updateItem(updateItemRequest);

	}

	private String validateAdvertiserAccount(String transactionAddress, TransactionReceipt receipt, Item row) {
		String rowMetadata = row.getJSON("metadata");
		JsonNode metadata = Jackson.jsonNodeOf(rowMetadata);
		String advertiserAccountId = metadata.get("advertiser_account").asText();
		System.out.println("need to validate advertiser account " + advertiserAccountId);
		byte[] bytes = Numeric.hexStringToByteArray(advertiserAccountId);
		try {
			Address accountAddress = shoutsenseInstance.getAdvertiserAccount(new Bytes32(bytes)).get();
			AdvertiserAccount account = ContractUtils.loadAdvertiserAccount(accountAddress);
			Bytes32 ownerAccountId = account.getOwnerAccountId().get();
			System.out.println(
					"checking that " + advertiserAccountId + " is returned from getOnwerAccountId() => "
							+ ownerAccountId.getTypeAsString());
			String toAddress = receipt.getTo();
			System.out.println(
					"checking that " + accountAddress.getTypeAsString()
							+ " (returned from ShoutSense.getAdvertiserAccount(" + advertiserAccountId
							+ ") needs to match the transaction 'to' address => " + toAddress);
			if (accountAddress.getTypeAsString().equals(toAddress)) {
				if (advertiserAccountId.equals(ownerAccountId.getTypeAsString())) {
					return "VERIFIED";
				}
			}
			return null;
		}
		catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}
}

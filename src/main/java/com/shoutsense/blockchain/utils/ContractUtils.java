package com.shoutsense.blockchain.utils;

import java.math.BigInteger;

import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;

import com.shoutsense.blockchain.contracts.AdvertiserAccount;
import com.shoutsense.blockchain.contracts.ShoutSense;

public class ContractUtils {

	private static Web3j web3j;
	private static String nullPrivateKey = "0";
	private static String nullPublicKey = "0";
	private static Credentials nullCredentials = Credentials.create(nullPrivateKey, nullPublicKey);
	private static BigInteger contractGasPrice = new BigInteger("0");
	private static BigInteger contractGasLimit = new BigInteger("0");

	private ContractUtils() {
	}

	public static ShoutSense loadShoutSense(Address shoutsenseContractAddress, Web3j web3jInstance) {
		web3j = web3jInstance;
		return ShoutSense
				.load(shoutsenseContractAddress.toString(), web3j, nullCredentials, contractGasPrice, contractGasLimit);
	}

	public static AdvertiserAccount loadAdvertiserAccount(Address instanceAddress) {
		return AdvertiserAccount
				.load(instanceAddress.toString(), web3j, nullCredentials, contractGasPrice, contractGasLimit);
	}

}

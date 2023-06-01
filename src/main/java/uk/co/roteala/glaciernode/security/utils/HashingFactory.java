package uk.co.roteala.glaciernode.security.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

public abstract class HashingFactory {
    public static byte[] hexStringToByteArray(String hexString) {
        // Remove any spaces or other formatting characters from the input string
        hexString = hexString.replaceAll("\\s", "");

        // Create a byte array with half the length of the hexadecimal string
        byte[] byteArray = new byte[hexString.length() / 2];

        // Iterate through the hexadecimal string by pairs of characters
        for (int i = 0; i < hexString.length(); i += 2) {
            // Get the two characters representing one byte
            String byteString = hexString.substring(i, i + 2);

            // Convert the byte string to a byte value
            byte b = (byte) Integer.parseInt(byteString, 16);

            // Store the byte value in the byte array
            byteArray[i / 2] = b;
        }

        return byteArray;
    }

    public static byte[] sha256Hash(byte[] input) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] ripemd160Hash(byte[] input) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            MessageDigest ripemd160 = MessageDigest.getInstance("RIPEMD160");
            return ripemd160.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] firstRoundAddressGenerator(byte[] hex) {
        return ripemd160Hash(sha256Hash(hex));
    }

    public static byte[] doubleSHA256(byte[] input) {
        try{
            Security.addProvider(new BouncyCastleProvider());
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] firstHash = sha256.digest(input);
            return sha256.digest(firstHash);
        } catch (NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
    }
}

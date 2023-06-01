package uk.co.roteala.glaciernode.security;

import java.math.BigInteger;

public interface PrivKey extends Key{

    BigInteger getD();

    String getHex();

    byte[] getEncoded();
    String toAddress();

    String toWIF();

    PublicKey getPublicKey();
}

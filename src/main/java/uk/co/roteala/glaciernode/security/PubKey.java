package uk.co.roteala.glaciernode.security;

public interface PubKey extends Key{
    String getX();

    String getY();

    byte[] encodedX();

    byte[] encodedY();
}

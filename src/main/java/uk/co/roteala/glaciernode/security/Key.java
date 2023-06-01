package uk.co.roteala.glaciernode.security;

public interface Key {
    KeyType getType();

    String toAddress();
}

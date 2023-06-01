package uk.co.roteala.glaciernode.security;

import java.util.HashMap;
import java.util.Map;

public enum KeyType {
    PRIVATE("001"),
    PUBLIC("002");

    private String code;

    private static final Map<String, KeyType> VALUES = new HashMap<>();

    KeyType(String code) {
        this.code = code;
    }

    static {
        for(KeyType key : values()) {
            VALUES.put(key.code, key);
        }
    }

    public String getCode() {
        return this.code;
    }

    public static KeyType valueOfCode(String code) {
        return VALUES.get(code);
    }
}

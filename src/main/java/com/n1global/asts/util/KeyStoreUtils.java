package com.n1global.asts.util;

import java.io.InputStream;
import java.security.KeyStore;

public class KeyStoreUtils {
    public static KeyStore loadFromResources(String name, String password) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            
            try (InputStream in = KeyStoreUtils.class.getResourceAsStream(name)) {
                ks.load(in, password.toCharArray());
            }
            
            return ks;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

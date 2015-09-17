package com.n1global.asts;

import java.security.KeyStore;

public class MainLoopConfig {
    private int soTimeout;

    private int idleTimeout;

    private KeyStore keyStore;
    
    private KeyStore trustStore;
    
    private String keyStorePassword;

    private MainLoopConfig(int soTimeout, int idleTimeout, KeyStore keyStore, KeyStore trustStore, String keyStorePassword) {
        this.soTimeout = soTimeout;
        this.idleTimeout = idleTimeout;
        this.keyStore = keyStore;
        this.trustStore = trustStore;
        this.keyStorePassword = keyStorePassword;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public KeyStore getTrustStore() {
        return trustStore;
    }
    
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public static class Builder {
        private int soTimeout = 5000;

        private int idleTimeout = 30000;

        private KeyStore keyStore;
        
        private KeyStore trustStore;
        
        private String keyStorePassword;

        public Builder setSoTimeout(int soTimeout) {
            this.soTimeout = soTimeout;

            return this;
        }

        public Builder setIdleTimeout(int idleTimeout) {
            this.idleTimeout = idleTimeout;

            return this;
        }

        /**
         * A KeyManager determines which authentication credentials to send to the remote host.
         */
        public Builder setKeyStore(KeyStore keyStore) {
            this.keyStore = keyStore;
            
            return this;
        }

        /**
         * A TrustManager determines whether the remote authentication credentials (and thus the connection) should be trusted. 
         */
        public Builder setTrustStore(KeyStore trustStore) {
            this.trustStore = trustStore;
            
            return this;
        }
        
        public Builder setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
            
            return this;
        }

        public MainLoopConfig build() {
            return new MainLoopConfig(soTimeout, idleTimeout, keyStore, trustStore, keyStorePassword);
        }
    }
}

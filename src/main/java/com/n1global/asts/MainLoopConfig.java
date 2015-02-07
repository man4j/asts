package com.n1global.asts;

public class MainLoopConfig {
    private int soTimeout;

    private int idleTimeout;

    private int appRecvBufSize;

    private SelectHandler selectHandler;

    private MainLoopConfig(int soTimeout, int idleTimeout, int appRecvBufSize, SelectHandler selectHandler) {
        this.soTimeout = soTimeout;
        this.idleTimeout = idleTimeout;
        this.appRecvBufSize = appRecvBufSize;
        this.selectHandler = selectHandler;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public int getAppRecvBufSize() {
        return appRecvBufSize;
    }

    public SelectHandler getSelectHandler() {
        return selectHandler;
    }

    public static class Builder {
        private int soTimeout = 5000;

        private int idleTimeout = 30000;

        private int appRecvBufSize = 8192;

        private SelectHandler selectHandler;

        public Builder setSoTimeout(int soTimeout) {
            this.soTimeout = soTimeout;

            return this;
        }

        public Builder setIdleTimeout(int idleTimeout) {
            this.idleTimeout = idleTimeout;

            return this;
        }

        public Builder setAppRecvBufSize(int appRecvBufSize) {
            this.appRecvBufSize = appRecvBufSize;

            return this;
        }

        public Builder setSelectHandler(SelectHandler selectHandler) {
            this.selectHandler = selectHandler;

            return this;
        }

        public MainLoopConfig build() {
            return new MainLoopConfig(soTimeout, idleTimeout, appRecvBufSize, selectHandler);
        }
    }
}

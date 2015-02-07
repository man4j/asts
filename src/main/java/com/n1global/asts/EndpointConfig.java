package com.n1global.asts;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.n1global.asts.message.Message;
import com.n1global.asts.protocol.AbstractFrameProtocol;

public class EndpointConfig<T extends Message> {
    private Class<? extends AbstractEventHandler<T>> handlerClass;

    private Class<? extends AbstractFrameProtocol<T>> protocolClass;

    private int socketSendBufSize;

    private int socketRecvBufSize;

    private int localPort;

    private InetAddress localAddr;

    private EndpointConfig(Class<? extends AbstractEventHandler<T>> handlerClass, Class<? extends AbstractFrameProtocol<T>> protocolClass, int socketSendBufSize, int socketRecvBufSize, int localPort, InetAddress localAddr) {
        this.handlerClass = handlerClass;
        this.protocolClass = protocolClass;
        this.socketSendBufSize = socketSendBufSize;
        this.socketRecvBufSize = socketRecvBufSize;
        this.localPort = localPort;
        this.localAddr = localAddr;
    }

    public Class<? extends AbstractEventHandler<T>> getHandlerClass() {
        return handlerClass;
    }

    public Class<? extends AbstractFrameProtocol<T>> getProtocolClass() {
        return protocolClass;
    }

    public int getSocketSendBufSize() {
        return socketSendBufSize;
    }

    public int getSocketRecvBufSize() {
        return socketRecvBufSize;
    }

    public int getLocalPort() {
        return localPort;
    }

    public InetAddress getLocalAddr() {
        return localAddr;
    }

    public static class Builder<T extends Message> {
        private Class<? extends AbstractEventHandler<T>> handlerClass;

        private Class<? extends AbstractFrameProtocol<T>> protocolClass;

        private int socketSendBufSize = 8192;

        private int socketRecvBufSize = 8192;

        private int localPort;

        private InetAddress localAddr;

        public Builder() {
            try {
                localAddr = InetAddress.getByName("0.0.0.0");
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }

        public Builder<T> setHandlerClass(Class<? extends AbstractEventHandler<T>> handlerClass) {
            this.handlerClass = handlerClass;

            return this;
        }

        public Builder<T> setProtocolClass(Class<? extends AbstractFrameProtocol<T>> protocolClass) {
            this.protocolClass = protocolClass;

            return this;
        }

        public Builder<T> setSocketSendBufSize(int socketSendBufSize) {
            this.socketSendBufSize = socketSendBufSize;

            return this;
        }

        public Builder<T> setSocketRecvBufSize(int socketRecvBufSize) {
            this.socketRecvBufSize = socketRecvBufSize;

            return this;
        }

        public Builder<T> setLocalPort(int localPort) {
            this.localPort = localPort;

            return this;
        }

        public Builder<T> setLocalAddr(InetAddress localAddr) {
            this.localAddr = localAddr;

            return this;
        }

        public EndpointConfig<T> build() {
            return new EndpointConfig<>(handlerClass, protocolClass, socketSendBufSize, socketRecvBufSize, localPort, localAddr);
        }
    }
}

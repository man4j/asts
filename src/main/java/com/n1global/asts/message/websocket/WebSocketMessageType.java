package com.n1global.asts.message.websocket;

public enum WebSocketMessageType {
    TEXT(1), BYTES(2), CLOSE(8), PING(9), PONG(10), HANDSHAKE(777);

    private int typeCode;

    private WebSocketMessageType(int typeCode) {
        this.typeCode = typeCode;
    }

    public int getTypeCode() {
        return typeCode;
    }

    public static WebSocketMessageType byTypeCode(int code) {
        switch (code) {
            case 1 : return TEXT;
            case 2 : return BYTES;
            case 8 : return CLOSE;
            case 9 : return PING;
            case 10: return PONG;
            case 777 : return HANDSHAKE;
        }

        throw new IllegalStateException("Illegal code: " + code);
    }
}

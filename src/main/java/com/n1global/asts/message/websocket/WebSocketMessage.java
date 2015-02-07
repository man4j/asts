package com.n1global.asts.message.websocket;

import java.nio.charset.StandardCharsets;

import com.n1global.asts.message.ByteMessage;

public class WebSocketMessage extends ByteMessage {
    private WebSocketMessageType type;

    private boolean last = true;

    public WebSocketMessage(WebSocketMessageType type) {
        this.type = type;
    }

    public WebSocketMessage(byte[] value, boolean last, WebSocketMessageType type) {
        setValue(value);
        this.type = type;
        this.last = last;
    }

    public WebSocketMessage(byte[] value, boolean last) {
        this(value, last, WebSocketMessageType.BYTES);
    }

    public WebSocketMessage(byte[] value, WebSocketMessageType type) {
        this(value, true, type);
    }

    public WebSocketMessage(byte[] value) {
        this(value, true);
    }

    public WebSocketMessage(String value, boolean last) {
        this(value.getBytes(StandardCharsets.UTF_8), last, WebSocketMessageType.TEXT);
    }

    public WebSocketMessage(String value) {
        this(value, true);
    }

    public WebSocketMessage() {
        //empty
    }

    public String convertBytes2String() {
        return new String(getValue(), StandardCharsets.UTF_8);
    }

    public WebSocketMessageType getType() {
        return type;
    }

    public void setType(WebSocketMessageType type) {
        this.type = type;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}

package com.n1global.asts.message;

import java.nio.charset.StandardCharsets;

public class StringMessage extends ByteMessage {
    private String stringValue;

    public StringMessage(String s) {
        super(s.getBytes(StandardCharsets.UTF_8));

        stringValue = s;
    }

    public StringMessage() {
        //empty
    }

    public String getStringValue() {
        return stringValue;
    }

    @Override
    public void setValue(byte[] value) {
        super.setValue(value);

        stringValue = new String(getValue(), StandardCharsets.UTF_8);
    }
}

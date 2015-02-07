package com.n1global.asts.protocol.websocket;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.n1global.asts.AbstractEventHandler;
import com.n1global.asts.message.websocket.WebSocketMessage;
import com.n1global.asts.message.websocket.WebSocketMessageType;

abstract public class WebSocketEventHandler extends AbstractEventHandler<WebSocketMessage> {
    @Override
    public void onReceive(List<WebSocketMessage> messages) {
        for (WebSocketMessage msg : messages) {
            switch (msg.getType()) {
            case HANDSHAKE:
                Map<String, String> headers = bytes2Headers(msg.getValue());

                String wsKey = headers.get("Sec-WebSocket-Key".toLowerCase());

                if (wsKey == null) wsKey = "";

                String acceptKey;

                try {
                    acceptKey = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)));
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }

                headers.clear();

                headers.put("Upgrade", "websocket");
                headers.put("Connection", "Upgrade");
                headers.put("Sec-WebSocket-Accept", acceptKey);

                getEndpointContext().getSender().send(new WebSocketMessage(headers2Bytes(headers), WebSocketMessageType.HANDSHAKE));

                onOpen();

                break;
            case CLOSE:
                getEndpointContext().getSender().send(new WebSocketMessage(WebSocketMessageType.CLOSE));

                break;
            case PING:
                getEndpointContext().getSender().send(new WebSocketMessage(WebSocketMessageType.PONG));

                break;
            case TEXT:
            case BYTES:
                onDataMessage(msg);

                break;
            default:
                throw new IllegalStateException("Unknown type");
            }
        }
    }

    public void onOpen() {/* empty */}

    public void onDataMessage(@SuppressWarnings("unused") WebSocketMessage msg) {/* empty */}

    private Map<String, String> bytes2Headers(byte[] data) {
        try {
            Map<String, String> headers = new HashMap<>();

            BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)));

            String line;

            while ((line = br.readLine()) != null) {
                int pos = line.indexOf(':');

                if (pos != -1) {
                    headers.put(line.substring(0, pos).trim().toLowerCase(), line.substring(pos + 1).trim());
                }
            }

            return headers;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] headers2Bytes(Map<String, String> headers) {
        StringBuilder data = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\n");

        for (String name : headers.keySet()) {
            data.append(name + ": " + headers.get(name) + "\r\n");
        }

        return data.append("\r\n").toString().getBytes(StandardCharsets.UTF_8);
    }
}

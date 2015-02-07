package com.n1global.asts.test.websockets;

import com.n1global.asts.message.websocket.WebSocketMessage;
import com.n1global.asts.protocol.websocket.WebSocketEventHandler;

public class TestWebSocketHandler extends WebSocketEventHandler {
    @Override
    public void onDataMessage(WebSocketMessage msg) {
        String stringMsg = msg.convertBytes2String();

        System.out.println("Received: " + stringMsg);

        send(new WebSocketMessage("Echo: " + stringMsg));
    }
}

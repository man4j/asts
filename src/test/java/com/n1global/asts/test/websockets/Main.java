package com.n1global.asts.test.websockets;

import com.n1global.asts.EndpointConfig;
import com.n1global.asts.MainLoop;
import com.n1global.asts.message.websocket.WebSocketMessage;
import com.n1global.asts.protocol.websocket.WebSocketProtocol;

public class Main {
    public static void main(String[] args) {
        MainLoop mainLoop = new MainLoop();

        mainLoop.addServer(new EndpointConfig.Builder<WebSocketMessage>().setHandlerClass(TestWebSocketHandler.class)
                                                                         .setProtocolClass(WebSocketProtocol.class)
                                                                         .setLocalPort(9999)
                                                                         .build());

        mainLoop.loop();
    }
}

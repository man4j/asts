package com.n1global.asts.test.lv;

import com.n1global.asts.EndpointConfig;
import com.n1global.asts.MainLoop;
import com.n1global.asts.message.StringMessage;
import com.n1global.asts.protocol.lv.LvStringMessageFrameProtocol;

public class ServerTest {
    public static void main(String[] args) {
        MainLoop mainLoop = new MainLoop();

        mainLoop.addServer(new EndpointConfig.Builder<StringMessage>().setLocalPort(9999)
                                                                      .setHandlerClass(ServerHandler.class)
                                                                      .setProtocolClass(LvStringMessageFrameProtocol.class)
                                                                      .build());

        mainLoop.loop();
    }
}

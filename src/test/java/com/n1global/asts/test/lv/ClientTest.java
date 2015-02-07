package com.n1global.asts.test.lv;

import java.net.InetSocketAddress;

import com.n1global.asts.EndpointConfig;
import com.n1global.asts.MainLoop;
import com.n1global.asts.message.StringMessage;
import com.n1global.asts.protocol.lv.LvStringMessageFrameProtocol;

public class ClientTest {
    public static void main(String[] args) {
        MainLoop mainLoop = new MainLoop();

        mainLoop.addClient(new EndpointConfig.Builder<StringMessage>().setHandlerClass(ClientHandler.class)
                                                                      .setProtocolClass(LvStringMessageFrameProtocol.class)
                                                                      .build(), new InetSocketAddress(9999));

        mainLoop.loop();
    }
}

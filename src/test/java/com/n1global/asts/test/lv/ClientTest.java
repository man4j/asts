package com.n1global.asts.test.lv;

import java.net.InetSocketAddress;
import java.util.Random;

import com.n1global.asts.EndpointConfig;
import com.n1global.asts.MainLoop;
import com.n1global.asts.message.StringMessage;
import com.n1global.asts.protocol.lv.LvStringMessageFrameProtocol;

public class ClientTest {
    public static void main(String[] args) {
        String host = args[0]; 
        int threads = Integer.parseInt(args[1]);
        
        int procs = Runtime.getRuntime().availableProcessors();
        
        System.out.println("Procs count: " + procs);
        
        for (int i = 0; i < threads; i++) {
            new Thread() {
                @Override
                public void run() {
                    int port = 20_000 + new Random().nextInt(procs);
                    
                    MainLoop mainLoop = new MainLoop();
                    
                    mainLoop.addClient(new EndpointConfig.Builder<StringMessage>().setHandlerClass(ClientHandler.class)
                                                                                  .setProtocolClass(LvStringMessageFrameProtocol.class)
                                                                                  .build(), new InetSocketAddress(host, port));
            
                    mainLoop.loop();
                };
            }.start();
        }
    }
}

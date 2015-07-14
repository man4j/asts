package com.n1global.asts.test.lv;

import com.n1global.asts.EndpointConfig;
import com.n1global.asts.MainLoop;
import com.n1global.asts.message.StringMessage;
import com.n1global.asts.protocol.lv.LvStringMessageFrameProtocol;

public class ServerTest {
    public static void main(String[] args) {
        int procs = Runtime.getRuntime().availableProcessors();
        
        System.out.println("Procs count: " + procs);
        
        for (int i = 0; i < procs; i++) {
            int[] arr = new int[1];
            
            arr[0] = i;
            
            new Thread() {
                @Override
                public void run() {
                    MainLoop mainLoop = new MainLoop();
                    
                    mainLoop.addServer(new EndpointConfig.Builder<StringMessage>().setLocalPort(20_000 + arr[0])
                                                                                  .setHandlerClass(ServerHandler.class)
                                                                                  .setProtocolClass(LvStringMessageFrameProtocol.class)
                                                                                  .build());
            
                    mainLoop.loop();
                };
            }.start();
        }
    }
}

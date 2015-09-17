package com.n1global.asts.test.lv;

import java.net.InetSocketAddress;
import java.util.Random;

import com.n1global.asts.EndpointConfig;
import com.n1global.asts.MainLoop;
import com.n1global.asts.MainLoopConfig;
import com.n1global.asts.message.StringMessage;
import com.n1global.asts.protocol.lv.LvStringMessageFrameProtocol;
import com.n1global.asts.util.KeyStoreUtils;

public class ClientTest {
    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        
        int procs = Runtime.getRuntime().availableProcessors();
        
        System.out.println("Procs count: " + procs);
        
        for (int i = 0; i < threads; i++) {
            new Thread() {
                @Override
                public void run() {
                    int port = 20_000 + new Random().nextInt(procs);
                    
                    MainLoop mainLoop = new MainLoop(new MainLoopConfig.Builder().setKeyStore(KeyStoreUtils.loadFromResources("/valid_keystore.jks", "123456"))
                                                                                 .setTrustStore(KeyStoreUtils.loadFromResources("/truststore.jks", "123456"))
                                                                                 .setKeyStorePassword("123456")
                                                                                 .build());
                    
                    mainLoop.addClient(new EndpointConfig.Builder<StringMessage>().setHandlerClass(ClientHandler.class)
                                                                                  .setProtocolClass(LvStringMessageFrameProtocol.class)
                                                                                  .build(), new InetSocketAddress(host, port));
            
                    mainLoop.loop();
                };
            }.start();
        }
    }
}

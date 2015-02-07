package com.n1global.asts.test.exceptions;

import java.io.DataOutputStream;
import java.net.Socket;

public class ExceptionTest {
    public static void main(String[] args) throws Exception {
        Socket s = new Socket("127.0.0.1", 9999);

        DataOutputStream dataOutputStream = new DataOutputStream(s.getOutputStream());

        dataOutputStream.writeChar(2);
        dataOutputStream.writeByte(97);
        dataOutputStream.writeByte(98);

        dataOutputStream.flush();

        s.getInputStream().read();

        s.close();
    }

}

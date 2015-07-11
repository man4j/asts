package com.n1global.asts.util;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class BufUtils {
    public static void copy(ByteBuffer source, ByteBuffer target) {
        int l = (source.remaining() < target.remaining()) ? source.remaining() : target.remaining();

        if (l > 0) {
            target.put(source.array(), source.position(), l);

            source.position(source.position() + l);
        }
    }
    
    public static ByteBuffer concat(ByteBuffer buf1, ByteBuffer buf2) {
        ByteBuffer newBuf = ByteBuffer.allocate(buf1.limit() + buf2.limit());

        return newBuf.put(buf1).put(buf2);
    }
    
    public static void destroyDirect(ByteBuffer buf) {
        try {
            Method cleanerMethod = buf.getClass().getMethod("cleaner");
            
            cleanerMethod.setAccessible(true);
            
            Object cleaner = cleanerMethod.invoke(buf);
            
            Method cleanMethod = cleaner.getClass().getMethod("clean");
        
            cleanMethod.setAccessible(true);
            
            cleanMethod.invoke(cleaner);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

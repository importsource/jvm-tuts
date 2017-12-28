package com.importsource.jvm.tuts;

import java.io.IOException;

/**
 * @author hezhuofan
 *
 * -XX:+DoEscapeAnalysis -XX:+EliminateLocks -XX:+PrintGC
 */
public class EliminateLocksTest {
        public static void alloc(){
            byte[] b=new byte[65];
            synchronized (b) {  //同步代码块
                b[0]=1;
            }
        }
        public static void main(String[] args) throws IOException {
            long b=System.currentTimeMillis();
            for(int i=0;i<100000000;i++){
                alloc();
            }
            long e=System.currentTimeMillis();
            System.out.println(e-b);
        }
}

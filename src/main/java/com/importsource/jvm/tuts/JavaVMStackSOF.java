package com.importsource.jvm.tuts;

/**
 * VM Args：-Xss128k
 *
 * @author hezhuofan
 */
public class JavaVMStackSOF {
    private int statckLength = 1;

    public void stackLeak() {
        statckLength++;
        stackLeak();
    }

    public static void main(String[] args) {
        JavaVMStackSOF javaVMStackSOF = new JavaVMStackSOF();
        try {
            javaVMStackSOF.stackLeak();

        } catch (Throwable e) {
            System.out.println("javaVMStackSOF.statckLength:"+javaVMStackSOF.statckLength);
            throw e;
        }
    }
}

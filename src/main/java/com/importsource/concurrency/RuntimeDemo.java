package com.importsource.concurrency;

/**
 * @author hezhuofan
 */
public class RuntimeDemo {
    public static void main(String[] args) {
        System.out.println("available processors:"+ Runtime.getRuntime().availableProcessors());
        System.out.println("free memory:"+ Runtime.getRuntime().freeMemory());
        System.out.println("max memory:"+ Runtime.getRuntime().maxMemory());
        System.out.println("total memory:"+ Runtime.getRuntime().totalMemory());
    }
}

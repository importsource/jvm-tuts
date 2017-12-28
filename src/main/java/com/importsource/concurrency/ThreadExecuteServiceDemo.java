package com.importsource.concurrency;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author hezhuofan
 */
public class ThreadExecuteServiceDemo {
    public static void main(String[] args) {
        ExecutorService executorService= Executors.newFixedThreadPool(4);
        executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println("hahah");
                return null;
            }
        });
        executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println("wwwwww");
                return null;
            }
        });
        executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println("33333333");
                return null;
            }
        });
        executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println("444444444");
                return null;
            }
        });
    }
}

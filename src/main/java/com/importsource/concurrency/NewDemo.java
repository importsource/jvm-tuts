package com.importsource.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author hezhuofan
 */
public class NewDemo {
    public static void main(String[] args) {
        ExecutorService executorService= Executors.newCachedThreadPool();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("cached thread pool");
            }
        });

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("cached thread pool");
            }
        });

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("cached thread pool");
            }
        });

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("cached thread pool");
            }
        });


    }
}

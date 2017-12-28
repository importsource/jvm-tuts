package com.importsource.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author hezhuofan
 */
public class WorkStealingThreadPoolDemo {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newWorkStealingPool();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("work stealing 1");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("work stealing 2");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("work stealing 3");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("work stealing 4");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("work stealing 5");
            }
        });
    }
}

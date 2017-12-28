package com.importsource.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author hezhuofan
 */
public class WorkStealingWithParlDemo {
    public static void main(String[] args) {
        ExecutorService executorService=Executors.newWorkStealingPool(4);

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("1");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("2");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("3");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("4");
            }
        });
    }
}

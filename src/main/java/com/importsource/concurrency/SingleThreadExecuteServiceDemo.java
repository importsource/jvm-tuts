package com.importsource.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author hezhuofan
 */
public class SingleThreadExecuteServiceDemo {
    public static void main(String[] args) {
        ExecutorService executorService= Executors.newSingleThreadExecutor();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("hahah");
            }
        });

    }
}

package com.importsource.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author hezhuofan
 */
public class CustomedThreadDemo {
    public static void main(String[] args) {
        ExecutorService executorService= Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                r.run();
                System.out.println("init a customed thread");
                return new CustomThread("custom thread");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("custom thread");
            }
        });
    }
}

class CustomThread extends Thread{
    CustomThread(String name){
        super.setName(name);
    }

}

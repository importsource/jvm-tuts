package com.importsource.concurrency;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author hezhuofan
 */
public class ScheduledWithFixedRate {
    public static void main(String[] args) {
        ScheduledExecutorService scheduledExecutorService= Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.println("hahah");
            }
        },1L,1L, TimeUnit.SECONDS);
    }
}

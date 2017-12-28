package com.importsource.concurrency;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author hezhuofan
 */
public class ScheduledWithFixedDelayDemo {
    public static void main(String[] args) {
        ScheduledExecutorService scheduledExecutorService= Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                System.out.println("hahah");
            }
        },3L,1L, TimeUnit.SECONDS);
        System.out.println();
    }
}

package com.importsource.concurrency.custom.queue.delay;

/**
 * @author hezhuofan
 */
public class Demo {
    public static void main(String[] args) {
        final TaskQueueDaemonThread instance = TaskQueueDaemonThread.getInstance();
        instance.init();
        instance.put(3000, new Runnable() {
            @Override
            public void run() {
                System.out.println("三秒后执行这一行");
            }
        });
        instance.put(6000, new Runnable() {
            @Override
            public void run() {
                System.out.println("六秒后执行这一行");
            }
        });
        instance.put(9000, new Runnable() {
            @Override
            public void run() {
                System.out.println("九秒后执行这一行");
            }
        });
        instance.put(20000, new Runnable() {
            @Override
            public void run() {
                System.out.println("20秒后执行这一行");
            }
        });
       instance.start();
    }
}

package com.importsource.conc.tuts.forkjoin;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * @author hezhuofan
 */
public class Computor {
    private int threshold = 100;
    private int parallelism=4;

    public Computor(){}

    public Computor(int parallelism){
        this.parallelism=parallelism;
    }
    public Computor(int parallelism,int threshold){
        this(parallelism);
        this.threshold=threshold;
    }

    public  Result compute(long[] array) {
        // fork/join task:
        ForkJoinPool fjp = new ForkJoinPool(parallelism); // 最大并发数
        ForkJoinTask<Long> task = new SumTask(array, 0, array.length,threshold);
        long startTime = System.currentTimeMillis();
        Long result = fjp.invoke(task);
        long endTime = System.currentTimeMillis();
        return new Result().setDuration(endTime-startTime).setSum(result);
    }

}

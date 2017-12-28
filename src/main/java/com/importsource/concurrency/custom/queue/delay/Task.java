package com.importsource.concurrency.custom.queue.delay;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * [任务调度系统]
 * <br>
 * [队列中要执行的任务]
 * </p>
 */
public class Task<T extends Runnable> implements Delayed {//实现Delayed接口
    /**
     * 到期时间
     */
    private final long time;

    /**
     * 问题对象
     */
    private final T task;
    private static final AtomicLong atomic = new AtomicLong(0);

    private final long n;

    public Task(long timeout, T t) {
        this.time = System.nanoTime() + timeout;
        this.task = t;
        this.n = atomic.getAndIncrement();
    }

    /**
     * 返回与此对象相关的剩余延迟时间，以给定的时间单位表示
     *
     * 这个方法会在CDelayQueue中被调用，通过对该方法的返回值的判断，来决定一个定时任务是否到时
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(this.time - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    /**
     * 实现此方法的目的是，该任务最终会被装载入CDelayQueue里的优先队列（PriorityQueue）中，而优先队列
     * 之所以叫优先队列，就是通过此方法来比对同等条件下哪些任务被优先弹出。
     * @param other
     * @return
     */
    @Override
    public int compareTo(Delayed other) {
        if (other == this) // compare zero ONLY if same object
            return 0;
        if (other instanceof Task) {
            Task x = (Task) other;
            long diff = time - x.time;
            if (diff < 0)
                return -1;
            else if (diff > 0)
                return 1;
            else if (n < x.n)
                return -1;
            else
                return 1;
        }
        long d = (getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS));
        return (d == 0) ? 0 : ((d < 0) ? -1 : 1);
    }

    public T getTask() {
        return this.task;
    }

    @Override
    public int hashCode() {
        return task.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Task) {
            return object.hashCode() == hashCode() ? true : false;
        }
        return false;
    }


}

package com.importsource.concurrency.custom.queue.delay;


import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * 自己搞一个延迟队列
 *
 * 提纲：
 * 1.什么是延时队列。
 *
 * 那么什么是延时队列呢？就是设置在未来某个时间点去执行特定的逻辑。
 *
 * 2.自己实现一个延时队列
 *  你有没有想过一件事情，就是并发包里的延迟队列是怎么实现的呢？ 如果让你实现一个延时队列，你会怎么做？
 *   2.1.定义一个Delayed接口。
 *   我们定义一个Delayed接口。
 *   2.2.定义一个DelayQueue。
 *      2.2.1.继承AbstractQueue
 *      2.2.2.实现BlockingQueue
 *      2.2.2.使用PriorityQueue来装载任务
 *      2.2.3.使用重入锁ReentrantLock来存取操作的线程安全
 *      2.2.4.创建condition，用来唤醒和挂起线程
 *      2.2.5.核心方法take的实现
 *        2.2.5.1.通过getDelay来判断是否到期
 *        2.2.5.2.Leader-Follower模式的使用
 *
 * @author hezhuofan
 */
public class CDelayQueue<E extends Delayed> extends AbstractQueue<E> implements BlockingQueue<E> {

    private final transient ReentrantLock lock=new ReentrantLock();

    private PriorityQueue<E> q=new PriorityQueue<E>();


    /**
     * As you might have read, the pattern consists of 4 components: ThreadPool, HandleSet, Handle, ConcreteEventHandler (implements the EventHandler interface).

     You can think of it as a taxi station at night, where all the drivers are sleeping except for one, the leader. The ThreadPool is a station managing many threads - cabs.

     The leader is waiting for an IO event on the HandleSet, like how a driver waits for a client.

     When a client arrives (in the form of a Handle identifying the IO event), the leader driver wakes up another driver to be the next leader and serves the request from his passenger.

     While he is taking the client to the given address (calling ConcreteEventHandler and handing over Handle to it) the next leader can concurrently serve another passenger.

     When a driver finishes he take his taxi back to the station and falls asleep if the station is not empty. Otherwise he become the leader.

     The pros for this pattern are:

     no communication between the threads are necessary, no synchronization, nor shared memory (no locks, mutexes) are needed.
     more ConcreteEventHandlers can be added without affecting any other EventHandler
     minimizes the latency because of the multiple threads
     The cons are:

     complex
     network IO can be a bottleneck

     正如你可能已经读过的，模式由4个组件组成：ThreadPool，HandleSet，Handle，ConcreteEventHandler（实现EventHandler接口）。

     你可以把它想象成一个夜晚的出租车站，所有的司机都在睡觉，除了一个领导。 ThreadPool是一个管理多个线程的工作站 - 出租车。

     领导者正在等待HandleSet上的IO事件，就像司机等待客户端一样。

     当一个客户到达（以识别IO事件的句柄的形式）时，领导司机唤醒另一个司机成为下一个领导并服务于他的乘客的请求。

     当他把客户送到给定的地址（呼叫ConcreteEventHandler并交给Handle）时，下一个领导可以同时为另一个乘客服务。

     当司机结束时，他把计程车带回车站，如果车站不空，就睡着了。否则他会成为领导者。

     这种模式的优点是：

     线程之间不需要通信，不需要同步，也不需要共享内存（无锁，互斥锁）。
     可以添加更多的ConcreteEventHandlers而不会影响任何其他EventHandler
     最大限度地减少由于多个线程的延迟
     缺点是：

     复杂
     网络IO可能是一个瓶颈
     */
    private Thread leader = null;

    private final Condition available = lock.newCondition();

    @Override
    public Iterator<E> iterator() {
        return null;
    }

    @Override
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(E e) throws InterruptedException {
         offer(e);
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }

    @Override
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock; //每个方法都持有一个lock
        lock.lockInterruptibly();//上锁，如果当前线程被中断，那么抛出异常无法获取锁
        try {
            for (;;) {//循环检测
                E first = q.peek(); //去获取优先队列中index为0的对象（在优先队列中是通过数组实现）
                if (first == null) {//如果没有拿到，那么就让该线程等待
                    System.out.println("awaiting");
                    available.await();//该线程一直等待，直到被signal唤醒或interrupted
                }else {//如果第一个元素拿到了
                    /**
                     * 那么就去获取到第一个元素所设置的延迟时间。
                     * 我们队列中的每个元素都是实现了Delayed接口的，所以是可以拿到元素自定义的getDelay的延迟时间的
                     */
                    long delay = first.getDelay(NANOSECONDS);
                    /**
                     * 如果延迟为小于等于0，那么就意味着这个任务到点了，要被执行了，于是就弹出该任务（元素）。
                     */
                    if (delay <= 0)
                        //弹出该元素，然后返回（得到，然后从队列中删除掉），本次take执行完成。
                        return q.poll();
                    /**
                     * 如果delay大于0，说明该任务还没到时间点
                     */
                    first = null; //那么就把这个first给置为null

                    /**
                     * 以下是有关leader的逻辑。
                     *
                     * 基本逻辑是：
                     *
                     * 1、如果leader被设置，那么就让当前线程挂起。
                     * 2、如果还没有leader，那么就把当前线程选为leader。
                     * 3、然后让当前线程等待delay等长时间。
                     */
                    if (leader != null)
                        available.await();
                    else {
                        Thread thisThread = Thread.currentThread();//当前线程
                        leader = thisThread; //把当前线程设置为leader
                        try {
                            available.awaitNanos(delay);//等待指定delay时间
                        } finally {
                           if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            if (leader == null && q.peek() != null){
                System.out.println("激活线程");
                available.signal();}
            lock.unlock();
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return null;
    }

    @Override
    public int remainingCapacity() {
        return 0;
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return 0;
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        return 0;
    }

    @Override
    public boolean offer(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            q.offer(e);
            if (q.peek() == e) {
                leader = null;
                available.signal();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.poll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.peek();
        } finally {
            lock.unlock();
        }
    }
}

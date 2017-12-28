package com.importsource.concurrency.custom.aqs;

import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * @author hezhuofan
 */
public abstract class AQS
            extends AbstractOwnableSynchronizer
            implements java.io.Serializable {

        private static final long serialVersionUID = 7373984972572414691L;

        /**
         * Creates a new {@code AQS} instance
         * with initial synchronization state of zero.
         */
        protected AQS() { }

        static final class Node {
            /** Marker to indicate a node is waiting in shared mode */
            static final AQS.Node SHARED = new AQS.Node();
            /** Marker to indicate a node is waiting in exclusive mode */
            static final AQS.Node EXCLUSIVE = null;

            /** waitStatus value to indicate thread has cancelled */
            static final int CANCELLED =  1;
            /** waitStatus value to indicate successor's thread needs unparking */
            static final int SIGNAL    = -1;
            /** waitStatus value to indicate thread is waiting on condition */
            static final int CONDITION = -2;
            /**
             * waitStatus value to indicate the next acquireShared should
             * unconditionally propagate
             */
            static final int PROPAGATE = -3;

            volatile int waitStatus;

            volatile AQS.Node prev;

            volatile AQS.Node next;

            volatile Thread thread;

            AQS.Node nextWaiter;


            final boolean isShared() {
                return nextWaiter == SHARED;
            }

            /**
             * Returns previous node, or throws NullPointerException if null.
             * Use when predecessor cannot be null.  The null check could
             * be elided, but is present to help the VM.
             *
             * @return the predecessor of this node
             */
            final AQS.Node predecessor() throws NullPointerException {
                AQS.Node p = prev;
                if (p == null)
                    throw new NullPointerException();
                else
                    return p;
            }

            Node() {    // Used to establish initial head or SHARED marker
            }

            Node(Thread thread, AQS.Node mode) {     // Used by addWaiter
                this.nextWaiter = mode;
                this.thread = thread;
            }

            Node(Thread thread, int waitStatus) { // Used by Condition
                this.waitStatus = waitStatus;
                this.thread = thread;
            }
        }

        /**
         * Head of the wait queue, lazily initialized.  Except for
         * initialization, it is modified only via method setHead.  Note:
         * If head exists, its waitStatus is guaranteed not to be
         * CANCELLED.
         */
        private transient volatile AQS.Node head;

        /**
         * Tail of the wait queue, lazily initialized.  Modified only via
         * method enq to add new wait node.
         */
        private transient volatile AQS.Node tail;

        /**
         * The synchronization state.
         */
        private volatile int state;

        /**
         * Returns the current value of synchronization state.
         * This operation has memory semantics of a {@code volatile} read.
         * @return current state value
         */
        protected final int getState() {
            return state;
        }

        /**
         * Sets the value of synchronization state.
         * This operation has memory semantics of a {@code volatile} write.
         * @param newState the new state value
         */
        protected final void setState(int newState) {
            state = newState;
        }

        /**
         * Atomically sets synchronization state to the given updated
         * value if the current state value equals the expected value.
         * This operation has memory semantics of a {@code volatile} read
         * and write.
         *
         * @param expect the expected value
         * @param update the new value
         * @return {@code true} if successful. False return indicates that the actual
         *         value was not equal to the expected value.
         */
        protected final boolean compareAndSetState(int expect, int update) {
            // See below for intrinsics setup to support this
            return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
        }

        // Queuing utilities

        /**
         * The number of nanoseconds for which it is faster to spin
         * rather than to use timed park. A rough estimate suffices
         * to improve responsiveness with very short timeouts.
         */
        static final long spinForTimeoutThreshold = 1000L;

        /**
         * Inserts node into queue, initializing if necessary. See picture above.
         * @param node the node to insert
         * @return node's predecessor
         */
        private AQS.Node enq(final AQS.Node node) {
            for (;;) {
                AQS.Node t = tail;
                if (t == null) { // Must initialize
                    if (compareAndSetHead(new AQS.Node()))
                        tail = head;
                } else {
                    node.prev = t;
                    if (compareAndSetTail(t, node)) {
                        t.next = node;
                        return t;
                    }
                }
            }
        }

        /**
         * Creates and enqueues node for current thread and given mode.
         *
         * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
         * @return the new node
         */
        private AQS.Node addWaiter(AQS.Node mode) {
            AQS.Node node = new AQS.Node(Thread.currentThread(), mode);
            // Try the fast path of enq; backup to full enq on failure
            AQS.Node pred = tail;
            if (pred != null) {
                node.prev = pred;
                if (compareAndSetTail(pred, node)) {
                    pred.next = node;
                    return node;
                }
            }
            enq(node);
            return node;
        }

        /**
         * Sets head of queue to be node, thus dequeuing. Called only by
         * acquire methods.  Also nulls out unused fields for sake of GC
         * and to suppress unnecessary signals and traversals.
         *
         * @param node the node
         */
        private void setHead(AQS.Node node) {
            head = node;
            node.thread = null;
            node.prev = null;
        }

        /**
         * Wakes up node's successor, if one exists.
         *
         * @param node the node
         */
        private void unparkSuccessor(AQS.Node node) {
            /*
             * If status is negative (i.e., possibly needing signal) try
             * to clear in anticipation of signalling.  It is OK if this
             * fails or if status is changed by waiting thread.
             */
            int ws = node.waitStatus;
            if (ws < 0)
                compareAndSetWaitStatus(node, ws, 0);

            /*
             * Thread to unpark is held in successor, which is normally
             * just the next node.  But if cancelled or apparently null,
             * traverse backwards from tail to find the actual
             * non-cancelled successor.
             */
            AQS.Node s = node.next;
            if (s == null || s.waitStatus > 0) {
                s = null;
                for (AQS.Node t = tail; t != null && t != node; t = t.prev)
                    if (t.waitStatus <= 0)
                        s = t;
            }
            if (s != null)
                LockSupport.unpark(s.thread);
        }

        /**
         * Release action for shared mode -- signals successor and ensures
         * propagation. (Note: For exclusive mode, release just amounts
         * to calling unparkSuccessor of head if it needs signal.)
         */
        private void doReleaseShared() {
            /*
             * Ensure that a release propagates, even if there are other
             * in-progress acquires/releases.  This proceeds in the usual
             * way of trying to unparkSuccessor of head if it needs
             * signal. But if it does not, status is set to PROPAGATE to
             * ensure that upon release, propagation continues.
             * Additionally, we must loop in case a new node is added
             * while we are doing this. Also, unlike other uses of
             * unparkSuccessor, we need to know if CAS to reset status
             * fails, if so rechecking.
             */
            for (;;) {
                AQS.Node h = head;
                if (h != null && h != tail) {
                    int ws = h.waitStatus;
                    if (ws == AQS.Node.SIGNAL) {
                        if (!compareAndSetWaitStatus(h, AQS.Node.SIGNAL, 0))
                            continue;            // loop to recheck cases
                        unparkSuccessor(h);
                    }
                    else if (ws == 0 &&
                            !compareAndSetWaitStatus(h, 0, AQS.Node.PROPAGATE))
                        continue;                // loop on failed CAS
                }
                if (h == head)                   // loop if head changed
                    break;
            }
        }

        /**
         * Sets head of queue, and checks if successor may be waiting
         * in shared mode, if so propagating if either propagate > 0 or
         * PROPAGATE status was set.
         *
         * @param node the node
         * @param propagate the return value from a tryAcquireShared
         */
        private void setHeadAndPropagate(AQS.Node node, int propagate) {
            AQS.Node h = head; // Record old head for check below
            setHead(node);
            /*
             * Try to signal next queued node if:
             *   Propagation was indicated by caller,
             *     or was recorded (as h.waitStatus either before
             *     or after setHead) by a previous operation
             *     (note: this uses sign-check of waitStatus because
             *      PROPAGATE status may transition to SIGNAL.)
             * and
             *   The next node is waiting in shared mode,
             *     or we don't know, because it appears null
             *
             * The conservatism in both of these checks may cause
             * unnecessary wake-ups, but only when there are multiple
             * racing acquires/releases, so most need signals now or soon
             * anyway.
             */
            if (propagate > 0 || h == null || h.waitStatus < 0 ||
                    (h = head) == null || h.waitStatus < 0) {
                AQS.Node s = node.next;
                if (s == null || s.isShared())
                    doReleaseShared();
            }
        }

        // Utilities for various versions of acquire

        /**
         * Cancels an ongoing attempt to acquire.
         *
         * @param node the node
         */
        private void cancelAcquire(AQS.Node node) {
            // Ignore if node doesn't exist
            if (node == null)
                return;

            node.thread = null;

            // Skip cancelled predecessors
            AQS.Node pred = node.prev;
            while (pred.waitStatus > 0)
                node.prev = pred = pred.prev;

            // predNext is the apparent node to unsplice. CASes below will
            // fail if not, in which case, we lost race vs another cancel
            // or signal, so no further action is necessary.
            AQS.Node predNext = pred.next;

            // Can use unconditional write instead of CAS here.
            // After this atomic step, other Nodes can skip past us.
            // Before, we are free of interference from other threads.
            node.waitStatus = AQS.Node.CANCELLED;

            // If we are the tail, remove ourselves.
            if (node == tail && compareAndSetTail(node, pred)) {
                compareAndSetNext(pred, predNext, null);
            } else {
                // If successor needs signal, try to set pred's next-link
                // so it will get one. Otherwise wake it up to propagate.
                int ws;
                if (pred != head &&
                        ((ws = pred.waitStatus) == AQS.Node.SIGNAL ||
                                (ws <= 0 && compareAndSetWaitStatus(pred, ws, AQS.Node.SIGNAL))) &&
                        pred.thread != null) {
                    AQS.Node next = node.next;
                    if (next != null && next.waitStatus <= 0)
                        compareAndSetNext(pred, predNext, next);
                } else {
                    unparkSuccessor(node);
                }

                node.next = node; // help GC
            }
        }

        /**
         * Checks and updates status for a node that failed to acquire.
         * Returns true if thread should block. This is the main signal
         * control in all acquire loops.  Requires that pred == node.prev.
         *
         * @param pred node's predecessor holding status
         * @param node the node
         * @return {@code true} if thread should block
         */
        private static boolean shouldParkAfterFailedAcquire(AQS.Node pred, AQS.Node node) {
            int ws = pred.waitStatus;
            if (ws == AQS.Node.SIGNAL)
                /*
                 * This node has already set status asking a release
                 * to signal it, so it can safely park.
                 */
                return true;
            if (ws > 0) {
                /*
                 * Predecessor was cancelled. Skip over predecessors and
                 * indicate retry.
                 */
                do {
                    node.prev = pred = pred.prev;
                } while (pred.waitStatus > 0);
                pred.next = node;
            } else {
                /*
                 * waitStatus must be 0 or PROPAGATE.  Indicate that we
                 * need a signal, but don't park yet.  Caller will need to
                 * retry to make sure it cannot acquire before parking.
                 */
                compareAndSetWaitStatus(pred, ws, AQS.Node.SIGNAL);
            }
            return false;
        }

        /**
         * Convenience method to interrupt current thread.
         */
        static void selfInterrupt() {
            Thread.currentThread().interrupt();
        }

        /**
         * Convenience method to park and then check if interrupted
         *
         * @return {@code true} if interrupted
         */
        private final boolean parkAndCheckInterrupt() {
            LockSupport.park(this);
            return Thread.interrupted();
        }

        /*
         * Various flavors of acquire, varying in exclusive/shared and
         * control modes.  Each is mostly the same, but annoyingly
         * different.  Only a little bit of factoring is possible due to
         * interactions of exception mechanics (including ensuring that we
         * cancel if tryAcquire throws exception) and other control, at
         * least not without hurting performance too much.
         */

        /**
         * Acquires in exclusive uninterruptible mode for thread already in
         * queue. Used by condition wait methods as well as acquire.
         *
         * @param node the node
         * @param arg the acquire argument
         * @return {@code true} if interrupted while waiting
         */
        final boolean acquireQueued(final AQS.Node node, int arg) {
            boolean failed = true;
            try {
                boolean interrupted = false;
                for (;;) {
                    final AQS.Node p = node.predecessor();
                    if (p == head && tryAcquire(arg)) {
                        setHead(node);
                        p.next = null; // help GC
                        failed = false;
                        return interrupted;
                    }
                    if (shouldParkAfterFailedAcquire(p, node) &&
                            parkAndCheckInterrupt())
                        interrupted = true;
                }
            } finally {
                if (failed)
                    cancelAcquire(node);
            }
        }

        /**
         * Acquires in exclusive interruptible mode.
         * @param arg the acquire argument
         */
        private void doAcquireInterruptibly(int arg)
                throws InterruptedException {
            final AQS.Node node = addWaiter(AQS.Node.EXCLUSIVE);
            boolean failed = true;
            try {
                for (;;) {
                    final AQS.Node p = node.predecessor();
                    if (p == head && tryAcquire(arg)) {
                        setHead(node);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                    if (shouldParkAfterFailedAcquire(p, node) &&
                            parkAndCheckInterrupt())
                        throw new InterruptedException();
                }
            } finally {
                if (failed)
                    cancelAcquire(node);
            }
        }

        /**
         * Acquires in exclusive timed mode.
         *
         * @param arg the acquire argument
         * @param nanosTimeout max wait time
         * @return {@code true} if acquired
         */
        private boolean doAcquireNanos(int arg, long nanosTimeout)
                throws InterruptedException {
            if (nanosTimeout <= 0L)
                return false;
            final long deadline = System.nanoTime() + nanosTimeout;
            final AQS.Node node = addWaiter(AQS.Node.EXCLUSIVE);
            boolean failed = true;
            try {
                for (;;) {
                    final AQS.Node p = node.predecessor();
                    if (p == head && tryAcquire(arg)) {
                        setHead(node);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                    nanosTimeout = deadline - System.nanoTime();
                    if (nanosTimeout <= 0L)
                        return false;
                    if (shouldParkAfterFailedAcquire(p, node) &&
                            nanosTimeout > spinForTimeoutThreshold)
                        LockSupport.parkNanos(this, nanosTimeout);
                    if (Thread.interrupted())
                        throw new InterruptedException();
                }
            } finally {
                if (failed)
                    cancelAcquire(node);
            }
        }

        /**
         * Acquires in shared uninterruptible mode.
         * @param arg the acquire argument
         */
        private void doAcquireShared(int arg) {
            final AQS.Node node = addWaiter(AQS.Node.SHARED);
            boolean failed = true;
            try {
                boolean interrupted = false;
                for (;;) {
                    final AQS.Node p = node.predecessor();
                    if (p == head) {
                        int r = tryAcquireShared(arg);
                        if (r >= 0) {
                            setHeadAndPropagate(node, r);
                            p.next = null; // help GC
                            if (interrupted)
                                selfInterrupt();
                            failed = false;
                            return;
                        }
                    }
                    if (shouldParkAfterFailedAcquire(p, node) &&
                            parkAndCheckInterrupt())
                        interrupted = true;
                }
            } finally {
                if (failed)
                    cancelAcquire(node);
            }
        }

        /**
         * Acquires in shared interruptible mode.
         * @param arg the acquire argument
         */
        private void doAcquireSharedInterruptibly(int arg)
                throws InterruptedException {
            final AQS.Node node = addWaiter(AQS.Node.SHARED);
            boolean failed = true;
            try {
                for (;;) {
                    final AQS.Node p = node.predecessor();
                    if (p == head) {
                        int r = tryAcquireShared(arg);
                        if (r >= 0) {
                            setHeadAndPropagate(node, r);
                            p.next = null; // help GC
                            failed = false;
                            return;
                        }
                    }
                    if (shouldParkAfterFailedAcquire(p, node) &&
                            parkAndCheckInterrupt())
                        throw new InterruptedException();
                }
            } finally {
                if (failed)
                    cancelAcquire(node);
            }
        }

        /**
         * Acquires in shared timed mode.
         *
         * @param arg the acquire argument
         * @param nanosTimeout max wait time
         * @return {@code true} if acquired
         */
        private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
                throws InterruptedException {
            if (nanosTimeout <= 0L)
                return false;
            final long deadline = System.nanoTime() + nanosTimeout;
            final AQS.Node node = addWaiter(AQS.Node.SHARED);
            boolean failed = true;
            try {
                for (;;) {
                    final AQS.Node p = node.predecessor();
                    if (p == head) {
                        int r = tryAcquireShared(arg);
                        if (r >= 0) {
                            setHeadAndPropagate(node, r);
                            p.next = null; // help GC
                            failed = false;
                            return true;
                        }
                    }
                    nanosTimeout = deadline - System.nanoTime();
                    if (nanosTimeout <= 0L)
                        return false;
                    if (shouldParkAfterFailedAcquire(p, node) &&
                            nanosTimeout > spinForTimeoutThreshold)
                        LockSupport.parkNanos(this, nanosTimeout);
                    if (Thread.interrupted())
                        throw new InterruptedException();
                }
            } finally {
                if (failed)
                    cancelAcquire(node);
            }
        }

        // Main exported methods

        /**
         * Attempts to acquire in exclusive mode. This method should query
         * if the state of the object permits it to be acquired in the
         * exclusive mode, and if so to acquire it.
         *
         * <p>This method is always invoked by the thread performing
         * acquire.  If this method reports failure, the acquire method
         * may queue the thread, if it is not already queued, until it is
         * signalled by a release from some other thread. This can be used
         * to implement method {@link Lock#tryLock()}.
         *
         * <p>The default
         * implementation throws {@link UnsupportedOperationException}.
         *
         * @param arg the acquire argument. This value is always the one
         *        passed to an acquire method, or is the value saved on entry
         *        to a condition wait.  The value is otherwise uninterpreted
         *        and can represent anything you like.
         * @return {@code true} if successful. Upon success, this object has
         *         been acquired.
         * @throws IllegalMonitorStateException if acquiring would place this
         *         synchronizer in an illegal state. This exception must be
         *         thrown in a consistent fashion for synchronization to work
         *         correctly.
         * @throws UnsupportedOperationException if exclusive mode is not supported
         */
        protected boolean tryAcquire(int arg) {
            throw new UnsupportedOperationException();
        }

        /**
         * Attempts to set the state to reflect a release in exclusive
         * mode.
         *
         * <p>This method is always invoked by the thread performing release.
         *
         * <p>The default implementation throws
         * {@link UnsupportedOperationException}.
         *
         * @param arg the release argument. This value is always the one
         *        passed to a release method, or the current state value upon
         *        entry to a condition wait.  The value is otherwise
         *        uninterpreted and can represent anything you like.
         * @return {@code true} if this object is now in a fully released
         *         state, so that any waiting threads may attempt to acquire;
         *         and {@code false} otherwise.
         * @throws IllegalMonitorStateException if releasing would place this
         *         synchronizer in an illegal state. This exception must be
         *         thrown in a consistent fashion for synchronization to work
         *         correctly.
         * @throws UnsupportedOperationException if exclusive mode is not supported
         */
        protected boolean tryRelease(int arg) {
            throw new UnsupportedOperationException();
        }

        /**
         * Attempts to acquire in shared mode. This method should query if
         * the state of the object permits it to be acquired in the shared
         * mode, and if so to acquire it.
         *
         * <p>This method is always invoked by the thread performing
         * acquire.  If this method reports failure, the acquire method
         * may queue the thread, if it is not already queued, until it is
         * signalled by a release from some other thread.
         *
         * <p>The default implementation throws {@link
         * UnsupportedOperationException}.
         *
         * @param arg the acquire argument. This value is always the one
         *        passed to an acquire method, or is the value saved on entry
         *        to a condition wait.  The value is otherwise uninterpreted
         *        and can represent anything you like.
         * @return a negative value on failure; zero if acquisition in shared
         *         mode succeeded but no subsequent shared-mode acquire can
         *         succeed; and a positive value if acquisition in shared
         *         mode succeeded and subsequent shared-mode acquires might
         *         also succeed, in which case a subsequent waiting thread
         *         must check availability. (Support for three different
         *         return values enables this method to be used in contexts
         *         where acquires only sometimes act exclusively.)  Upon
         *         success, this object has been acquired.
         * @throws IllegalMonitorStateException if acquiring would place this
         *         synchronizer in an illegal state. This exception must be
         *         thrown in a consistent fashion for synchronization to work
         *         correctly.
         * @throws UnsupportedOperationException if shared mode is not supported
         */
        protected int tryAcquireShared(int arg) {
            throw new UnsupportedOperationException();
        }

        /**
         * Attempts to set the state to reflect a release in shared mode.
         *
         * <p>This method is always invoked by the thread performing release.
         *
         * <p>The default implementation throws
         * {@link UnsupportedOperationException}.
         *
         * @param arg the release argument. This value is always the one
         *        passed to a release method, or the current state value upon
         *        entry to a condition wait.  The value is otherwise
         *        uninterpreted and can represent anything you like.
         * @return {@code true} if this release of shared mode may permit a
         *         waiting acquire (shared or exclusive) to succeed; and
         *         {@code false} otherwise
         * @throws IllegalMonitorStateException if releasing would place this
         *         synchronizer in an illegal state. This exception must be
         *         thrown in a consistent fashion for synchronization to work
         *         correctly.
         * @throws UnsupportedOperationException if shared mode is not supported
         */
        protected boolean tryReleaseShared(int arg) {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns {@code true} if synchronization is held exclusively with
         * respect to the current (calling) thread.  This method is invoked
         * upon each call to a non-waiting {@link AQS.ConditionObject} method.
         * (Waiting methods instead invoke {@link #release}.)
         *
         * <p>The default implementation throws {@link
         * UnsupportedOperationException}. This method is invoked
         * internally only within {@link AQS.ConditionObject} methods, so need
         * not be defined if conditions are not used.
         *
         * @return {@code true} if synchronization is held exclusively;
         *         {@code false} otherwise
         * @throws UnsupportedOperationException if conditions are not supported
         */
        protected boolean isHeldExclusively() {
            throw new UnsupportedOperationException();
        }

        /**
         * Acquires in exclusive mode, ignoring interrupts.  Implemented
         * by invoking at least once {@link #tryAcquire},
         * returning on success.  Otherwise the thread is queued, possibly
         * repeatedly blocking and unblocking, invoking {@link
         * #tryAcquire} until success.  This method can be used
         * to implement method {@link Lock#lock}.
         *
         * @param arg the acquire argument.  This value is conveyed to
         *        {@link #tryAcquire} but is otherwise uninterpreted and
         *        can represent anything you like.
         */
        public final void acquire(int arg) {
            if (!tryAcquire(arg) &&
                    acquireQueued(addWaiter(AQS.Node.EXCLUSIVE), arg))
                selfInterrupt();
        }

        /**
         * Acquires in exclusive mode, aborting if interrupted.
         * Implemented by first checking interrupt status, then invoking
         * at least once {@link #tryAcquire}, returning on
         * success.  Otherwise the thread is queued, possibly repeatedly
         * blocking and unblocking, invoking {@link #tryAcquire}
         * until success or the thread is interrupted.  This method can be
         * used to implement method {@link Lock#lockInterruptibly}.
         *
         * @param arg the acquire argument.  This value is conveyed to
         *        {@link #tryAcquire} but is otherwise uninterpreted and
         *        can represent anything you like.
         * @throws InterruptedException if the current thread is interrupted
         */
        public final void acquireInterruptibly(int arg)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            if (!tryAcquire(arg))
                doAcquireInterruptibly(arg);
        }

        /**
         * Attempts to acquire in exclusive mode, aborting if interrupted,
         * and failing if the given timeout elapses.  Implemented by first
         * checking interrupt status, then invoking at least once {@link
         * #tryAcquire}, returning on success.  Otherwise, the thread is
         * queued, possibly repeatedly blocking and unblocking, invoking
         * {@link #tryAcquire} until success or the thread is interrupted
         * or the timeout elapses.  This method can be used to implement
         * method {@link Lock#tryLock(long, TimeUnit)}.
         *
         * @param arg the acquire argument.  This value is conveyed to
         *        {@link #tryAcquire} but is otherwise uninterpreted and
         *        can represent anything you like.
         * @param nanosTimeout the maximum number of nanoseconds to wait
         * @return {@code true} if acquired; {@code false} if timed out
         * @throws InterruptedException if the current thread is interrupted
         */
        public final boolean tryAcquireNanos(int arg, long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            return tryAcquire(arg) ||
                    doAcquireNanos(arg, nanosTimeout);
        }

        /**
         * Releases in exclusive mode.  Implemented by unblocking one or
         * more threads if {@link #tryRelease} returns true.
         * This method can be used to implement method {@link Lock#unlock}.
         *
         * @param arg the release argument.  This value is conveyed to
         *        {@link #tryRelease} but is otherwise uninterpreted and
         *        can represent anything you like.
         * @return the value returned from {@link #tryRelease}
         */
        public final boolean release(int arg) {
            if (tryRelease(arg)) {
                AQS.Node h = head;
                if (h != null && h.waitStatus != 0)
                    unparkSuccessor(h);
                return true;
            }
            return false;
        }

        /**
         * Acquires in shared mode, ignoring interrupts.  Implemented by
         * first invoking at least once {@link #tryAcquireShared},
         * returning on success.  Otherwise the thread is queued, possibly
         * repeatedly blocking and unblocking, invoking {@link
         * #tryAcquireShared} until success.
         *
         * @param arg the acquire argument.  This value is conveyed to
         *        {@link #tryAcquireShared} but is otherwise uninterpreted
         *        and can represent anything you like.
         */
        public final void acquireShared(int arg) {
            if (tryAcquireShared(arg) < 0)
                doAcquireShared(arg);
        }

        /**
         * Acquires in shared mode, aborting if interrupted.  Implemented
         * by first checking interrupt status, then invoking at least once
         * {@link #tryAcquireShared}, returning on success.  Otherwise the
         * thread is queued, possibly repeatedly blocking and unblocking,
         * invoking {@link #tryAcquireShared} until success or the thread
         * is interrupted.
         * @param arg the acquire argument.
         * This value is conveyed to {@link #tryAcquireShared} but is
         * otherwise uninterpreted and can represent anything
         * you like.
         * @throws InterruptedException if the current thread is interrupted
         */
        public final void acquireSharedInterruptibly(int arg)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            if (tryAcquireShared(arg) < 0)
                doAcquireSharedInterruptibly(arg);
        }

        /**
         * Attempts to acquire in shared mode, aborting if interrupted, and
         * failing if the given timeout elapses.  Implemented by first
         * checking interrupt status, then invoking at least once {@link
         * #tryAcquireShared}, returning on success.  Otherwise, the
         * thread is queued, possibly repeatedly blocking and unblocking,
         * invoking {@link #tryAcquireShared} until success or the thread
         * is interrupted or the timeout elapses.
         *
         * @param arg the acquire argument.  This value is conveyed to
         *        {@link #tryAcquireShared} but is otherwise uninterpreted
         *        and can represent anything you like.
         * @param nanosTimeout the maximum number of nanoseconds to wait
         * @return {@code true} if acquired; {@code false} if timed out
         * @throws InterruptedException if the current thread is interrupted
         */
        public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            return tryAcquireShared(arg) >= 0 ||
                    doAcquireSharedNanos(arg, nanosTimeout);
        }

        /**
         * Releases in shared mode.  Implemented by unblocking one or more
         * threads if {@link #tryReleaseShared} returns true.
         *
         * @param arg the release argument.  This value is conveyed to
         *        {@link #tryReleaseShared} but is otherwise uninterpreted
         *        and can represent anything you like.
         * @return the value returned from {@link #tryReleaseShared}
         */
        public final boolean releaseShared(int arg) {
            if (tryReleaseShared(arg)) {
                doReleaseShared();
                return true;
            }
            return false;
        }

        // Queue inspection methods

        /**
         * Queries whether any threads are waiting to acquire. Note that
         * because cancellations due to interrupts and timeouts may occur
         * at any time, a {@code true} return does not guarantee that any
         * other thread will ever acquire.
         *
         * <p>In this implementation, this operation returns in
         * constant time.
         *
         * @return {@code true} if there may be other threads waiting to acquire
         */
        public final boolean hasQueuedThreads() {
            return head != tail;
        }

        /**
         * Queries whether any threads have ever contended to acquire this
         * synchronizer; that is if an acquire method has ever blocked.
         *
         * <p>In this implementation, this operation returns in
         * constant time.
         *
         * @return {@code true} if there has ever been contention
         */
        public final boolean hasContended() {
            return head != null;
        }

        /**
         * Returns the first (longest-waiting) thread in the queue, or
         * {@code null} if no threads are currently queued.
         *
         * <p>In this implementation, this operation normally returns in
         * constant time, but may iterate upon contention if other threads are
         * concurrently modifying the queue.
         *
         * @return the first (longest-waiting) thread in the queue, or
         *         {@code null} if no threads are currently queued
         */
        public final Thread getFirstQueuedThread() {
            // handle only fast path, else relay
            return (head == tail) ? null : fullGetFirstQueuedThread();
        }

        /**
         * Version of getFirstQueuedThread called when fastpath fails
         */
        private Thread fullGetFirstQueuedThread() {
            /*
             * The first node is normally head.next. Try to get its
             * thread field, ensuring consistent reads: If thread
             * field is nulled out or s.prev is no longer head, then
             * some other thread(s) concurrently performed setHead in
             * between some of our reads. We try this twice before
             * resorting to traversal.
             */
            AQS.Node h, s;
            Thread st;
            if (((h = head) != null && (s = h.next) != null &&
                    s.prev == head && (st = s.thread) != null) ||
                    ((h = head) != null && (s = h.next) != null &&
                            s.prev == head && (st = s.thread) != null))
                return st;

            /*
             * Head's next field might not have been set yet, or may have
             * been unset after setHead. So we must check to see if tail
             * is actually first node. If not, we continue on, safely
             * traversing from tail back to head to find first,
             * guaranteeing termination.
             */

            AQS.Node t = tail;
            Thread firstThread = null;
            while (t != null && t != head) {
                Thread tt = t.thread;
                if (tt != null)
                    firstThread = tt;
                t = t.prev;
            }
            return firstThread;
        }

        /**
         * Returns true if the given thread is currently queued.
         *
         * <p>This implementation traverses the queue to determine
         * presence of the given thread.
         *
         * @param thread the thread
         * @return {@code true} if the given thread is on the queue
         * @throws NullPointerException if the thread is null
         */
        public final boolean isQueued(Thread thread) {
            if (thread == null)
                throw new NullPointerException();
            for (AQS.Node p = tail; p != null; p = p.prev)
                if (p.thread == thread)
                    return true;
            return false;
        }

        /**
         * Returns {@code true} if the apparent first queued thread, if one
         * exists, is waiting in exclusive mode.  If this method returns
         * {@code true}, and the current thread is attempting to acquire in
         * shared mode (that is, this method is invoked from {@link
         * #tryAcquireShared}) then it is guaranteed that the current thread
         * is not the first queued thread.  Used only as a heuristic in
         * ReentrantReadWriteLock.
         */
        final boolean apparentlyFirstQueuedIsExclusive() {
            AQS.Node h, s;
            return (h = head) != null &&
                    (s = h.next)  != null &&
                    !s.isShared()         &&
                    s.thread != null;
        }

        /**
         * Queries whether any threads have been waiting to acquire longer
         * than the current thread.
         *
         * <p>An invocation of this method is equivalent to (but may be
         * more efficient than):
         *  <pre> {@code
         * getFirstQueuedThread() != Thread.currentThread() &&
         * hasQueuedThreads()}</pre>
         *
         * <p>Note that because cancellations due to interrupts and
         * timeouts may occur at any time, a {@code true} return does not
         * guarantee that some other thread will acquire before the current
         * thread.  Likewise, it is possible for another thread to win a
         * race to enqueue after this method has returned {@code false},
         * due to the queue being empty.
         *
         * <p>This method is designed to be used by a fair synchronizer to
         * avoid <a href="AQS#barging">barging</a>.
         * Such a synchronizer's {@link #tryAcquire} method should return
         * {@code false}, and its {@link #tryAcquireShared} method should
         * return a negative value, if this method returns {@code true}
         * (unless this is a reentrant acquire).  For example, the {@code
         * tryAcquire} method for a fair, reentrant, exclusive mode
         * synchronizer might look like this:
         *
         *  <pre> {@code
         * protected boolean tryAcquire(int arg) {
         *   if (isHeldExclusively()) {
         *     // A reentrant acquire; increment hold count
         *     return true;
         *   } else if (hasQueuedPredecessors()) {
         *     return false;
         *   } else {
         *     // try to acquire normally
         *   }
         * }}</pre>
         *
         * @return {@code true} if there is a queued thread preceding the
         *         current thread, and {@code false} if the current thread
         *         is at the head of the queue or the queue is empty
         * @since 1.7
         */
        public final boolean hasQueuedPredecessors() {
            // The correctness of this depends on head being initialized
            // before tail and on head.next being accurate if the current
            // thread is first in queue.
            AQS.Node t = tail; // Read fields in reverse initialization order
            AQS.Node h = head;
            AQS.Node s;
            return h != t &&
                    ((s = h.next) == null || s.thread != Thread.currentThread());
        }


        // Instrumentation and monitoring methods

        /**
         * Returns an estimate of the number of threads waiting to
         * acquire.  The value is only an estimate because the number of
         * threads may change dynamically while this method traverses
         * internal data structures.  This method is designed for use in
         * monitoring system state, not for synchronization
         * control.
         *
         * @return the estimated number of threads waiting to acquire
         */
        public final int getQueueLength() {
            int n = 0;
            for (AQS.Node p = tail; p != null; p = p.prev) {
                if (p.thread != null)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing threads that may be waiting to
         * acquire.  Because the actual set of threads may change
         * dynamically while constructing this result, the returned
         * collection is only a best-effort estimate.  The elements of the
         * returned collection are in no particular order.  This method is
         * designed to facilitate construction of subclasses that provide
         * more extensive monitoring facilities.
         *
         * @return the collection of threads
         */
        public final Collection<Thread> getQueuedThreads() {
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (AQS.Node p = tail; p != null; p = p.prev) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
            return list;
        }

        /**
         * Returns a collection containing threads that may be waiting to
         * acquire in exclusive mode. This has the same properties
         * as {@link #getQueuedThreads} except that it only returns
         * those threads waiting due to an exclusive acquire.
         *
         * @return the collection of threads
         */
        public final Collection<Thread> getExclusiveQueuedThreads() {
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (AQS.Node p = tail; p != null; p = p.prev) {
                if (!p.isShared()) {
                    Thread t = p.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }

        /**
         * Returns a collection containing threads that may be waiting to
         * acquire in shared mode. This has the same properties
         * as {@link #getQueuedThreads} except that it only returns
         * those threads waiting due to a shared acquire.
         *
         * @return the collection of threads
         */
        public final Collection<Thread> getSharedQueuedThreads() {
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (AQS.Node p = tail; p != null; p = p.prev) {
                if (p.isShared()) {
                    Thread t = p.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }

        /**
         * Returns a string identifying this synchronizer, as well as its state.
         * The state, in brackets, includes the String {@code "State ="}
         * followed by the current value of {@link #getState}, and either
         * {@code "nonempty"} or {@code "empty"} depending on whether the
         * queue is empty.
         *
         * @return a string identifying this synchronizer, as well as its state
         */
        public String toString() {
            int s = getState();
            String q  = hasQueuedThreads() ? "non" : "";
            return super.toString() +
                    "[State = " + s + ", " + q + "empty queue]";
        }


        // Internal support methods for Conditions

        /**
         * Returns true if a node, always one that was initially placed on
         * a condition queue, is now waiting to reacquire on sync queue.
         * @param node the node
         * @return true if is reacquiring
         */
        final boolean isOnSyncQueue(AQS.Node node) {
            if (node.waitStatus == AQS.Node.CONDITION || node.prev == null)
                return false;
            if (node.next != null) // If has successor, it must be on queue
                return true;
            /*
             * node.prev can be non-null, but not yet on queue because
             * the CAS to place it on queue can fail. So we have to
             * traverse from tail to make sure it actually made it.  It
             * will always be near the tail in calls to this method, and
             * unless the CAS failed (which is unlikely), it will be
             * there, so we hardly ever traverse much.
             */
            return findNodeFromTail(node);
        }

        /**
         * Returns true if node is on sync queue by searching backwards from tail.
         * Called only when needed by isOnSyncQueue.
         * @return true if present
         */
        private boolean findNodeFromTail(AQS.Node node) {
            AQS.Node t = tail;
            for (;;) {
                if (t == node)
                    return true;
                if (t == null)
                    return false;
                t = t.prev;
            }
        }

        /**
         * Transfers a node from a condition queue onto sync queue.
         * Returns true if successful.
         * @param node the node
         * @return true if successfully transferred (else the node was
         * cancelled before signal)
         */
        final boolean transferForSignal(AQS.Node node) {
            /*
             * If cannot change waitStatus, the node has been cancelled.
             */
            if (!compareAndSetWaitStatus(node, AQS.Node.CONDITION, 0))
                return false;

            /*
             * Splice onto queue and try to set waitStatus of predecessor to
             * indicate that thread is (probably) waiting. If cancelled or
             * attempt to set waitStatus fails, wake up to resync (in which
             * case the waitStatus can be transiently and harmlessly wrong).
             */
            AQS.Node p = enq(node);
            int ws = p.waitStatus;
            if (ws > 0 || !compareAndSetWaitStatus(p, ws, AQS.Node.SIGNAL))
                LockSupport.unpark(node.thread);
            return true;
        }

        /**
         * Transfers node, if necessary, to sync queue after a cancelled wait.
         * Returns true if thread was cancelled before being signalled.
         *
         * @param node the node
         * @return true if cancelled before the node was signalled
         */
        final boolean transferAfterCancelledWait(AQS.Node node) {
            if (compareAndSetWaitStatus(node, AQS.Node.CONDITION, 0)) {
                enq(node);
                return true;
            }
            /*
             * If we lost out to a signal(), then we can't proceed
             * until it finishes its enq().  Cancelling during an
             * incomplete transfer is both rare and transient, so just
             * spin.
             */
            while (!isOnSyncQueue(node))
                Thread.yield();
            return false;
        }

        /**
         * Invokes release with current state value; returns saved state.
         * Cancels node and throws exception on failure.
         * @param node the condition node for this wait
         * @return previous sync state
         */
        final int fullyRelease(AQS.Node node) {
            boolean failed = true;
            try {
                int savedState = getState();
                if (release(savedState)) {
                    failed = false;
                    return savedState;
                } else {
                    throw new IllegalMonitorStateException();
                }
            } finally {
                if (failed)
                    node.waitStatus = AQS.Node.CANCELLED;
            }
        }

        // Instrumentation methods for conditions

        /**
         * Queries whether the given ConditionObject
         * uses this synchronizer as its lock.
         *
         * @param condition the condition
         * @return {@code true} if owned
         * @throws NullPointerException if the condition is null
         */
        public final boolean owns(AQS.ConditionObject condition) {
            return condition.isOwnedBy(this);
        }

        /**
         * Queries whether any threads are waiting on the given condition
         * associated with this synchronizer. Note that because timeouts
         * and interrupts may occur at any time, a {@code true} return
         * does not guarantee that a future {@code signal} will awaken
         * any threads.  This method is designed primarily for use in
         * monitoring of the system state.
         *
         * @param condition the condition
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if exclusive synchronization
         *         is not held
         * @throws IllegalArgumentException if the given condition is
         *         not associated with this synchronizer
         * @throws NullPointerException if the condition is null
         */
        public final boolean hasWaiters(AQS.ConditionObject condition) {
            if (!owns(condition))
                throw new IllegalArgumentException("Not owner");
            return condition.hasWaiters();
        }

        /**
         * Returns an estimate of the number of threads waiting on the
         * given condition associated with this synchronizer. Note that
         * because timeouts and interrupts may occur at any time, the
         * estimate serves only as an upper bound on the actual number of
         * waiters.  This method is designed for use in monitoring of the
         * system state, not for synchronization control.
         *
         * @param condition the condition
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if exclusive synchronization
         *         is not held
         * @throws IllegalArgumentException if the given condition is
         *         not associated with this synchronizer
         * @throws NullPointerException if the condition is null
         */
        public final int getWaitQueueLength(AQS.ConditionObject condition) {
            if (!owns(condition))
                throw new IllegalArgumentException("Not owner");
            return condition.getWaitQueueLength();
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on the given condition associated with this
         * synchronizer.  Because the actual set of threads may change
         * dynamically while constructing this result, the returned
         * collection is only a best-effort estimate. The elements of the
         * returned collection are in no particular order.
         *
         * @param condition the condition
         * @return the collection of threads
         * @throws IllegalMonitorStateException if exclusive synchronization
         *         is not held
         * @throws IllegalArgumentException if the given condition is
         *         not associated with this synchronizer
         * @throws NullPointerException if the condition is null
         */
        public final Collection<Thread> getWaitingThreads(AQS.ConditionObject condition) {
            if (!owns(condition))
                throw new IllegalArgumentException("Not owner");
            return condition.getWaitingThreads();
        }

        /**
         * Condition implementation for a {@link
         * AQS} serving as the basis of a {@link
         * Lock} implementation.
         *
         * <p>Method documentation for this class describes mechanics,
         * not behavioral specifications from the point of view of Lock
         * and Condition users. Exported versions of this class will in
         * general need to be accompanied by documentation describing
         * condition semantics that rely on those of the associated
         * {@code AQS}.
         *
         * <p>This class is Serializable, but all fields are transient,
         * so deserialized conditions have no waiters.
         */
        public class ConditionObject implements Condition, java.io.Serializable {
            private static final long serialVersionUID = 1173984872572414699L;
            /** First node of condition queue. */
            private transient AQS.Node firstWaiter;
            /** Last node of condition queue. */
            private transient AQS.Node lastWaiter;

            /**
             * Creates a new {@code ConditionObject} instance.
             */
            public ConditionObject() { }

            // Internal methods

            /**
             * Adds a new waiter to wait queue.
             * @return its new wait node
             */
            private AQS.Node addConditionWaiter() {
                AQS.Node t = lastWaiter;
                // If lastWaiter is cancelled, clean out.
                if (t != null && t.waitStatus != AQS.Node.CONDITION) {
                    unlinkCancelledWaiters();
                    t = lastWaiter;
                }
                AQS.Node node = new AQS.Node(Thread.currentThread(), AQS.Node.CONDITION);
                if (t == null)
                    firstWaiter = node;
                else
                    t.nextWaiter = node;
                lastWaiter = node;
                return node;
            }

            /**
             * Removes and transfers nodes until hit non-cancelled one or
             * null. Split out from signal in part to encourage compilers
             * to inline the case of no waiters.
             * @param first (non-null) the first node on condition queue
             */
            private void doSignal(AQS.Node first) {
                do {
                    if ( (firstWaiter = first.nextWaiter) == null)
                        lastWaiter = null;
                    first.nextWaiter = null;
                } while (!transferForSignal(first) &&
                        (first = firstWaiter) != null);
            }

            /**
             * Removes and transfers all nodes.
             * @param first (non-null) the first node on condition queue
             */
            private void doSignalAll(AQS.Node first) {
                lastWaiter = firstWaiter = null;
                do {
                    AQS.Node next = first.nextWaiter;
                    first.nextWaiter = null;
                    transferForSignal(first);
                    first = next;
                } while (first != null);
            }

            /**
             * Unlinks cancelled waiter nodes from condition queue.
             * Called only while holding lock. This is called when
             * cancellation occurred during condition wait, and upon
             * insertion of a new waiter when lastWaiter is seen to have
             * been cancelled. This method is needed to avoid garbage
             * retention in the absence of signals. So even though it may
             * require a full traversal, it comes into play only when
             * timeouts or cancellations occur in the absence of
             * signals. It traverses all nodes rather than stopping at a
             * particular target to unlink all pointers to garbage nodes
             * without requiring many re-traversals during cancellation
             * storms.
             */
            private void unlinkCancelledWaiters() {
                AQS.Node t = firstWaiter;
                AQS.Node trail = null;
                while (t != null) {
                    AQS.Node next = t.nextWaiter;
                    if (t.waitStatus != AQS.Node.CONDITION) {
                        t.nextWaiter = null;
                        if (trail == null)
                            firstWaiter = next;
                        else
                            trail.nextWaiter = next;
                        if (next == null)
                            lastWaiter = trail;
                    }
                    else
                        trail = t;
                    t = next;
                }
            }

            // public methods

            /**
             * Moves the longest-waiting thread, if one exists, from the
             * wait queue for this condition to the wait queue for the
             * owning lock.
             *
             * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
             *         returns {@code false}
             */
            public final void signal() {
                if (!isHeldExclusively())
                    throw new IllegalMonitorStateException();
                AQS.Node first = firstWaiter;
                if (first != null)
                    doSignal(first);
            }

            /**
             * Moves all threads from the wait queue for this condition to
             * the wait queue for the owning lock.
             *
             * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
             *         returns {@code false}
             */
            public final void signalAll() {
                if (!isHeldExclusively())
                    throw new IllegalMonitorStateException();
                AQS.Node first = firstWaiter;
                if (first != null)
                    doSignalAll(first);
            }

            /**
             * Implements uninterruptible condition wait.
             * <ol>
             * <li> Save lock state returned by {@link #getState}.
             * <li> Invoke {@link #release} with saved state as argument,
             *      throwing IllegalMonitorStateException if it fails.
             * <li> Block until signalled.
             * <li> Reacquire by invoking specialized version of
             *      {@link #acquire} with saved state as argument.
             * </ol>
             */
            public final void awaitUninterruptibly() {
                AQS.Node node = addConditionWaiter();
                int savedState = fullyRelease(node);
                boolean interrupted = false;
                while (!isOnSyncQueue(node)) {
                    LockSupport.park(this);
                    if (Thread.interrupted())
                        interrupted = true;
                }
                if (acquireQueued(node, savedState) || interrupted)
                    selfInterrupt();
            }

            /*
             * For interruptible waits, we need to track whether to throw
             * InterruptedException, if interrupted while blocked on
             * condition, versus reinterrupt current thread, if
             * interrupted while blocked waiting to re-acquire.
             */

            /** Mode meaning to reinterrupt on exit from wait */
            private static final int REINTERRUPT =  1;
            /** Mode meaning to throw InterruptedException on exit from wait */
            private static final int THROW_IE    = -1;

            /**
             * Checks for interrupt, returning THROW_IE if interrupted
             * before signalled, REINTERRUPT if after signalled, or
             * 0 if not interrupted.
             */
            private int checkInterruptWhileWaiting(AQS.Node node) {
                return Thread.interrupted() ?
                        (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                        0;
            }

            /**
             * Throws InterruptedException, reinterrupts current thread, or
             * does nothing, depending on mode.
             */
            private void reportInterruptAfterWait(int interruptMode)
                    throws InterruptedException {
                if (interruptMode == THROW_IE)
                    throw new InterruptedException();
                else if (interruptMode == REINTERRUPT)
                    selfInterrupt();
            }

            /**
             * Implements interruptible condition wait.
             * <ol>
             * <li> If current thread is interrupted, throw InterruptedException.
             * <li> Save lock state returned by {@link #getState}.
             * <li> Invoke {@link #release} with saved state as argument,
             *      throwing IllegalMonitorStateException if it fails.
             * <li> Block until signalled or interrupted.
             * <li> Reacquire by invoking specialized version of
             *      {@link #acquire} with saved state as argument.
             * <li> If interrupted while blocked in step 4, throw InterruptedException.
             * </ol>
             */
            public final void await() throws InterruptedException {
                if (Thread.interrupted())
                    throw new InterruptedException();
                AQS.Node node = addConditionWaiter();
                int savedState = fullyRelease(node);
                int interruptMode = 0;
                while (!isOnSyncQueue(node)) {
                    LockSupport.park(this);
                    if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                        break;
                }
                if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                    interruptMode = REINTERRUPT;
                if (node.nextWaiter != null) // clean up if cancelled
                    unlinkCancelledWaiters();
                if (interruptMode != 0)
                    reportInterruptAfterWait(interruptMode);
            }

            /**
             * Implements timed condition wait.
             * <ol>
             * <li> If current thread is interrupted, throw InterruptedException.
             * <li> Save lock state returned by {@link #getState}.
             * <li> Invoke {@link #release} with saved state as argument,
             *      throwing IllegalMonitorStateException if it fails.
             * <li> Block until signalled, interrupted, or timed out.
             * <li> Reacquire by invoking specialized version of
             *      {@link #acquire} with saved state as argument.
             * <li> If interrupted while blocked in step 4, throw InterruptedException.
             * </ol>
             */
            public final long awaitNanos(long nanosTimeout)
                    throws InterruptedException {
                if (Thread.interrupted())
                    throw new InterruptedException();
                AQS.Node node = addConditionWaiter();
                int savedState = fullyRelease(node);
                final long deadline = System.nanoTime() + nanosTimeout;
                int interruptMode = 0;
                while (!isOnSyncQueue(node)) {
                    if (nanosTimeout <= 0L) {
                        transferAfterCancelledWait(node);
                        break;
                    }
                    if (nanosTimeout >= spinForTimeoutThreshold)
                        LockSupport.parkNanos(this, nanosTimeout);
                    if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                        break;
                    nanosTimeout = deadline - System.nanoTime();
                }
                if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                    interruptMode = REINTERRUPT;
                if (node.nextWaiter != null)
                    unlinkCancelledWaiters();
                if (interruptMode != 0)
                    reportInterruptAfterWait(interruptMode);
                return deadline - System.nanoTime();
            }

            /**
             * Implements absolute timed condition wait.
             * <ol>
             * <li> If current thread is interrupted, throw InterruptedException.
             * <li> Save lock state returned by {@link #getState}.
             * <li> Invoke {@link #release} with saved state as argument,
             *      throwing IllegalMonitorStateException if it fails.
             * <li> Block until signalled, interrupted, or timed out.
             * <li> Reacquire by invoking specialized version of
             *      {@link #acquire} with saved state as argument.
             * <li> If interrupted while blocked in step 4, throw InterruptedException.
             * <li> If timed out while blocked in step 4, return false, else true.
             * </ol>
             */
            public final boolean awaitUntil(Date deadline)
                    throws InterruptedException {
                long abstime = deadline.getTime();
                if (Thread.interrupted())
                    throw new InterruptedException();
                AQS.Node node = addConditionWaiter();
                int savedState = fullyRelease(node);
                boolean timedout = false;
                int interruptMode = 0;
                while (!isOnSyncQueue(node)) {
                    if (System.currentTimeMillis() > abstime) {
                        timedout = transferAfterCancelledWait(node);
                        break;
                    }
                    LockSupport.parkUntil(this, abstime);
                    if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                        break;
                }
                if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                    interruptMode = REINTERRUPT;
                if (node.nextWaiter != null)
                    unlinkCancelledWaiters();
                if (interruptMode != 0)
                    reportInterruptAfterWait(interruptMode);
                return !timedout;
            }

            /**
             * Implements timed condition wait.
             * <ol>
             * <li> If current thread is interrupted, throw InterruptedException.
             * <li> Save lock state returned by {@link #getState}.
             * <li> Invoke {@link #release} with saved state as argument,
             *      throwing IllegalMonitorStateException if it fails.
             * <li> Block until signalled, interrupted, or timed out.
             * <li> Reacquire by invoking specialized version of
             *      {@link #acquire} with saved state as argument.
             * <li> If interrupted while blocked in step 4, throw InterruptedException.
             * <li> If timed out while blocked in step 4, return false, else true.
             * </ol>
             */
            public final boolean await(long time, TimeUnit unit)
                    throws InterruptedException {
                long nanosTimeout = unit.toNanos(time);
                if (Thread.interrupted())
                    throw new InterruptedException();
                AQS.Node node = addConditionWaiter();
                int savedState = fullyRelease(node);
                final long deadline = System.nanoTime() + nanosTimeout;
                boolean timedout = false;
                int interruptMode = 0;
                while (!isOnSyncQueue(node)) {
                    if (nanosTimeout <= 0L) {
                        timedout = transferAfterCancelledWait(node);
                        break;
                    }
                    if (nanosTimeout >= spinForTimeoutThreshold)
                        LockSupport.parkNanos(this, nanosTimeout);
                    if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                        break;
                    nanosTimeout = deadline - System.nanoTime();
                }
                if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                    interruptMode = REINTERRUPT;
                if (node.nextWaiter != null)
                    unlinkCancelledWaiters();
                if (interruptMode != 0)
                    reportInterruptAfterWait(interruptMode);
                return !timedout;
            }

            //  support for instrumentation

            /**
             * Returns true if this condition was created by the given
             * synchronization object.
             *
             * @return {@code true} if owned
             */
            final boolean isOwnedBy(AQS sync) {
                return sync == AQS.this;
            }

            /**
             * Queries whether any threads are waiting on this condition.
             * Implements {@link AQS#hasWaiters(AQS.ConditionObject)}.
             *
             * @return {@code true} if there are any waiting threads
             * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
             *         returns {@code false}
             */
            protected final boolean hasWaiters() {
                if (!isHeldExclusively())
                    throw new IllegalMonitorStateException();
                for (AQS.Node w = firstWaiter; w != null; w = w.nextWaiter) {
                    if (w.waitStatus == AQS.Node.CONDITION)
                        return true;
                }
                return false;
            }

            /**
             * Returns an estimate of the number of threads waiting on
             * this condition.
             * Implements {@link AQS#getWaitQueueLength(AQS.ConditionObject)}.
             *
             * @return the estimated number of waiting threads
             * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
             *         returns {@code false}
             */
            protected final int getWaitQueueLength() {
                if (!isHeldExclusively())
                    throw new IllegalMonitorStateException();
                int n = 0;
                for (AQS.Node w = firstWaiter; w != null; w = w.nextWaiter) {
                    if (w.waitStatus == AQS.Node.CONDITION)
                        ++n;
                }
                return n;
            }

            /**
             * Returns a collection containing those threads that may be
             * waiting on this Condition.
             * Implements {@link AQS#getWaitingThreads(AQS.ConditionObject)}.
             *
             * @return the collection of threads
             * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
             *         returns {@code false}
             */
            protected final Collection<Thread> getWaitingThreads() {
                if (!isHeldExclusively())
                    throw new IllegalMonitorStateException();
                ArrayList<Thread> list = new ArrayList<Thread>();
                for (AQS.Node w = firstWaiter; w != null; w = w.nextWaiter) {
                    if (w.waitStatus == AQS.Node.CONDITION) {
                        Thread t = w.thread;
                        if (t != null)
                            list.add(t);
                    }
                }
                return list;
            }
        }

        /**
         * Setup to support compareAndSet. We need to natively implement
         * this here: For the sake of permitting future enhancements, we
         * cannot explicitly subclass AtomicInteger, which would be
         * efficient and useful otherwise. So, as the lesser of evils, we
         * natively implement using hotspot intrinsics API. And while we
         * are at it, we do the same for other CASable fields (which could
         * otherwise be done with atomic field updaters).
         */
        private static final Unsafe unsafe = Unsafe.getUnsafe();
        private static final long stateOffset;
        private static final long headOffset;
        private static final long tailOffset;
        private static final long waitStatusOffset;
        private static final long nextOffset;

        static {
            try {
                stateOffset = unsafe.objectFieldOffset
                        (AQS.class.getDeclaredField("state"));
                headOffset = unsafe.objectFieldOffset
                        (AQS.class.getDeclaredField("head"));
                tailOffset = unsafe.objectFieldOffset
                        (AQS.class.getDeclaredField("tail"));
                waitStatusOffset = unsafe.objectFieldOffset
                        (AQS.Node.class.getDeclaredField("waitStatus"));
                nextOffset = unsafe.objectFieldOffset
                        (AQS.Node.class.getDeclaredField("next"));

            } catch (Exception ex) { throw new Error(ex); }
        }

        /**
         * CAS head field. Used only by enq.
         */
        private final boolean compareAndSetHead(AQS.Node update) {
            return unsafe.compareAndSwapObject(this, headOffset, null, update);
        }

        /**
         * CAS tail field. Used only by enq.
         */
        private final boolean compareAndSetTail(AQS.Node expect, AQS.Node update) {
            return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
        }

        /**
         * CAS waitStatus field of a node.
         */
        private static final boolean compareAndSetWaitStatus(AQS.Node node,
                                                             int expect,
                                                             int update) {
            return unsafe.compareAndSwapInt(node, waitStatusOffset,
                    expect, update);
        }

        /**
         * CAS next field of a node.
         */
        private static final boolean compareAndSetNext(AQS.Node node,
                                                       AQS.Node expect,
                                                       AQS.Node update) {
            return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
        }
    }



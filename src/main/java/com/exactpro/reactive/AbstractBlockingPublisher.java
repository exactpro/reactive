/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.reactive;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class AbstractBlockingPublisher<T> implements Publisher<T>, Subscription, Runnable {
    private volatile boolean cancelled = false;

    private volatile Subscriber<? super T> downstream = null;
    private static final AtomicReferenceFieldUpdater<AbstractBlockingPublisher, Subscriber> DOWNSTREAM_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(AbstractBlockingPublisher.class, Subscriber.class, "downstream");

    private volatile Throwable error = null;
    private static final AtomicReferenceFieldUpdater<AbstractBlockingPublisher, Throwable> ERROR_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(AbstractBlockingPublisher.class, Throwable.class, "error");

    private volatile Thread ioThread;

    private final DemandSemaphore demand = new DemandSemaphore(0L);

    @Override
    public void subscribe(Subscriber<? super T> downstream) {
        Objects.requireNonNull(downstream, "downstream cannot be null");
        if (DOWNSTREAM_UPDATER.compareAndSet(this, null, downstream)) {
            downstream.onSubscribe(this);
            if (!cancelled) {
                startIoThread();
            }
        } else {
            fail(downstream, new IllegalStateException("Already subscribed"));
        }
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            setError(new IllegalArgumentException("`n` must be greater than zero"));
            interruptIoThread();
            return;
        }
        demand.release(n);
    }

    @Override
    public void cancel() {
        if (!cancelled) {
            cancelled = true;
            downstream = null;
            interruptIoThread();
        }
    }

    @Override
    public void run() {
        Subscriber<? super T> downstream = this.downstream;
        try {
            if (cancelled || (error != null)) {
                return;
            }
            try {
                open();
            } catch (InterruptedException ex) {
                // Allow thread to finish
            } catch (Throwable ex) {
                setError(ex);
            }
            try {
                while (!cancelled && (error == null)) {
                    demand.acquire(); // TODO acquire max
                    T next = read();
                    if (next == null) {
                        break;
                    } else {
                        downstream.onNext(next);
                    }
                }
            } catch (InterruptedException ex) {
                // Allow thread to finish
            } catch (Throwable ex) {
                setError(ex);
            } finally {
                try {
                    close();
                } catch (Throwable ex) {
                    setError(ex);
                }
            }
        } finally {
            if (!cancelled) {
                cancelled = true;
                if (error == null) {
                    downstream.onComplete();
                } else {
                    downstream.onError(error);
                }
            }
            ioThread = null;
        }
    }

    abstract void open() throws Exception;

    abstract T read() throws Exception;

    abstract void close() throws Exception;

    static class EmptySubscription implements Subscription {
        @Override
        public void request(long n) {
            // TODO add parameter validations
        }

        @Override
        public void cancel() {
            // Do nothing.
        }
    }

    static Subscription empty() {
        return new EmptySubscription();
    }

    private void startIoThread() {
        ioThread = new Thread(this);
        // TODO ioThread.setName("");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    private void interruptIoThread() {
        Thread ioThread = this.ioThread;
        if (ioThread != null) {
            ioThread.interrupt();
        }
    }

    private void setError(Throwable ex) {
        if (!ERROR_UPDATER.compareAndSet(this, null, ex)) {
            error.addSuppressed(ex);
        }
    }

    private static <T> void fail(Subscriber<T> subscriber, Throwable error) {
        subscriber.onSubscribe(empty());
        subscriber.onError(error);
    }
}

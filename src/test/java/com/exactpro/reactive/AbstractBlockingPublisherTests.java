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
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

import java.io.IOException;

public class AbstractBlockingPublisherTests extends PublisherVerification<Long> {
    public AbstractBlockingPublisherTests() {
        super(new TestEnvironment());
    }

    @Override
    public Publisher<Long> createPublisher(long elements) {
        return new SequencePublisher(elements);
    }

    @Override
    public Publisher<Long> createFailedPublisher() {
        return new FailedBlockingPublisher();
    }

    @Override
    public long boundedDepthOfOnNextAndRequestRecursion() {
        return 1;
    }

    static class SequencePublisher extends AbstractBlockingPublisher<Long> {
        private final long limit;

        private long current = 0;

        public SequencePublisher(long limit) {
            this.limit = limit;
        }

        @Override
        void open() throws Exception {
            Thread.sleep(1);
        }

        @Override
        Long read() throws Exception {
            Thread.sleep(1);
            if (current >= limit) {
                return null;
            }
            return current++;
        }

        @Override
        void close() throws Exception {
            Thread.sleep(1);
        }
    }

    static class FailedBlockingPublisher extends AbstractBlockingPublisher<Long> {
        @Override
        void open() throws IOException {
            throw new IOException("Unable to open stream");
        }

        @Override
        Long read() {
            throw new UnsupportedOperationException("Shouldn't be called");
        }

        @Override
        void close() {
            throw new UnsupportedOperationException("Shouldn't be called");
        }
    }
}

/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import com.lmax.disruptor.util.ThreadHints;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    private final Object mutex = new Object();


    @Override
    public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        //sequence 是消费者的下一个消费位置，cursorSequence是当前生产者的位置
        //双重检查锁，double check
        if (cursorSequence.get() < sequence)
        {
            synchronized (mutex)
            {
                while (cursorSequence.get() < sequence)
                {
                    barrier.checkAlert();
                    mutex.wait();
                }
            }
        }

        //表示当前这个消费者的位置(sequence 是消费者的下一个消费位置)，不能大于依赖的消费者的消费位置
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
            ThreadHints.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        synchronized (mutex)
        {
            mutex.notifyAll();
        }
    }

    @Override
    public String toString()
    {
        return "BlockingWaitStrategy{" +
            "mutex=" + mutex +
            '}';
    }
}

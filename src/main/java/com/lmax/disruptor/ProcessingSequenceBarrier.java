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


/**
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy.
 */
final class ProcessingSequenceBarrier implements SequenceBarrier
{
    private final WaitStrategy waitStrategy;
    private final Sequence dependentSequence;
    private volatile boolean alerted = false;
    private final Sequence cursorSequence;
    private final Sequencer sequencer;

    ProcessingSequenceBarrier(
        final Sequencer sequencer,
        final WaitStrategy waitStrategy,
        final Sequence cursorSequence,
        final Sequence[] dependentSequences)
    {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        if (0 == dependentSequences.length)
        {
            dependentSequence = cursorSequence;
        }
        else
        {
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException, TimeoutException
    {
        checkAlert();

        /*
        *1. 这里的cursorSequence 是通过Sequencer传进来的，可以看成是生产者的游标，
        *在disruptor里面没有明确的生产者的概念，Sequencer==producer，语义上是相等的，
        *所以在barrier里面的cursor和Sequencer是同一个游标，用来标记当前写入的位置
        * */

        /*
        * 2. 这里的dependentSequence表示这个barrier依赖的序列，在disruptor里面有下面的关系:
        * eventHandler --- eventProcessor --- SequencerBarrier 1:1:1的关系
        * eventProcess可以看成是消费者，里面有一个sequence表示消费的位置，还有一个变量sequencerBarrier
        * 来记录这个consumer依赖的consumer的消费位置，在disruptor里面是通过handler的依赖关系来表示的
        * 比如：handler2 依赖 handler1 ，那么handler1的消费位置sequence1必须大于等于handler2的sequence2
        * 在handler2的sequencerBarrier记录了handler1的sequence1，也就是下面的dependentSequence变量，
        * 这个变量可以表示一个数组，一支持比如一个handler同时依赖多个其他的handler
        * */
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);

        //看了下 ，大部分的waitStrategy都是返回的availableSequence大于等于sequence的
        //少部份的会小于，就直接返回，后面分析这些会小于的waitStrategy todo
        if (availableSequence < sequence)
        {
            return availableSequence;
        }

        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert()
    {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException
    {
        if (alerted)
        {
            throw AlertException.INSTANCE;
        }
    }
}
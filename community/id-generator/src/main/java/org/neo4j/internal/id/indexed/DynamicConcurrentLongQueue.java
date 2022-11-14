/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.id.indexed;

import static java.lang.Math.max;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

class DynamicConcurrentLongQueue implements ConcurrentLongQueue {
    private final AtomicReference<Chunk> first;
    private final AtomicReference<Chunk> last;
    private final AtomicInteger numChunks = new AtomicInteger(1);
    private final int chunkSize;
    private final int maxNumChunks;

    DynamicConcurrentLongQueue(int chunkSize, int maxNumChunks) {
        this.chunkSize = chunkSize;
        this.maxNumChunks = maxNumChunks;

        var chunk = new Chunk(chunkSize);
        this.first = new AtomicReference<>(chunk);
        this.last = new AtomicReference<>(chunk);
    }

    @Override
    public boolean offer(long v) {
        var chunk = last.get();
        if (!chunk.offer(v)) {
            if (numChunks.get() >= maxNumChunks) {
                return false;
            }

            var next = new Chunk(chunkSize);
            chunk.next.set(next);
            last.set(next);
            numChunks.incrementAndGet();
            return next.offer(v);
        }
        return true;
    }

    @Override
    public long takeOrDefault(long defaultValue) {
        Chunk chunk;
        Chunk next;
        do {
            chunk = first.get();
            next = chunk.next.get();
            var candidate = chunk.takeOrDefault(defaultValue);
            if (candidate != defaultValue) {
                return candidate;
            }
            if (next != null) {
                if (first.compareAndSet(chunk, next)) {
                    numChunks.decrementAndGet();
                }
            }
        } while (next != null);
        return defaultValue;
    }

    private int capacity() {
        return chunkSize * maxNumChunks;
    }

    @Override
    public int size() {
        int size = first.get().size();
        var numChunks = this.numChunks.get();
        if (numChunks > 1) {
            size += (numChunks - 2) * chunkSize;
            size += last.get().size();
        }
        return size;
    }

    @Override
    public int availableSpace() {
        int capacity = capacity();
        var lastChunk = last.get();
        int occupied = (numChunks.get() - 1) * chunkSize + lastChunk.occupied();
        return capacity - occupied;
    }

    @Override
    public void clear() {
        var chunk = new Chunk(chunkSize);
        first.set(chunk);
        last.set(chunk);
    }

    private static class Chunk {
        private final AtomicLongArray array;
        private final int capacity;
        private final AtomicInteger readSeq = new AtomicInteger();
        private final AtomicInteger writeSeq = new AtomicInteger();
        private final AtomicReference<Chunk> next = new AtomicReference<>();

        Chunk(int capacity) {
            this.array = new AtomicLongArray(capacity);
            this.capacity = capacity;
        }

        boolean offer(long v) {
            var currentWriteSeq = writeSeq.get();
            if (currentWriteSeq == capacity) {
                return false;
            }
            array.set(currentWriteSeq, v);
            writeSeq.incrementAndGet();
            return true;
        }

        long takeOrDefault(long defaultValue) {
            int currentReadSeq;
            int currentWriteSeq;
            long value;
            do {
                currentReadSeq = readSeq.get();
                currentWriteSeq = writeSeq.get();
                if (currentReadSeq == currentWriteSeq) {
                    return defaultValue;
                }
                value = array.get(currentReadSeq);
            } while (!readSeq.compareAndSet(currentReadSeq, currentReadSeq + 1));
            return value;
        }

        int size() {
            return max(0, writeSeq.intValue() - readSeq.intValue());
        }

        int occupied() {
            return writeSeq.intValue();
        }
    }
}
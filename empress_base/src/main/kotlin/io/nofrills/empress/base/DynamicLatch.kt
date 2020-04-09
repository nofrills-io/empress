/*
 * Copyright 2020 Mateusz Armatys
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

package io.nofrills.empress.base

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicLong

internal class DynamicLatch(initialValue: Long = 0) {
    private val channel = Channel<Long>(Channel.CONFLATED)
    private val atomic = AtomicLong(initialValue)

    init {
        check(initialValue >= 0)
    }

    fun countUp() {
        channel.offer(atomic.incrementAndGet())
    }

    fun countDown() {
        channel.offer(atomic.decrementAndGet().also { check(it >= 0) })
    }

    suspend fun close() {
        for (value in channel) {
            if (value == 0L) {
                channel.cancel()
                break
            }
        }
    }
}

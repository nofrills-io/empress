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

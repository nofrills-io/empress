package io.nofrills.empress.base

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import java.lang.IllegalStateException
import java.util.concurrent.Executors
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
internal class DynamicLatchTest {
    @Test
    fun baseCase() = runBlockingTest {
        val tested = DynamicLatch()
        tested.countUp()
        tested.countDown()
        tested.close()
    }

    @Test
    fun initialValue() = runBlockingTest {
        val tested = DynamicLatch(1)
        tested.countDown()
        tested.close()
    }

    @Test(expected = IllegalStateException::class)
    fun invalidInitialValue() {
        DynamicLatch(-1)
    }

    @Test(expected = IllegalStateException::class)
    fun countDownBelowZero() {
        val tested = DynamicLatch()
        tested.countUp()
        tested.countDown()
        tested.countDown()
    }

    @Test
    fun multipleThreads() {
        val dispatcherA = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        val dispatcherB = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val tested = DynamicLatch()

        runBlocking(dispatcherA) {
            tested.countUp()
            val deferred = async(dispatcherB) { tested.close() }
            for (i in 1..1_000) {
                launch {
                    tested.countUp()
                    delay(Random.nextLong(10))
                    tested.countDown()
                }
            }
            launch {
                delay(10)
                tested.countDown()
            }
            deferred.await()
        }
    }
}

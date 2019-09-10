package io.nofrills.empress.internal

import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class AtomicItemTest {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: AtomicItem<Int>

    @Before
    fun setUp() {
        scope = TestCoroutineScope()
        tested = AtomicItem(2)
    }

    @After
    fun tearDown() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun simpleUsage() = scope.runBlockingTest {
        assertEquals(2, tested.get())

        tested.update { it + 1 }
        assertEquals(3, tested.get())
    }

    @Test
    fun blockingGetOnUpdate() = scope.runBlockingTest {
        launch {
            delay(10)
            tested.update {
                delay(100)
                it + 2
            }
        }

        val deferredValue = async {
            delay(50)
            tested.get()
        }

        val value = deferredValue.await()
        assertEquals(4, value)
    }

    @Test
    fun blockingDoubleUpdate() = scope.runBlockingTest {
        val job1 = launch {
            delay(10)
            tested.update {
                delay(100)
                it * 2
            }
        }

        val job2 = launch {
            delay(50)
            tested.update {
                it + 3
            }
        }

        joinAll(job1, job2)

        assertEquals(7, tested.get())
    }
}

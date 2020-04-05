package io.nofrills.empress.base

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmpressBackendTest {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: EmpressApi<SampleEmpress, Model, Signal>

    @Before
    fun setUp() {
        scope = TestCoroutineScope()
        tested = EmpressBackend(SampleEmpress(), scope)
    }

    @After
    fun tearDown() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun name() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toList() }
        tested.post { increment() }
        tested.post { increment() }
        tested.post { decrement() }
        tested.interrupt()
        val updated = deferredUpdates.await()
        val expected = listOf<Model>(
            Model.Counter(1),
            Model.Counter(2),
            Model.Counter(1)
        )
        assertEquals(expected, updated)
    }
}

sealed class Model {
    data class Counter(val count: Int) : Model()
    data class Sender(val handlerId: HandlerId? = null) : Model()
}

sealed class Signal {
    object CounterSent : Signal()
}

class SampleEmpress : Empress<Model, Signal>() {
    override fun initialModels(): Collection<Model> {
        return listOf(Model.Counter(0), Model.Sender())
    }

    fun decrement() {
        val count = get(Model.Counter::class).count
        queueUpdate(Model.Counter(count - 1))
    }

    suspend fun increment() {
        val count = get(Model.Counter::class).count
        update(Model.Counter(count + 1))
    }
}
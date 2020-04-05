package io.nofrills.empress.base

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
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
    fun modelUpdates() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toList() }
        tested.post { increment() }
        tested.post { increment() }
        tested.post { delta(2) }
        tested.post { decrement() }
        tested.interrupt()
        val updates = deferredUpdates.await()
        val expected = listOf<Model>(
            Model.Counter(1),
            Model.Counter(2),
            Model.Counter(4),
            Model.Counter(3)
        )
        assertEquals(expected, updates)
    }

    @Test
    fun signaling() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toList() }
        val deferredSignals = async { tested.signals().toList() }

        tested.post { increment() }
        tested.post { sendCounter() }
        tested.interrupt()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        val expectedUpdates = listOf(
            Model.Counter(1),
            Model.Sender(HandlerId(2L)),
            Model.Sender(null)
        )
        assertEquals(expectedUpdates, updates)
        assertEquals(listOf(Signal.CounterSent), signals)
    }

    @Test
    fun cancelHandler() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toList() }
        val deferredSignals = async { tested.signals().toList() }

        pauseDispatcher()
        tested.post { increment() }
        tested.post { sendCounter() }
        advanceTimeBy(500L)
        tested.post { cancelSending() }
        resumeDispatcher()
        tested.interrupt()

        val updates = deferredUpdates.await()
        val signals = deferredSignals.await()

        val expectedModels = listOf(
            Model.Counter(1),
            Model.Sender(HandlerId(2L)),
            Model.Sender(null)
        )
        assertEquals(expectedModels, updates)
        assertEquals(listOf(Signal.SendingCancelled), signals)
    }
}

sealed class Model {
    data class Counter(val count: Int) : Model()
    data class Sender(val handlerId: HandlerId? = null) : Model()
}

sealed class Signal {
    object CounterSent : Signal()
    object SendingCancelled : Signal()
}

class SampleEmpress : Empress<Model, Signal>() {
    override fun initialModels(): Collection<Model> {
        return listOf(Model.Counter(0), Model.Sender())
    }

    fun decrement() {
        val count = get<Model.Counter>().count
        queueUpdate(Model.Counter(count - 1))
    }

    suspend fun increment() {
        val count = get<Model.Counter>().count
        update(Model.Counter(count + 1))
    }

    suspend fun delta(d: Int) {
        val count = get<Model.Counter>().count
        update(Model.Counter(count + d))
    }

    suspend fun sendCounter() {
        if (get<Model.Sender>().handlerId != null) return

        update(Model.Sender(handlerId()))
        val count = get<Model.Counter>().count
        delay(count * 1000L)
        signal(Signal.CounterSent)
        update(Model.Sender(null))
    }

    fun cancelSending() {
        val handlerId = get<Model.Sender>().handlerId ?: return
        cancelHandler(handlerId)
        queueUpdate(Model.Sender(null))
        queueSignal(Signal.SendingCancelled)
    }
}
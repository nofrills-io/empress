package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException

class DefaultEmpressBackendTest {
    private val baseModel = Model(listOf(Patch.Counter(0), Patch.Sender(null)))

    private lateinit var job: Job
    private lateinit var requestIdProducer: RequestIdProducer
    private lateinit var requestHolder: RequestHolder
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: DefaultEmpressBackend<Event, Patch, Request>

    @Before
    fun setUp() {
        requestIdProducer = DefaultRequestIdProducer()
        requestHolder = DefaultRequestHolder()
        scope = TestCoroutineScope()
        job = Job()
        tested = makeEmpressBackend(job)
    }

    @After
    fun tearDown() {
        job.cancel()
        scope.cleanupTestCoroutines()
    }

    @Test
    fun simpleUsage() = scope.runBlockingTest {
        assertEquals(baseModel, tested.modelSnapshot())

        val deferredUpdates = async(start = CoroutineStart.UNDISPATCHED) {
            tested.updates().toList()
        }

        tested.send(Event.Increment)
        tested.send(Event.Increment)
        tested.interrupt()

        val updates = deferredUpdates.await()

        assertEquals(2, updates.size)
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(1))), Event.Increment),
            updates[0]
        )
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(2))), Event.Increment),
            updates[1]
        )
        assertTrue(tested.areChannelsClosed())
    }

    @Test
    fun simpleUsageWithRequest() = scope.runBlockingTest {
        val deferredUpdates = async {
            tested.updates().toList()
        }

        tested.send(Event.Send(1))
        tested.interrupt()

        val updates = deferredUpdates.await()

        assertEquals(2, updates.size)
        assertEquals(
            Update<Event, Patch>(Model(baseModel, listOf(Patch.Sender(1))), Event.Send(1)),
            updates[0]
        )
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(null))),
                Event.CounterSent
            ), updates[1]
        )
    }

    @Test
    fun delayedFirstObserver() = scope.runBlockingTest {
        tested.send(Event.Increment)
        tested.send(Event.Increment)

        val deferredUpdates = async(start = CoroutineStart.UNDISPATCHED) {
            tested.updates().toList()
        }

        tested.send(Event.Decrement)
        tested.interrupt()

        val updates = deferredUpdates.await()

        assertEquals(1, updates.size)
        assertEquals(
            Update<Event, Patch>(Model(baseModel, listOf(Patch.Counter(1))), Event.Decrement),
            updates[0]
        )
    }

    @Test
    fun twoObservers() = scope.runBlockingTest {
        val deferredUpdates1 = async(start = CoroutineStart.UNDISPATCHED) {
            tested.updates().toList()
        }
        val deferredUpdates2 = async(start = CoroutineStart.UNDISPATCHED) {
            tested.updates().toList()
        }

        tested.send(Event.Decrement)
        tested.send(Event.Decrement)
        tested.interrupt()

        val updates1 = deferredUpdates1.await()
        val updates2 = deferredUpdates2.await()
        assertEquals(updates1, updates2)

        assertEquals(2, updates1.size)
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(-1))), Event.Decrement),
            updates1[0]
        )
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(-2))), Event.Decrement),
            updates1[1]
        )
    }

    @Test
    fun delayedSecondObserver() = scope.runBlockingTest {
        val deferredUpdates1 = async {
            tested.updates().toList()
        }

        tested.send(Event.Increment)
        tested.send(Event.Increment)

        val deferredUpdates2 = async {
            tested.updates().toList()
        }

        tested.send(Event.Decrement)
        tested.interrupt()

        val updates1 = deferredUpdates1.await()
        val updates2 = deferredUpdates2.await()

        assertEquals(3, updates1.size)
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(1))), Event.Increment),
            updates1[0]
        )
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(2))), Event.Increment),
            updates1[1]
        )
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(1))), Event.Decrement),
            updates1[2]
        )

        assertEquals(1, updates2.size)
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(1))), Event.Decrement),
            updates2[0]
        )
    }

    @Test
    fun cancellingRequest() = scope.runBlockingTest {
        val deferredUpdates = async(start = CoroutineStart.UNDISPATCHED) {
            tested.updates().toList()
        }
        launch {
            tested.send(Event.Send(1000))
            delay(10)
            tested.send(Event.CancelSending)
            tested.interrupt()
        }
        val updates = deferredUpdates.await()

        assertEquals(2, updates.size)
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(1))),
                Event.Send(1000)
            ), updates[0]
        )
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(null))),
                Event.CancelSending
            ), updates[1]
        )
    }

    @Test
    fun cancellingCompletedRequest() = scope.runBlockingTest {
        val deferredUpdates = async(start = CoroutineStart.UNDISPATCHED) {
            tested.updates().toList()
        }
        launch {
            tested.send(Event.Send(1))
            delay(10)
            tested.send(Event.CancelSending)
            tested.interrupt()
        }
        val updates = deferredUpdates.await()

        assertEquals(3, updates.size)
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(1))),
                Event.Send(1)
            ), updates[0]
        )
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(null))),
                Event.CounterSent
            ), updates[1]
        )
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel),
                Event.CancelSending
            ), updates[2]
        )
    }

    @Test
    fun cancellingParentJob() = scope.runBlockingTest {
        val deferredUpdates = async(start = CoroutineStart.UNDISPATCHED) {
            tested.updates().toList()
        }
        launch {
            tested.send(Event.Send(1000))
            delay(10)
            job.cancel()
        }
        val updates = deferredUpdates.await()

        assertEquals(1, updates.size)
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(1))),
                Event.Send(1000)
            ), updates[0]
        )
        assertTrue(tested.areChannelsClosed())
    }

    @Test(expected = IllegalStateException::class)
    fun creatingWithoutJob() {
        tested = makeEmpressBackend(job = null)
    }

    @Test
    fun storedPatches() = scope.runBlockingTest {
        val storedPatches = listOf(Patch.Counter(3))
        tested = makeEmpressBackend(job, storedPatches = storedPatches)
        assertEquals(Model(listOf(Patch.Counter(3), Patch.Sender(null))), tested.modelSnapshot())
    }

    private fun makeEmpressBackend(
        job: Job?,
        storedPatches: Collection<Patch>? = null
    ): DefaultEmpressBackend<Event, Patch, Request> {
        val coroutineScope = when (job) {
            null -> scope
            else -> scope + job
        }

        return DefaultEmpressBackend(
            empress,
            requestIdProducer,
            requestHolder,
            coroutineScope,
            storedPatches
        )
    }

    private val empress = object : Empress<Event, Patch, Request> {
        override fun initializer(): Collection<Patch> = listOf(
            Patch.Counter(0),
            Patch.Sender(null)
        )

        override fun onEvent(
            event: Event,
            model: Model<Patch>,
            requests: Requests<Event, Request>
        ): Collection<Patch> {
            return when (event) {
                Event.Decrement -> listOf(model.get<Patch.Counter>().let { it.copy(count = it.count - 1) })
                Event.Increment -> listOf(model.get<Patch.Counter>().let { it.copy(count = it.count + 1) })
                is Event.Send -> run<Collection<Patch>> {
                    val requestId = requests.post(Request.Send(event.delayMillis))
                    listOf(Patch.Sender(requestId))
                }
                Event.CancelSending -> run {
                    val sender = model.get<Patch.Sender>()
                    if (requests.cancel(sender.requestId)) {
                        listOf(Patch.Sender(null))
                    } else {
                        emptyList()
                    }
                }
                Event.CounterSent -> listOf(Patch.Sender(null))
            }
        }

        override suspend fun onRequest(request: Request): Event {
            return when (request) {
                is Request.Send -> run {
                    delay(request.delayMillis)
                    Event.CounterSent
                }
            }
        }
    }

    internal sealed class Event {
        object Decrement : Event()
        object Increment : Event()
        data class Send(val delayMillis: Long) : Event()
        object CancelSending : Event()
        object CounterSent : Event()
    }

    internal sealed class Patch {
        data class Counter(val count: Int) : Patch()
        data class Sender(val requestId: RequestId? = null) : Patch()
    }

    internal sealed class Request {
        data class Send(val delayMillis: Long) : Request()
    }
}
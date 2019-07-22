package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException
import kotlin.coroutines.CoroutineContext

class DefaultEmpressBackendTest {
    private val baseModel = Model(listOf(Patch.Counter(0), Patch.Sender(null)))

    private lateinit var coroutineContext: CoroutineContext
    private lateinit var job: Job
    private lateinit var requestIdProducer: RequestIdProducer
    private lateinit var requestHolder: RequestHolder
    private lateinit var tested: DefaultEmpressBackend<Event, Patch, Request>

    @Before
    fun setUp() {
        requestIdProducer = DefaultRequestIdProducer()
        requestHolder = DefaultRequestHolder()
        job = Job()
        tested = makeEmpressBackend(job)
    }

    @After
    fun tearDown() {
        job.cancel()
    }

    @Test
    fun simpleUsage() {
        assertEquals(baseModel, tested.model)

        val updates = runBlocking {
            val deferredUpdates = async {
                tested.updates().toList()
            }
            launch {
                tested.send(Event.Increment, closeUpdates = true)
            }
            deferredUpdates.await()
        }

        assertEquals(2, updates.size)
        assertEquals(Update<Event, Patch>(baseModel), updates[0])
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(1))), Event.Increment),
            updates[1]
        )
    }

    @Test
    fun simpleUsageWithRequest() {
        val updates = runBlocking {
            val deferredUpdates = async {
                tested.updates().toList()
            }
            launch {
                tested.send(Event.Send(1), closeUpdates = true)
            }
            deferredUpdates.await()
        }

        assertEquals(3, updates.size)
        assertEquals(Update<Event, Patch>(baseModel), updates[0])
        assertEquals(
            Update<Event, Patch>(Model(baseModel, listOf(Patch.Sender(1))), Event.Send(1)),
            updates[1]
        )
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(null))),
                Event.CounterSent
            ), updates[2]
        )
    }

    @Test
    fun delayedFirstObserver() = runBlocking {
        launch {
            tested.send(Event.Increment)
            tested.send(Event.Increment)
        }
        val deferredUpdates = async {
            tested.updates().toList()
        }
        launch {
            tested.send(Event.Decrement, closeUpdates = true)
        }
        val updates = deferredUpdates.await()

        assertEquals(2, updates.size)
        assertEquals(
            Update<Event, Patch>(Model(baseModel, listOf(Patch.Counter(2)))),
            updates[0]
        )
        assertEquals(
            Update<Event, Patch>(Model(baseModel, listOf(Patch.Counter(1))), Event.Decrement),
            updates[1]
        )

        assertEquals(1, tested.model.get<Patch.Counter>().count)
    }

    @Test
    fun twoObservers() {
        val updates = runBlocking {
            val deferredUpdates1 = async {
                tested.updates().toList()
            }
            val deferredUpdates2 = async {
                tested.updates().toList()
            }
            launch {
                tested.send(Event.Decrement)
                tested.send(Event.Decrement, closeUpdates = true)
            }
            val updates1 = deferredUpdates1.await()
            val updates2 = deferredUpdates2.await()
            assertEquals(updates1, updates2)
            updates1
        }

        assertEquals(3, updates.size)
        assertEquals(Update<Event, Patch>(baseModel), updates[0])
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(-1))), Event.Decrement),
            updates[1]
        )
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(-2))), Event.Decrement),
            updates[2]
        )
    }

    @Test
    fun delayedSecondObserver() = runBlocking {
        val deferredUpdates1 = async {
            tested.updates().toList()
        }

        launch {
            tested.send(Event.Increment)
            tested.send(Event.Increment)
        }

        val deferredUpdates2 = async {
            tested.updates().toList()
        }

        launch {
            tested.send(Event.Decrement, closeUpdates = true)
        }

        val updates1 = deferredUpdates1.await()
        val updates2 = deferredUpdates2.await()

        assertEquals(4, updates1.size)
        assertEquals(Update<Event, Patch>(baseModel), updates1[0])
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(1))), Event.Increment),
            updates1[1]
        )
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(2))), Event.Increment),
            updates1[2]
        )
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(1))), Event.Decrement),
            updates1[3]
        )

        assertEquals(2, updates2.size)
        assertEquals(
            Update<Event, Patch>(Model(baseModel, listOf(Patch.Counter(2)))),
            updates2[0]
        )
        assertEquals(
            Update(Model(baseModel, listOf(Patch.Counter(1))), Event.Decrement),
            updates2[1]
        )

        assertEquals(1, tested.model.get<Patch.Counter>().count)
    }

    @Test
    fun cancellingRequest() {
        val updates = runBlocking {
            val deferredUpdates = async {
                tested.updates().toList()
            }
            launch {
                tested.send(Event.Send(1000))
                delay(10)
                tested.send(Event.CancelSending, closeUpdates = true)
            }
            deferredUpdates.await()
        }

        assertEquals(3, updates.size)
        assertEquals(Update<Event, Patch>(baseModel), updates[0])
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(1))),
                Event.Send(1000)
            ), updates[1]
        )
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(null))),
                Event.CancelSending
            ), updates[2]
        )
    }

    @Test
    fun cancellingCompletedRequest() {
        val updates = runBlocking {
            val deferredUpdates = async {
                tested.updates().toList()
            }
            launch {
                tested.send(Event.Send(1))
                delay(10)
                tested.send(Event.CancelSending, closeUpdates = true)
            }
            deferredUpdates.await()
        }

        assertEquals(4, updates.size)
        assertEquals(Update<Event, Patch>(baseModel), updates[0])
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(1))),
                Event.Send(1)
            ), updates[1]
        )
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(null))),
                Event.CounterSent
            ), updates[2]
        )
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel),
                Event.CancelSending
            ), updates[3]
        )
    }

    @Test
    fun cancellingParentJob() {
        val updates = runBlocking {
            val deferredUpdates = async {
                tested.updates().toList()
            }
            launch {
                tested.send(Event.Send(1000))
                delay(10)
                job.cancel()
            }
            deferredUpdates.await()
        }

        assertEquals(2, updates.size)
        assertEquals(Update<Event, Patch>(baseModel), updates[0])
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(1))),
                Event.Send(1000)
            ), updates[1]
        )
    }

    @Test(expected = IllegalStateException::class)
    fun creatingWithoutJob() {
        tested = makeEmpressBackend(job = null)
    }

    @Test
    fun storedPatches() {
        val storedPatches = listOf(Patch.Counter(3))
        tested = makeEmpressBackend(job, storedPatches)
        assertEquals(Model(listOf(Patch.Counter(3), Patch.Sender(null))), tested.model)
    }

    private fun makeEmpressBackend(
        job: Job?,
        storedPatches: Collection<Patch>? = null
    ): DefaultEmpressBackend<Event, Patch, Request> {
        coroutineContext = if (job != null) {
            Dispatchers.Unconfined + job
        } else {
            Dispatchers.Unconfined
        }
        return DefaultEmpressBackend(
            coroutineContext,
            empress,
            requestIdProducer,
            requestHolder,
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
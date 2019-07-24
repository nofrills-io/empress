package io.nofrills.empress

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultEmpressBackendTest {
    private val baseModel = Model(
        listOf(
            Patch.Counter(0),
            Patch.Loader(null),
            Patch.Sender(null)
        )
    )
    private val empress = TestEmpress()
    private var testScope: TestCoroutineScope? = null

    @After
    fun tearDown() {
        testScope?.cleanupTestCoroutines()
    }

    @Test
    fun simpleUsage() = usingTestScope { tested ->
        assertEquals(baseModel, tested.modelSnapshot())

        val deferredUpdates = async {
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
    fun simpleUsageWithRequest() = usingTestScope { tested ->
        val deferredUpdates = async {
            tested.updates().toList()
        }

        tested.send(Event.Send(1))
        tested.interrupt()

        val updates = deferredUpdates.await()

        assertEquals(2, updates.size)
        assertEquals(
            Update<Event, Patch>(
                Model(baseModel, listOf(Patch.Sender(RequestId(1)))),
                Event.Send(1)
            ),
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
    fun delayedFirstObserver() = usingTestScope { tested ->
        tested.send(Event.Increment)
        tested.send(Event.Increment)

        val deferredUpdates = async {
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
    fun twoObservers() = usingTestScope { tested ->
        val deferredUpdates1 = async {
            tested.updates().toList()
        }
        val deferredUpdates2 = async {
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
    fun delayedSecondObserver() = usingTestScope { tested ->
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
    fun cancellingRequest() = usingTestScope { tested ->
        val deferredUpdates = async {
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
                Model(baseModel, listOf(Patch.Sender(RequestId(1)))),
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
    fun cancellingCompletedRequest() = usingTestScope { tested ->
        val deferredUpdates = async {
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
                Model(baseModel, listOf(Patch.Sender(RequestId(1)))),
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
    fun cancellingOneOfTwoRequests() = usingTestScope { tested ->
        val deferredUpdates = async {
            tested.updates().toList()
        }
        launch {
            tested.send(Event.Load(1000))
            tested.send(Event.Send(1000))
            delay(10)
            tested.send(Event.CancelSending)
            tested.interrupt()
        }
        val updates = deferredUpdates.await()

        assertEquals(4, updates.size)

        val model0 = Model(baseModel, listOf(Patch.Loader(RequestId(1))))
        assertEquals(
            Update<Event, Patch>(
                model0,
                Event.Load(1000)
            ), updates[0]
        )

        val model1 = Model(model0, listOf(Patch.Sender(RequestId(2))))
        assertEquals(
            Update<Event, Patch>(
                model1,
                Event.Send(1000)
            ), updates[1]
        )

        val model2 = Model(model1, listOf(Patch.Sender(null)))
        assertEquals(
            Update<Event, Patch>(
                model2,
                Event.CancelSending
            ), updates[2]
        )

        val model3 = Model(model2, listOf(Patch.Loader(null)))
        assertEquals(
            Update<Event, Patch>(
                model3,
                Event.Loaded
            ), updates[3]
        )
    }

    @Test
    fun cancellingParentJob() = runBlockingTest {
        val job = Job()
        val tested = makeEmpressBackend(CoroutineScope(Dispatchers.Unconfined + job))

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
                Model(baseModel, listOf(Patch.Sender(RequestId(1)))),
                Event.Send(1000)
            ), updates[0]
        )
        assertTrue(tested.areChannelsClosed())
    }

    @Test
    fun storedPatches() = runBlockingTest {
        val storedPatches = listOf(Patch.Counter(3))
        val tested = makeEmpressBackend(
            CoroutineScope(Dispatchers.Unconfined),
            storedPatches = storedPatches
        )
        assertEquals(
            Model(listOf(Patch.Counter(3), Patch.Loader(null), Patch.Sender(null))),
            tested.modelSnapshot()
        )
        tested.interrupt()
    }

    private fun usingTestScope(block: suspend TestCoroutineScope.(DefaultEmpressBackend<Event, Patch, Request>) -> Unit) {
        val scope = TestCoroutineScope()
        testScope = scope
        val empressBackend = makeEmpressBackend(scope)
        scope.runBlockingTest {
            block.invoke(scope, empressBackend)
        }
    }

    private fun makeEmpressBackend(
        coroutineScope: CoroutineScope,
        storedPatches: Collection<Patch>? = null
    ): DefaultEmpressBackend<Event, Patch, Request> {
        val requestIdProducer = DefaultRequestIdProducer()
        val requestHolder = DefaultRequestHolder()
        return DefaultEmpressBackend(
            empress,
            requestIdProducer,
            requestHolder,
            coroutineScope,
            storedPatches
        )
    }
}
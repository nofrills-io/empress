/*
 * Copyright 2019 Mateusz Armatys
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

package io.nofrills.empress

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmpressBackendTest :
    RulerBackendTest<EmpressBackend<Event, Model, Request>, Empress<Event, Model, Request>>() {

    override fun makeRuler(initializeWithDuplicate: Boolean): Empress<Event, Model, Request> {
        return TestEmpress(initializeWithDuplicate)
    }

    override fun makeBackend(
        ruler: Empress<Event, Model, Request>,
        eventHandlerScope: CoroutineScope,
        requestHandlerScope: CoroutineScope,
        storedModels: Collection<Model>?
    ): EmpressBackend<Event, Model, Request> {
        return EmpressBackend(ruler, eventHandlerScope, requestHandlerScope, storedModels)
    }

    @Test
    fun updates() = usingTestScope { tested ->
        val deferredUpdates = async {
            tested.updates().toList()
        }

        tested.post(Event.Increment)
        tested.interrupt()

        val updates = deferredUpdates.await()
        assertEquals(1, updates.size)
        assertEquals(
            ModelsImpl(
                mapOf(
                    Model.Counter::class.java to Model.Counter(1),
                    Model.Sender::class.java to Model.Sender(null)
                )
            ), updates[0].all
        )
        assertEquals(Event.Increment, updates[0].event)
        assertEquals(listOf(Model.Counter(1)), updates[0].updated)
    }
}

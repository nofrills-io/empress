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

package io.nofrills.empress.sample

import io.nofrills.empress.EmpressApi
import io.nofrills.empress.EmpressBackend
import io.nofrills.empress.test_support.Event
import io.nofrills.empress.test_support.Patch
import io.nofrills.empress.test_support.SampleEmpress
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class SampleTest {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: EmpressApi<Event, Patch>

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
    fun example() = scope.runBlockingTest {
        val deferredUpdates = async { tested.updates().toCollection(mutableListOf()) }

        Assert.assertEquals(Patch.Counter(0), tested.modelSnapshot().get<Patch.Counter>())

        tested.send(Event.Increment)
        tested.send(Event.Increment)
        Assert.assertEquals(Patch.Counter(2), tested.modelSnapshot().get<Patch.Counter>())

        tested.send(Event.SendCounter)

        tested.interrupt()

        val updates = deferredUpdates.await()
        Assert.assertEquals(4, updates.size)
    }
}
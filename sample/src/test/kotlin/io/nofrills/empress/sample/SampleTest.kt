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

import io.nofrills.empress.base.EmpressBackend
import io.nofrills.empress.base.RequestId
import io.nofrills.empress.base.TestEmpressApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SampleTest {
    private lateinit var scope: TestCoroutineScope
    private lateinit var tested: TestEmpressApi<SampleEmpress, Model, Signal>

    @Before
    fun setUp() {
        scope = TestCoroutineScope()
        tested = EmpressBackend(SampleEmpress(), scope, scope)
    }

    @After
    fun tearDown() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun example() = scope.runBlockingTest {
        val deferredUpdates = async {
            val list = mutableListOf<Model>()
            launch { tested.listen { counter }.collect { list.add(it) } }
            launch { tested.listen { sender }.collect { list.add(it) } }
            list
        }

        tested.post { increment() }
        tested.post { increment() }
        tested.post { sendCounter() }
        tested.interrupt()

        deferredUpdates.cancelChildren()
        val updates = deferredUpdates.await()
        val expected = listOf(
            Model.Counter(0),
            Model.Sender(SenderState.Idle),
            Model.Counter(1),
            Model.Counter(2),
            Model.Sender(SenderState.Sending(RequestId(1))),
            Model.Sender(SenderState.Idle)
        )
        assertEquals(expected, updates)
    }
}

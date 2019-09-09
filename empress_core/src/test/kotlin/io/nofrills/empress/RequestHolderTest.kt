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

import io.nofrills.empress.internal.RequestHolder
import io.nofrills.empress.internal.RequestIdImpl
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RequestHolderTest {
    private lateinit var tested: RequestHolder

    @Before
    fun setUp() {
        tested = RequestHolder()
    }

    @Test
    fun initialState() {
        assertTrue(tested.isEmpty())
        assertNull(tested.pop(RequestIdImpl(1)))
    }

    @Test
    fun pushPop() {
        val requestId = RequestIdImpl(1)
        val job = Job()
        tested.push(requestId, job)

        assertFalse(tested.isEmpty())
        assertSame(job, tested.pop(requestId))
        assertNull(tested.pop(requestId))
    }
}

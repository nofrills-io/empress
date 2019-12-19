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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConsumableTest {
    private lateinit var effectCommander: MockEffectCommander

    @Before
    fun setUp() {
        effectCommander = MockEffectCommander()
    }

    @Test
    fun noEffect() {
        val c: Consumable<Int, String> = consumableOf(15)
        assertTrue(c.isConsumed)
        assertEquals(15, c.peek())
        assertEquals(15, c.consume(effectCommander))
        assertTrue(c.isConsumed)
        assertNull(effectCommander.popPostedEffect())
    }

    @Test
    @UseExperimental(ExperimentalCoroutinesApi::class)
    fun singleEffect() = runBlockingTest {
        val c: Consumable<Int, String> = consumableOf(15) { "eff" }
        assertFalse(c.isConsumed)
        assertEquals(15, c.peek())
        assertFalse(c.isConsumed)
        assertNull(effectCommander.popPostedEffect())

        assertEquals(15, c.consume(effectCommander))
        assertTrue(c.isConsumed)
        assertEquals("eff", effectCommander.popPostedEffect()?.invoke())

        assertEquals(15, c.consume(effectCommander))
        assertTrue(c.isConsumed)
        assertNull(effectCommander.popPostedEffect())
    }

    private class MockEffectCommander : EffectCommander<String> {
        private var postedEffect: Effect<String>? = null

        override fun post(effect: Effect<String>) {
            postedEffect = effect
        }

        internal fun popPostedEffect(): Effect<String>? {
            return postedEffect.also {
                postedEffect = null
            }
        }
    }
}

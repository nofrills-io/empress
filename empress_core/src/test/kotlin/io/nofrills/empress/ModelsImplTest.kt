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

import io.nofrills.empress.internal.ModelsImpl
import org.junit.Assert.*
import org.junit.Test

class ModelsImplTest {
    private lateinit var tested: ModelsImpl<Any>

    @Test
    fun emptyState() {
        tested = ModelsImpl(emptyMap())
        assertEquals(emptyList<Any>(), tested.all())
    }

    @Test(expected = NoSuchElementException::class)
    fun emptyNonExistent() {
        tested = ModelsImpl(emptyMap())
        tested[String::class]
    }

    @Test(expected = NoSuchElementException::class)
    fun relatedClasses() {
        val modelMap = mapOf<Class<out Any>, Any>(CharSequence::class.java to "hi")
        tested = ModelsImpl(modelMap)
        tested[String::class]
    }

    @Test
    fun twoModels() {
        val modelMap = mapOf(String::class.java to "hello", Int::class.java to 123)
        tested = ModelsImpl(modelMap)
        assertEquals("hello", tested[String::class])
        assertEquals(123, tested[Int::class])
    }

    @Test
    fun mutating() {
        val obj = object {
            var a: String = "a"
            var b: Int = 5
        }
        val modelMap = mapOf<Class<out Any>, Any>(obj::class.java to obj)
        tested = ModelsImpl(modelMap)

        assertEquals("a", tested[obj::class].a)
        assertEquals(5, tested[obj::class].b)

        obj.a = "hello"

        assertEquals("hello", tested[obj::class].a)
        assertEquals(5, tested[obj::class].b)
    }
}

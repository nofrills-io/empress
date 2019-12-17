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

import java.io.Serializable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/** Allows to access the models. */
interface Models<M : Any> {
    /** Returns all models. */
    fun all(): Collection<M>

    /** Returns a model with the given [modelClass]. */
    operator fun <T : M> get(modelClass: Class<T>): T

    /** Returns a model with the given [modelClass]. */
    operator fun <T : M> get(modelClass: KClass<T>): T
}

/** Initializes the models. */
interface ModelInitializer<out M : Any> {
    /** Initializer should return a collection of all possible models. */
    fun initialize(): Collection<M>
}

open class Consumable<E : Any, V> constructor(
    private val value: V,
    private val effect: Effect<E>? = null
) : Serializable {
    private val atomicIsConsumed = AtomicBoolean(false)

    val isConsumed: Boolean
        get() = atomicIsConsumed.get()

    fun consume(effectHandler: EffectHandler<E>): V {
        if (!atomicIsConsumed.getAndSet(true) && effect != null) {
            effectHandler.post(effect)
        }
        return value
    }

    fun peek(): V {
        return value
    }
}

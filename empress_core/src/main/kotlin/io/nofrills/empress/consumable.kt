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

/** Wraps an object that can be consumed.
 * When it's [consumed][consume], an [Effect] may be produced.
 */
interface Consumable<V, E : Any> : Serializable {
    val isConsumed: Boolean
    fun consume(effectCommander: EffectCommander<E>): V
    fun peek(): V
}

/** A [Consumable] that when it's consumed, produces no [Effect]. */
internal class IneffectiveConsumable<V, E : Any>(private val value: V) : Consumable<V, E> {
    override val isConsumed: Boolean = true
    override fun consume(effectCommander: EffectCommander<E>): V = value
    override fun peek(): V = value
}

/** A [Consumable] that when it's consumed for the first time, produces an [effect]. */
internal class SingleEffectConsumable<V, E : Any> constructor(
    private val value: V,
    private val effect: Effect<E>
) : Consumable<V, E> {
    private val atomicIsConsumed = AtomicBoolean(false)

    override val isConsumed: Boolean
        get() = atomicIsConsumed.get()

    override fun consume(effectCommander: EffectCommander<E>): V {
        if (!atomicIsConsumed.getAndSet(true)) {
            effectCommander.post(effect)
        }
        return value
    }

    override fun peek(): V {
        return value
    }
}

/** Creates a [Consumable] for a given [value] and [effect]. */
fun <V, E : Any> consumableOf(value: V, effect: Effect<E>? = null): Consumable<V, E> {
    return if (effect != null) {
        SingleEffectConsumable(value, effect)
    } else {
        IneffectiveConsumable(value)
    }
}

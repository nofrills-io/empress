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

/** Common interface for defining models and handling requests. */
interface Ruler<E : Any, M : Any, R : Any> : ModelInitializer<M>, RequestHandler<E, R>

/** Common interface for managing a [Ruler]. */
interface RulerApi<E : Any, M : Any> : EffectCommander<E>, EventCommander<E>, EventListener<E> {
    /** Interrupts event processing loop.
     * Usually only needed in tests.
     */
    fun interrupt()

    /** Return current snapshot of the model.
     * Usually you'll want to obtain all models to render initial state.
     */
    fun models(): Models<M>
}

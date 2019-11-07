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

/** Allows to define __mutable__ models, event and request handlers. */
interface MutableEmpress<E : Any, M : Any, R : Any> : Ruler<E, M, R> {
    /** Handles an incoming [event].
     * @param event An event that was triggered.
     * @param models Current models.
     * @param requests Allows to post new requests or cancel existing ones.
     */
    fun onEvent(event: E, models: Models<M>, requests: RequestCommander<R>)
}

/** Allows to manage an [MutableEmpress] instance. */
interface MutableEmpressApi<E : Any, M : Any> : EventCommander<E>, EventListener<E>, RulerApi {
    /** Returns current models. */
    fun models(): Models<M>
}

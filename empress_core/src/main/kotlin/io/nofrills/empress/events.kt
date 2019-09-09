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

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/** Event handler for models with immutable fields. */
interface ImmutableEventHandler<E : Any, M : Any, R : Any> {
    /** Handles an incoming [event].
     * @param event An event that was triggered.
     * @param models Current models.
     * @param requests Allows to post new requests or cancel existing ones.
     * @return A collection of updated patches. You should only return patches that have changed.
     *  If nothing has changed, you can return an empty collection. Based on updated patches,
     *  a new [update][Update] will be sent.
     */
    fun onEvent(event: E, models: Models<M>, requests: RequestCommander<R>): Collection<M>
}

/** Event handler for models with mutable fields. */
interface MutableEventHandler<E : Any, M : Any, R : Any> {
    /** Handles an incoming [event].
     * @param event An event that was triggered.
     * @param models Current models.
     * @param requests Allows to post new requests or cancel existing ones.
     */
    fun onEvent(event: E, models: Models<M>, requests: RequestCommander<R>)
}

/** Allows to send events and listen to processed events. */
interface EventCommander<E : Any> {
    /** Allows to listen for event that have been handled. */
    fun events(): Flow<E>

    /** Sends an [event] for processing. */
    fun post(event: E): Job
}

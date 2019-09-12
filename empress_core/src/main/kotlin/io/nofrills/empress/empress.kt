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

import kotlinx.coroutines.flow.Flow

/** Allows to define __immutable__ models, event and request handlers. */
interface Empress<E : Any, M : Any, R : Any> : Ruler<E, M, R> {
    /** Handles an incoming [event].
     * @param event An event that was triggered.
     * @param models Current models.
     * @param requests Allows to post new requests or cancel existing ones.
     * @return A collection of updated models. You should only return models that have changed.
     *  If nothing has changed, you can return an empty collection. Based on updated models,
     *  a new [update][Update] will be sent.
     */
    fun onEvent(event: E, models: Models<M>, requests: RequestCommander<R>): Collection<M>
}

/** Represents an update, that resulted from processing an [event].
 * @property all Set of all models
 * @property event Event that triggered the update.
 * @property updated Models that were updated as a result of processing an event.
 */
interface Update<E : Any, M : Any> {
    val all: Models<M>
    val event: E
    val updated: Collection<M>
}

/** Allows to manage an [Empress] instance. */
interface EmpressApi<E : Any, M : Any> : EventCommander<E>, RulerApi {
    /** Return current snapshot of the model.
     * Usually you want to obtain whole model when starting the application.
     */
    suspend fun models(): Models<M>

    /** Allows to listen for [updates][Update]. */
    fun updates(): Flow<Update<E, M>>
}

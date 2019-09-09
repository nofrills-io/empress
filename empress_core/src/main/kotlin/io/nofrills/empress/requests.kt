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

/** Represents a running request. */
interface RequestId

/** Manages requests. */
interface RequestCommander<R : Any> {
    /** Cancels a request
     * @param requestId An id of a request.
     * @return True if request was cancelled; false if [requestId] was null, or the request was already completed.
     */
    fun cancel(requestId: RequestId?): Boolean

    /** Posts a new [request] for execution. */
    fun post(request: R): RequestId
}

/** Handles requests. */
interface RequestHandler<E : Any, R : Any> {
    /** Handles an incoming [request].
     * @return An event with the result, that will be fed back to an event handler.
     */
    suspend fun onRequest(request: R): E
}

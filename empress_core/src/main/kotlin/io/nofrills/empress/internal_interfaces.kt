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

/** Manages currently active requests. */
interface RequestHolder {
    /** Checks if there are active requests.
     * @return True if there are no running or scheduled requests.
     */
    fun isEmpty(): Boolean

    /** Removes a request from the manager.
     * @return A [job][Job] for the request or null if the request was not found or has already completed.
     */
    fun pop(requestId: RequestId): Job?

    /** Stores a [job][Job] associated with [requestId]. */
    fun push(requestId: RequestId, requestJob: Job)

    /** Returns current list of [jobs][Job] for currently running or scheduled requests. */
    fun snapshot(): Sequence<Job>
}

/** Generates identifiers for requests. */
interface RequestIdProducer {
    /** Returns a unique ID for a request. */
    fun getNextRequestId(): RequestId
}

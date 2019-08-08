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

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal class ModelHolder<Patch : Any>(private var model: Model<Patch>) {
    private val mutex: Mutex = Mutex()

    suspend fun get(): Model<Patch> {
        return mutex.withLock {
            model
        }
    }

    /** Updates the model.
     * @param updater Function which takes current model and returns a new, updated one.
     * @return A new, updated model.
     */
    suspend fun update(updater: (Model<Patch>) -> Model<Patch>): Model<Patch> {
        return mutex.withLock {
            model = updater(model)
            model
        }
    }
}

/** Default implementation for handling requests. */
internal class RequestsImpl<Event, Request> constructor(
    private val idProducer: RequestIdProducer,
    private val onRequest: suspend (Request) -> Event,
    private val requestHolder: RequestHolder,
    private val scope: CoroutineScope,
    private val sendEvent: suspend (Event) -> Unit
) : Requests<Event, Request> {
    override fun cancel(requestId: RequestId?): Boolean {
        requestId ?: return false
        val requestJob = requestHolder.pop(requestId) ?: return false

        // Since we have a non-null requestJob, it means it hasn't been completed yet
        requestJob.cancel()
        return true
    }

    override fun post(request: Request): RequestId {
        val requestId = idProducer.getNextRequestId()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val eventFromRequest = onRequest(request)
            if (requestHolder.pop(requestId) != null) {
                sendEvent(eventFromRequest)
            }
        }
        job.invokeOnCompletion {
            // make sure to clean up, in case there were errors in handling the request
            requestHolder.pop(requestId)
        }
        requestHolder.push(requestId, job)
        return requestId
    }
}

/** Manages currently active requests. */
internal class RequestHolder {
    private val requestMap: MutableMap<RequestId, Job> = ConcurrentHashMap()

    /** Checks if there are active requests.
     * @return True if there are no running or scheduled requests.
     */
    fun isEmpty(): Boolean {
        return requestMap.isEmpty()
    }

    /** Removes a request from the manager.
     * @return A [job][Job] for the request or null if the request was not found or has already completed.
     */
    fun pop(requestId: RequestId): Job? {
        return requestMap.remove(requestId)
    }

    /** Stores a [job][Job] associated with [requestId]. */
    fun push(requestId: RequestId, requestJob: Job) {
        requestMap[requestId] = requestJob
    }

    /** Returns current list of [jobs][Job] for currently running or scheduled requests. */
    fun snapshot(): Sequence<Job> {
        return requestMap.values.asSequence()
    }
}

/** Generates identifiers for requests. */
internal class RequestIdProducer {
    private var nextRequestId: Int = 0

    /** Returns a unique ID for a request. */
    fun getNextRequestId(): RequestId {
        nextRequestId += 1
        return RequestId(nextRequestId)
    }
}

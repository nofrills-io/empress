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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal data class RequestIdImpl constructor(private val id: Int) : RequestId

internal class RequestCommanderImpl<E : Any, R : Any> constructor(
    private val idProducer: RequestIdProducer,
    private val requestHandler: RequestHandler<E, R>,
    private val requestHolder: RequestHolder,
    private val scope: CoroutineScope,
    private val sendEvent: suspend (E) -> Unit
) : RequestCommander<R> {
    override fun cancel(requestId: RequestId?): Boolean {
        requestId ?: return false
        val requestJob = requestHolder.pop(requestId) ?: return false

        // Since we have a non-null requestJob, it means it hasn't been completed yet
        requestJob.cancel()
        return true
    }

    override fun post(request: R): RequestId {
        val requestId = idProducer.getNextRequestId()
        val job = scope.launch {
            val eventFromRequest = requestHandler.onRequest(request)
            if (requestHolder.pop(requestId) != null) {
                sendEvent(eventFromRequest)
            }
        }
        requestHolder.push(requestId, job)
        job.invokeOnCompletion {
            requestHolder.pop(requestId)
        }
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
}

/** Generates identifiers for requests. */
internal class RequestIdProducer {
    private var nextRequestId: Int = 0

    /** Returns a unique ID for a request. */
    fun getNextRequestId(): RequestId {
        nextRequestId += 1
        return RequestIdImpl(nextRequestId)
    }
}

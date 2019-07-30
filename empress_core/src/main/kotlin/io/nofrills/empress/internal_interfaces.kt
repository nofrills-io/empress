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

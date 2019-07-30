package io.nofrills.empress

import kotlinx.coroutines.flow.Flow

/** The main interface that needs to be implemented.
 * @param Event Signals an event for which we need to take some action.
 * @param Patch Represents application state. Usually modelled as a sealed class,
 *  where each subclass is relatively small, and is related to a single aspect of the app.
 * @param Request Denotes an intent for obtaining some resource asynchronously.
 */
interface Empress<Event, Patch : Any, Request> {
    /** Initializer should return a collection of all possible patches.
     * Forgetting to return an initializer for a particular [patch subclass][Patch],
     * may result in an error in [onEvent] method.
     */
    fun initializer(): Collection<Patch>

    /** Handles an incoming [event].
     * @param event An event that was triggered.
     * @param model Current model.
     * @param requests Allows to post new requests or cancel existing ones.
     * @return A collection of updated patches. You should only return patches that have changed.
     *  If nothing has changed, you can return an empty collection. Based on updated patches,
     *  a new [update][Update] will be sent.
     */
    fun onEvent(
        event: Event,
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch>

    /** Handles an incoming [request].
     * @return An event with the result, that will be fed back to [onEvent] method.
     */
    suspend fun onRequest(request: Request): Event
}

/** Interface for interacting with a running empress system. */
interface EmpressApi<Event, Patch : Any> {
    /** Interrupts the empress loop. Usually only needed in tests. */
    fun interrupt()

    /** Return current snapshot of the model.
     * Usually you want to obtain whole model when starting the application.
     */
    suspend fun modelSnapshot(): Model<Patch>

    /** Sends an [event] for processing. */
    fun send(event: Event)

    /** Allows to listen for [updates][Update].
     * When receiving an update, you can check [Model.updated] to see which patches have changed.
     */
    fun updates(): Flow<Update<Event, Patch>>
}

/** Allows to manage any additional asynchronous requests (e.g. downloading data from a server). */
interface Requests<Event, Request> {
    /** Cancels a request with given [requestId].
     * @return True if the request has been cancelled; false if [requestId] was null, or the request has already completed.
     */
    fun cancel(requestId: RequestId?): Boolean

    /** Posts a [request] to be executed asynchronously.
     * @return An id that can be used to [cancel] the request.
     */
    fun post(request: Request): RequestId
}

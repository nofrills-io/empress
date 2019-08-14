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

package io.nofrills.empress.builder

import io.nofrills.empress.Empress
import io.nofrills.empress.Model
import io.nofrills.empress.Requests

/** Context for handling events.
 * @param event Event that has been triggered.
 * @param model Current model.
 * @param requests Allows to create or cancel requests.
 */
class EventHandlerContext<Event, Patch : Any, Request>(
    val event: Event,
    val model: Model<Patch>,
    val requests: Requests<Event, Request>
)

/** Context for handling requests.
 * @param request Request to handle.
 */
class RequestHandlerContext<Request>(val request: Request)

/** Creates an initial patch value. */
typealias PatchInitializer<P> = () -> P

/** Handles an event.
 * Event handler should return a collection of patches that have changed.
 * If nothing has changed, it should return an empty collection.
 */
typealias EventHandler<E, Patch, Request> = EventHandlerContext<E, Patch, Request>.() -> Collection<Patch>

/** Handles (executes) a request. */
typealias RequestHandler<Event, R> = suspend RequestHandlerContext<R>.() -> Event

/** Builds an [Empress] instance.
 * @param id ID for [Empress] instance.
 * @param body Specification for the new [Empress] instance.
 * @return New [Empress].
 */
fun <Event : Any, Patch : Any, Request : Any> empressBuilder(
    id: String,
    body: EmpressBuilder<Event, Patch, Request>.() -> Unit
): Empress<Event, Patch, Request> {
    val builder = EmpressBuilder<Event, Patch, Request>(id)
    body(builder)
    return builder.build()
}

/** Allows to build an [Empress] instance. */
class EmpressBuilder<Event : Any, Patch : Any, Request : Any> internal constructor(private val id: String) {
    private val builderData = EmpressBuilderData<Event, Patch, Request>()

    /** Defines an initializer for a [Patch]. */
    inline fun <reified P : Patch> initializer(noinline body: () -> P) {
        initializer(body, P::class.java)
    }

    /** @see initializer */
    fun <P : Patch> initializer(body: () -> P, patchClass: Class<P>) {
        builderData.addInitializer(body, patchClass)
    }

    /** Defines event handler for an [Event]. */
    inline fun <reified E : Event> onEvent(noinline body: EventHandler<E, Patch, Request>) {
        onEvent(E::class.java, body)
    }

    /** @see onEvent */
    fun <E : Event> onEvent(eventClass: Class<E>, body: EventHandler<E, Patch, Request>) {
        builderData.addOnEvent(body, eventClass)
    }

    /** Defines request handler for a [Request]. */
    inline fun <reified R : Request> onRequest(noinline body: RequestHandler<Event, R>) {
        onRequest(R::class.java, body)
    }

    /** @see onRequest */
    fun <R : Request> onRequest(requestClass: Class<R>, body: RequestHandler<Event, R>) {
        builderData.addOnRequest(body, requestClass)
    }

    internal fun build(): Empress<Event, Patch, Request> {
        return EmpressFromBuilder(
            id,
            builderData.initializers.values,
            builderData.eventHandlers,
            builderData.requestHandlers
        )
    }
}

private class EmpressBuilderData<Event, Patch : Any, Request> {
    internal val initializers = mutableMapOf<Class<out Patch>, PatchInitializer<Patch>>()
    internal val eventHandlers =
        mutableMapOf<Class<out Event>, EventHandler<Event, Patch, Request>>()
    internal val requestHandlers =
        mutableMapOf<Class<out Request>, RequestHandler<Event, Request>>()

    internal fun <P : Patch> addInitializer(initializer: () -> P, patchCls: Class<P>) {
        if (initializers.put(patchCls, initializer) != null) {
            throw IllegalStateException("Initializer for $patchCls was already added.")
        }
    }

    internal fun <E : Event> addOnEvent(
        onEvent: EventHandler<E, Patch, Request>,
        eventCls: Class<E>
    ) {
        @Suppress("UNCHECKED_CAST")
        if (eventHandlers.put(eventCls, onEvent as EventHandler<Event, Patch, Request>) != null) {
            throw IllegalStateException("Handler for $eventCls was already added.")
        }
    }

    internal fun <R : Request> addOnRequest(
        onRequest: RequestHandler<Event, R>,
        requestCls: Class<R>
    ) {
        @Suppress("UNCHECKED_CAST")
        if (requestHandlers.put(requestCls, onRequest as RequestHandler<Event, Request>) != null) {
            throw IllegalStateException("Handler for $requestCls was already added.")
        }
    }
}

private class EmpressFromBuilder<Event : Any, Patch : Any, Request : Any>(
    private val id: String,
    private val initializers: Collection<PatchInitializer<Patch>>,
    private val eventHandlers: Map<Class<out Event>, EventHandler<Event, Patch, Request>>,
    private val requestHandlers: Map<Class<out Request>, RequestHandler<Event, Request>>
) : Empress<Event, Patch, Request> {

    override fun id(): String {
        return id
    }

    override fun initializer(): Collection<Patch> {
        return initializers.map { it.invoke() }
    }

    override fun onEvent(
        event: Event,
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        val eventHandlerContext = EventHandlerContext(event, model, requests)
        return eventHandlers.getValue(event::class.java).invoke(eventHandlerContext)
    }

    override suspend fun onRequest(request: Request): Event {
        val requestHandlerContext = RequestHandlerContext(request)
        return requestHandlers.getValue(request::class.java).invoke(requestHandlerContext)
    }
}

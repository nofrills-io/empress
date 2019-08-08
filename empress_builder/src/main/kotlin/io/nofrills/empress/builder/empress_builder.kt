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

class EventHandlerContext<Event, Patch : Any, Request>(
    val event: Event,
    val model: Model<Patch>,
    val requests: Requests<Event, Request>
)

class RequestHandlerContext<Request>(val request: Request)

typealias PatchInitializer<P> = () -> P
typealias EventHandler<E, Patch, Request> = EventHandlerContext<E, Patch, Request>.() -> Collection<Patch>
typealias RequestHandler<Event, R> = suspend RequestHandlerContext<R>.() -> Event

class EmpressBuilder<Event : Any, Patch : Any, Request : Any> {
    internal val empress = EmpressFromBuilder<Event, Patch, Request>()

    inline fun <reified P : Patch> initializer(noinline body: () -> P) {
        initializer(body, P::class.java)
    }

    fun <P : Patch> initializer(body: () -> P, patchClass: Class<P>) {
        empress.addInitializer(body, patchClass)
    }

    inline fun <reified E : Event> onEvent(noinline body: EventHandler<E, Patch, Request>) {
        onEvent(body, E::class.java)
    }

    fun <E : Event> onEvent(body: EventHandler<E, Patch, Request>, eventClass: Class<E>) {
        empress.addOnEvent(body, eventClass)
    }

    inline fun <reified R : Request> onRequest(noinline body: RequestHandler<Event, R>) {
        onRequest(body, R::class.java)
    }

    fun <R : Request> onRequest(body: RequestHandler<Event, R>, requestClass: Class<R>) {
        empress.addOnRequest(body, requestClass)
    }
}

fun <Event : Any, Patch : Any, Request : Any> empressBuilder(body: EmpressBuilder<Event, Patch, Request>.() -> Unit): Empress<Event, Patch, Request> {

    val builder = EmpressBuilder<Event, Patch, Request>()
    body(builder)
    return builder.empress
}

internal class EmpressFromBuilder<Event : Any, Patch : Any, Request : Any> :
    Empress<Event, Patch, Request> {
    private val initializers = mutableMapOf<Class<out Patch>, PatchInitializer<Patch>>()
    private val eventHandlers =
        mutableMapOf<Class<out Event>, EventHandler<Event, Patch, Request>>()
    private val requestHandlers = mutableMapOf<Class<out Request>, RequestHandler<Event, Request>>()

    override fun initializer(): Collection<Patch> {
        return initializers.map { (_, f) -> f.invoke() }
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

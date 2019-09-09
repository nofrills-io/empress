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

import io.nofrills.empress.*

/** DSL Marker for [EmpressBuilder]. */
@DslMarker
annotation class EmpressDslMarker

/** Context for handling events.
 * @param event Event that has been triggered.
 * @param models Current models.
 * @param requests Allows to create or cancel requests.
 */
@EmpressDslMarker
class EventHandlerContext<E : Any, M : Any, R : Any>(
    val event: E,
    val models: Models<M>,
    val requests: RequestCommander<R>
)

/** Context for handling requests.
 * @param request A request to handle.
 */
@EmpressDslMarker
class RequestHandlerContext<R>(val request: R)

/** Context for patch initializers. */
@EmpressDslMarker
object InitializerContext

/** Handles an event.
 * Event handler should return a collection of patches that have changed.
 * If nothing has changed, it should return an empty collection.
 */
typealias EventHandler<E, M, R> = EventHandlerContext<E, M, R>.() -> Collection<M>

/** Handles (executes) a request. */
typealias RequestHandler<E, R> = suspend RequestHandlerContext<R>.() -> E

/** Creates an initial model value. */
typealias Initializer<M> = InitializerContext.() -> M

/** Builds an [Empress] instance.
 * @param id ID for [Empress] instance.
 * @param body Specification for the new [Empress] instance.
 * @return New [Empress].
 */
@Suppress("FunctionName")
fun <E : Any, M : Any, R : Any> Empress(
    id: String,
    body: EmpressBuilder<E, M, R>.() -> Unit
): Empress<E, M, R> {
    val builder = EmpressBuilder<E, M, R>(id)
    body(builder)
    return builder.build()
}

/** Allows to build an [Empress] instance. */
@EmpressDslMarker
class EmpressBuilder<E : Any, M : Any, R : Any> internal constructor(private val id: String) {
    private val builderData = EmpressBuilderData<E, M, R>()

    /** Defines an initializer for a [M]. */
    inline fun <reified P : M> initializer(noinline body: Initializer<P>) {
        initializer(body, P::class.java)
    }

    /** @see initializer */
    fun <P : M> initializer(body: Initializer<P>, patchClass: Class<P>) {
        builderData.addInitializer(body, patchClass)
    }

    /** Defines event handler for an [E]. */
    inline fun <reified Ev : E> onEvent(noinline body: EventHandler<Ev, M, R>) {
        onEvent(Ev::class.java, body)
    }

    /** @see onEvent */
    fun <Ev : E> onEvent(eventClass: Class<Ev>, body: EventHandler<Ev, M, R>) {
        builderData.addOnEvent(body, eventClass)
    }

    /** Defines request handler for a [R]. */
    inline fun <reified Rq : R> onRequest(noinline body: RequestHandler<E, Rq>) {
        onRequest(Rq::class.java, body)
    }

    /** @see onRequest */
    fun <Rq : R> onRequest(requestClass: Class<Rq>, body: RequestHandler<E, Rq>) {
        builderData.addOnRequest(body, requestClass)
    }

    internal fun build(): Empress<E, M, R> {
        return EmpressFromBuilder(
            id,
            builderData.initializers.values,
            builderData.eventHandlers,
            builderData.requestHandlers
        )
    }
}

private class EmpressBuilderData<E : Any, M : Any, R : Any> {
    internal val initializers = mutableMapOf<Class<out M>, Initializer<M>>()
    internal val eventHandlers = mutableMapOf<Class<out E>, EventHandler<E, M, R>>()
    internal val requestHandlers = mutableMapOf<Class<out R>, RequestHandler<E, R>>()

    internal fun <P : M> addInitializer(initializer: Initializer<P>, patchCls: Class<P>) {
        check(initializers.put(patchCls, initializer) == null) {
            "Initializer for $patchCls was already added."
        }
    }

    internal fun <Ev : E> addOnEvent(
        onEvent: EventHandler<Ev, M, R>,
        eventCls: Class<Ev>
    ) {
        @Suppress("UNCHECKED_CAST")
        check(eventHandlers.put(eventCls, onEvent as EventHandler<E, M, R>) == null) {
            "Handler for $eventCls was already added."
        }
    }

    internal fun <Rq : R> addOnRequest(
        onRequest: RequestHandler<E, Rq>,
        requestCls: Class<Rq>
    ) {
        @Suppress("UNCHECKED_CAST")
        check(requestHandlers.put(requestCls, onRequest as RequestHandler<E, R>) == null) {
            "Handler for $requestCls was already added."
        }
    }
}

private class EmpressFromBuilder<E : Any, M : Any, R : Any>(
    private val id: String,
    private val initializers: Collection<Initializer<M>>,
    private val eventHandlers: Map<Class<out E>, EventHandler<E, M, R>>,
    private val requestHandlers: Map<Class<out R>, RequestHandler<E, R>>
) : Empress<E, M, R> {

    override fun id(): String {
        return id
    }

    override fun initialize(): Collection<M> {
        return initializers.map { it.invoke(InitializerContext) }
    }

    override fun onEvent(
        event: E,
        models: Models<M>,
        requests: RequestCommander<R>
    ): Collection<M> {
        val eventHandlerContext = EventHandlerContext(event, models, requests)
        return eventHandlers.getValue(event::class.java).invoke(eventHandlerContext)
    }

    override suspend fun onRequest(request: R): E {
        val requestHandlerContext = RequestHandlerContext(request)
        return requestHandlers.getValue(request::class.java).invoke(requestHandlerContext)
    }
}

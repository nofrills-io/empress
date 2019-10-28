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
import io.nofrills.empress.builder.internal.EmpressBuilderData
import io.nofrills.empress.builder.internal.EmpressFromBuilder
import io.nofrills.empress.builder.internal.MutableEmpressFromBuilder

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

/** Context for model initializers. */
@EmpressDslMarker
object InitializerContext

/** Handles an event.
 * Event handler should return a collection of models that have changed.
 * If nothing has changed, it should return an empty collection.
 */
typealias EventHandler<E, M, R> = EventHandlerContext<E, M, R>.() -> Collection<M>

/** Handles an event.
 * Intended for mutable models.
 */
typealias MutableEventHandler<E, M, R> = EventHandlerContext<E, M, R>.() -> Unit

/** Handles (executes) a request. */
typealias RequestHandler<E, R> = suspend RequestHandlerContext<R>.() -> E

/** Creates an initial model value. */
typealias Initializer<M> = InitializerContext.() -> M

/** Allows to build an [Empress] instance. */
@EmpressDslMarker
abstract class RulerBuilder<E : Any, M : Any, R : Any, RL : Ruler<E, M, R>> internal constructor() {
    internal val builderData = EmpressBuilderData<E, M, R>()

    /** Defines an initializer for a [Md] model. */
    inline fun <reified Md : M> initializer(noinline body: Initializer<Md>) {
        initializer(body, Md::class.java)
    }

    /** @see initializer */
    fun <Md : M> initializer(body: Initializer<Md>, modelClass: Class<Md>) {
        builderData.addInitializer(body, modelClass)
    }

    /** Defines request handler for a [R]. */
    inline fun <reified Rq : R> onRequest(noinline body: RequestHandler<E, Rq>) {
        onRequest(Rq::class.java, body)
    }

    /** @see onRequest */
    fun <Rq : R> onRequest(requestClass: Class<Rq>, body: RequestHandler<E, Rq>) {
        builderData.addOnRequest(body, requestClass)
    }

    internal abstract fun build(): RL
}

/** Allows to build an [Empress] instance. */
@EmpressDslMarker
class EmpressBuilder<E : Any, M : Any, R : Any> internal constructor() :
    RulerBuilder<E, M, R, Empress<E, M, R>>() {

    /** Defines event handler for an [E]. */
    inline fun <reified Ev : E> onEvent(noinline body: EventHandler<Ev, M, R>) {
        onEvent(Ev::class.java, body)
    }

    /** @see onEvent */
    fun <Ev : E> onEvent(eventClass: Class<Ev>, body: EventHandler<Ev, M, R>) {
        builderData.addOnEvent(body, eventClass)
    }

    override fun build(): Empress<E, M, R> {
        return EmpressFromBuilder(
            builderData.initializers.values,
            builderData.eventHandlers,
            builderData.requestHandlers
        )
    }
}

/** Allows to build an [Empress] instance. */
@EmpressDslMarker
class MutableEmpressBuilder<E : Any, M : Any, R : Any> internal constructor() :
    RulerBuilder<E, M, R, MutableEmpress<E, M, R>>() {

    /** Defines event handler for an [E]. */
    inline fun <reified Ev : E> onEvent(noinline body: MutableEventHandler<Ev, M, R>) {
        onEvent(Ev::class.java, body)
    }

    /** @see onEvent */
    fun <Ev : E> onEvent(eventClass: Class<Ev>, body: MutableEventHandler<Ev, M, R>) {
        builderData.addOnMutableEvent(body, eventClass)
    }

    override fun build(): MutableEmpress<E, M, R> {
        return MutableEmpressFromBuilder(
            builderData.initializers.values,
            builderData.mutableEventHandlers,
            builderData.requestHandlers
        )
    }
}

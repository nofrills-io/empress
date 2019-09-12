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

package io.nofrills.empress.builder.internal

import io.nofrills.empress.builder.EventHandler
import io.nofrills.empress.builder.Initializer
import io.nofrills.empress.builder.MutableEventHandler
import io.nofrills.empress.builder.RequestHandler

internal class EmpressBuilderData<E : Any, M : Any, R : Any> {
    internal val initializers = mutableMapOf<Class<out M>, Initializer<M>>()
    internal val eventHandlers = mutableMapOf<Class<out E>, EventHandler<E, M, R>>()
    internal val mutableEventHandlers = mutableMapOf<Class<out E>, MutableEventHandler<E, M, R>>()
    internal val requestHandlers = mutableMapOf<Class<out R>, RequestHandler<E, R>>()

    internal fun <Md : M> addInitializer(initializer: Initializer<Md>, modelClass: Class<Md>) {
        check(initializers.put(modelClass, initializer) == null) {
            "Initializer for $modelClass was already added."
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

    internal fun <Ev : E> addOnMutableEvent(
        onEvent: MutableEventHandler<Ev, M, R>,
        eventCls: Class<Ev>
    ) {
        @Suppress("UNCHECKED_CAST")
        check(mutableEventHandlers.put(eventCls, onEvent as MutableEventHandler<E, M, R>) == null) {
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

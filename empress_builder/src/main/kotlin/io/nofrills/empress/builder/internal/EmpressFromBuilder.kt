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

import io.nofrills.empress.Empress
import io.nofrills.empress.Models
import io.nofrills.empress.RequestCommander
import io.nofrills.empress.builder.*

internal class EmpressFromBuilder<E : Any, M : Any, R : Any>(
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
        val eventHandlerContext =
            EventHandlerContext(event, models, requests)
        return eventHandlers.getValue(event::class.java).invoke(eventHandlerContext)
    }

    override suspend fun onRequest(request: R): E {
        val requestHandlerContext = RequestHandlerContext(request)
        return requestHandlers.getValue(request::class.java).invoke(requestHandlerContext)
    }
}

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

package io.nofrills.empress.backend

import io.nofrills.empress.Models
import io.nofrills.empress.MutableEmpress
import io.nofrills.empress.MutableEmpressApi
import kotlinx.coroutines.CoroutineScope

/** Runs and manages an [MutableEmpress] instance.
 * @param mutableEmpress MutableEmpress instance that we want to run.
 * @param eventHandlerScope A coroutine scope where events will be processed.
 * @param requestHandlerScope A coroutine scope where requests will be processed.
 * @param storedModels Models that were previously stored, which will be used instead of the ones initialized in [initialize][io.nofrills.empress.ModelInitializer.initialize] function.
 */
class MutableEmpressBackend<E : Any, M : Any, R : Any> constructor(
    private val mutableEmpress: MutableEmpress<E, M, R>,
    eventHandlerScope: CoroutineScope,
    requestHandlerScope: CoroutineScope,
    storedModels: Collection<M>?
) : RulerBackend<E, M, R>(mutableEmpress, eventHandlerScope, requestHandlerScope, storedModels),
    MutableEmpressApi<E, M> {
    override suspend fun processEvent(event: E) {
        mutableEmpress.onEvent(event, models, requestCommander)
    }

    override suspend fun modelSnapshot(): Models<M> {
        return models()
    }
}

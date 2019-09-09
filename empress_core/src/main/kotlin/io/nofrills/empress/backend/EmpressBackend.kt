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

import io.nofrills.empress.Empress
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.Models
import io.nofrills.empress.Update
import io.nofrills.empress.internal.AtomicItem
import io.nofrills.empress.internal.ModelsImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/** Runs and manages an [Empress] instance.
 * @param empress Empress instance that we want to run.
 * @param eventHandlerScope A coroutine scope where events will be processed.
 * @param requestHandlerScope A coroutine scope where requests will be processed.
 * @param storedModels Models that were previously stored, which will be used instead of the ones initialized in [initialize][io.nofrills.empress.ModelInitializer.initialize] function.
 */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
class EmpressBackend<E : Any, M : Any, R : Any> constructor(
    private val empress: Empress<E, M, R>,
    eventHandlerScope: CoroutineScope,
    requestHandlerScope: CoroutineScope,
    storedModels: Collection<M>? = null
) : RulerBackend<E, M, R>(empress, eventHandlerScope, requestHandlerScope), EmpressApi<E, M> {

    private val modelsMap = AtomicItem(makeModelMap(storedModels ?: emptyList(), empress))

    private val updates = BroadcastChannel<Update<E, M>>(UPDATES_CHANNEL_CAPACITY)

    private val updatesFlow: Flow<Update<E, M>> = updates.asFlow()

    override suspend fun models(): Models<M> {
        return ModelsImpl(modelsMap.get())
    }

    override fun updates(): Flow<Update<E, M>> {
        return updatesFlow
    }

    override suspend fun processEvent(event: E) {
        lateinit var updated: Collection<M>
        val map = modelsMap.update {
            updated = empress.onEvent(
                event,
                ModelsImpl(it), requestCommander
            )
            it.toMutableMap().apply {
                putAll(makeModelMap(updated))
            }
        }
        updates.send(UpdateImpl(ModelsImpl(map), event, updated))
    }

    override fun areChannelsClosedForSend(): Boolean {
        return super.areChannelsClosedForSend() && updates.isClosedForSend
    }

    override fun closeChannels() {
        updates.close()
        super.closeChannels()
    }

    override suspend fun modelSnapshot(): Models<M> {
        return models()
    }

    companion object {
        private const val UPDATES_CHANNEL_CAPACITY = 16
    }

    private class UpdateImpl<E : Any, M : Any>(
        override val all: Models<M>,
        override val event: E,
        override val updated: Collection<M>
    ) : Update<E, M>
}

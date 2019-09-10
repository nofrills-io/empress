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

package io.nofrills.empress

import io.nofrills.empress.backend.MutableEmpressBackend
import kotlinx.coroutines.CoroutineScope

internal class MutableEmpressBackendTest :
    RulerBackendTest<MutableEmpressBackend<Event, Model, Request>, MutableEmpress<Event, Model, Request>>() {

    override fun makeRuler(initializeWithDuplicate: Boolean): MutableEmpress<Event, Model, Request> {
        return TestMutableEmpress(initializeWithDuplicate)
    }

    override fun makeBackend(
        ruler: MutableEmpress<Event, Model, Request>,
        eventHandlerScope: CoroutineScope,
        requestHandlerScope: CoroutineScope,
        storedModels: Collection<Model>?
    ): MutableEmpressBackend<Event, Model, Request> {
        return MutableEmpressBackend(
            ruler,
            eventHandlerScope,
            requestHandlerScope,
            storedModels
        )
    }
}
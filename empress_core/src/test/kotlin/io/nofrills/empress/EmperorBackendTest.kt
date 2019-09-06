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

import kotlinx.coroutines.CoroutineScope

internal class EmperorBackendTest :
    RulerBackendTest<EmperorBackend<Event, Model, Request>, Emperor<Event, Model, Request>>() {

    override fun makeRuler(initializeWithDuplicate: Boolean): Emperor<Event, Model, Request> {
        return TestEmperor(initializeWithDuplicate)
    }

    override fun makeBackend(
        ruler: Emperor<Event, Model, Request>,
        eventHandlerScope: CoroutineScope,
        requestHandlerScope: CoroutineScope,
        storedModels: Collection<Model>?
    ): EmperorBackend<Event, Model, Request> {
        return EmperorBackend(ruler, eventHandlerScope, requestHandlerScope, storedModels)
    }
}
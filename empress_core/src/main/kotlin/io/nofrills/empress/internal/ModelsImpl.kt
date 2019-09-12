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

package io.nofrills.empress.internal

import io.nofrills.empress.Models
import kotlin.reflect.KClass

internal data class ModelsImpl<M : Any> constructor(private val modelMap: Map<Class<out M>, M>) :
    Models<M> {
    override fun all(): Collection<M> {
        return modelMap.values.toList()
    }

    override fun <T : M> get(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return modelMap.getValue(modelClass) as T
    }

    override fun <T : M> get(modelClass: KClass<T>): T {
        return get(modelClass.java)
    }
}

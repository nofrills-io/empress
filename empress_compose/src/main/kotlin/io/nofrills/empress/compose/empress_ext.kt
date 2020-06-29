/*
 * Copyright 2020 Mateusz Armatys
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

package io.nofrills.empress.compose

import androidx.compose.State
import androidx.compose.collectAsState
import io.nofrills.empress.base.EmpressApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

// fun <EM : EmpressApi<E, M, S>, E : Any, M : Any, S : Any, T : M> EM.state(modelClass: Class<T>): State<T> {
//     return updates().filter { it.javaClass == modelClass }.map {
//         @Suppress("UNCHECKED_CAST")
//         it as T
//     }.collectAsState(get(modelClass))
// }
//
// inline fun <EM : EmpressApi<E, M, S>, E : Any, M : Any, S : Any, reified T : M> EM.state(): State<T> {
//     return state(T::class.java)
// }

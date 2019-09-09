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

/** Allows to define __mutable__ models, event and request handlers. */
interface Emperor<E : Any, M : Any, R : Any> : Ruler<E, M, R>, MutableEventHandler<E, M, R>

/** Allows to manage an [Emperor] instance. */
interface EmperorApi<E : Any, M : Any> : EventCommander<E>, RulerApi {
    /** Returns current models. */
    fun models(): Models<M>
}

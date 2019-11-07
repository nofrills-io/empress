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

import kotlinx.coroutines.flow.Flow

/** Allows to send events. */
interface EventCommander<in E : Any> {
    /** Sends an [event] for processing. */
    fun post(event: E)
}

/** Allows to listen to processed events. */
interface EventListener<out E : Any> {
    /** Allows to listen for events that have been handled. */
    fun events(): Flow<E>
}

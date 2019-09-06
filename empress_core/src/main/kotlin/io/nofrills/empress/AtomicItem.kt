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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AtomicItem<T : Any>(private var item: T) {
    private val mutex: Mutex = Mutex()

    suspend fun get(): T {
        return mutex.withLock {
            item
        }
    }

    /** Updates the item.
     * @param updater Function which takes current item and returns a new, updated one.
     * @return A new, updated item.
     */
    suspend fun update(updater: suspend (T) -> T): T {
        return mutex.withLock {
            item = updater(item)
            item
        }
    }
}

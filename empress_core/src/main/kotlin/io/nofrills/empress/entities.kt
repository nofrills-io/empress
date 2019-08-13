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

import java.lang.IllegalStateException

/** Represents state of your application.
 * A model consists of a set of [patches][Patch], where a [Patch] is usually
 * defined as a sealed class, and each subclass of [Patch] is related to a single aspect of the state.
 */
data class Model<Patch : Any> internal constructor(
    private val patchMap: Map<Class<out Patch>, Patch>,
    private val updatedPatches: Set<Class<out Patch>>
) {
    companion object {
        /** Constructs a model from a collection of patches.
         * All patches will be marked as updated.
         * @param updatedPatches Collection of patches from which to create the model.
         * @param skipDuplicates If `true`, any duplicates in [updatedPatches] will be skipped;
         *  if `false` and duplicates are detected, an exception will be thrown.
         *  @throws IllegalStateException In case [skipDuplicates] is `false` and [updatedPatches] contains two or more instances of the same class.
         */
        fun <Patch : Any> from(
            updatedPatches: Collection<Patch>,
            skipDuplicates: Boolean = false
        ): Model<Patch> {
            val map = mutableMapOf<Class<out Patch>, Patch>()
            for (p in updatedPatches) {
                val alreadyExists = map.contains(p::class.java)
                if (!alreadyExists) {
                    map[p::class.java] = p
                } else if (!skipDuplicates) {
                    error("Cannot use more than one patch of the same class ($p)")
                }
            }
            return Model(map, updatedPatches.map { it::class.java }.toSet())
        }

        /** Constructs a model from an existing one, but applying a collection of updated patches.
         * Only patches from [updatedPatches] will be marked as updated.
         * @throws IllegalStateException In case [updatedPatches] contains two or more instances of the same class.
         */
        fun <Patch : Any> from(
            model: Model<Patch>,
            updatedPatches: Collection<Patch> = emptyList()
        ): Model<Patch> {
            val updatedPatchesMap = mutableMapOf<Class<out Patch>, Patch>()
            for (patch in updatedPatches) {
                if (updatedPatchesMap.contains(patch::class.java)) {
                    error("Cannot use more than one patch of the same class ($patch)")
                } else {
                    updatedPatchesMap[patch::class.java] = patch
                }
            }

            val patchMap = model.patchMap.toMutableMap().apply { putAll(updatedPatchesMap) }
            return Model(patchMap, updatedPatches.map { it::class.java }.toSet())
        }
    }

    /** Returns all patches (even the ones that were not recently updated). */
    fun all(): Collection<Patch> {
        return patchMap.values
    }

    /** Returns a patch instance. */
    inline fun <reified P : Patch> get(): P {
        return get(P::class.java) as P
    }

    /** Returns a patch instance for the given class. */
    operator fun get(key: Class<out Patch>): Patch {
        return patchMap[key] ?: throw IllegalStateException("Patch ${key.name} was not initialized.")
    }

    /** Returns collection of recently updated patches. */
    fun updated(): Collection<Patch> {
        return patchMap.filter { updatedPatches.contains(it.key) }.values
    }
}

/** Represents a running request. */
data class RequestId constructor(private val id: Int)

/** Represents an update in the [model], that resulted from processing an [event].
 * @param model Updated model
 * @param event Event that triggered the update.
 * @see Model.updated
 */
data class Update<Event, Patch : Any> constructor(val model: Model<Patch>, val event: Event)

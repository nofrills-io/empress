package io.nofrills.empress

data class Effect<Patch : Any, Request> constructor(
    val updatedPatches: Collection<Patch> = emptyList(),
    val request: Request? = null
) {
    constructor(updatedPatch: Patch, request: Request? = null) : this(listOf(updatedPatch), request)
}

sealed class Update<Event, Patch : Any>(val model: Model<Patch>) {
    class Initial<Event, Patch : Any> internal constructor(model: Model<Patch>) :
        Update<Event, Patch>(model)

    class FromEvent<Event, Patch : Any> internal constructor(
        val event: Event,
        model: Model<Patch>
    ) : Update<Event, Patch>(model)
}

class Model<Patch : Any> : Iterable<Patch> {
    private val patchMap: Map<Class<out Patch>, Patch>
    private val updates: Set<Class<out Patch>>

    /** Constructs a model from a collection of patches.
     * @param patches Collection of patches from which to create the model.
     * @param skipDuplicates If `true`, any duplicates in [patches] will be skipped;
     *  if `false` and duplicates are detected, an exception will be thrown.
     */
    internal constructor(patches: Collection<Patch>, skipDuplicates: Boolean = false) {
        val map = mutableMapOf<Class<out Patch>, Patch>()
        for (p in patches) {
            val alreadyExists = map.contains(p::class.java)
            if (!alreadyExists) {
                map[p::class.java] = p
            } else if (!skipDuplicates) {
                error("Cannot use more than one patch of the same class ($p)")
            }
        }
        patchMap = map
        updates = emptySet()
    }

    internal constructor(model: Model<Patch>, updatedPatches: Collection<Patch> = emptyList()) {
        val updatedPatchesMap = mutableMapOf<Class<out Patch>, Patch>()
        for (patch in updatedPatches) {
            if (updatedPatchesMap.contains(patch::class.java)) {
                error("Cannot use more than one patch of the same class ($patch)")
            } else {
                updatedPatchesMap[patch::class.java] = patch
            }
        }

        patchMap = model.patchMap.toMutableMap().apply { putAll(updatedPatchesMap) }
        updates = updatedPatches.map { it::class.java }.toSet()
    }

    fun all(): Collection<Patch> {
        return patchMap.values
    }

    inline fun <reified P : Patch> get(): P {
        return get(P::class.java) as P
    }

    operator fun get(key: Class<out Patch>): Patch {
        return patchMap.getValue(key)
    }

    override fun iterator(): Iterator<Patch> {
        return patchMap.values.iterator()
    }

    fun updated(): Collection<Patch> {
        return patchMap.filter { updates.contains(it.key) }.values
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Model<*>

        if (patchMap != other.patchMap) return false
        if (updates != other.updates) return false

        return true
    }

    override fun hashCode(): Int {
        var result = patchMap.hashCode()
        result = 31 * result + updates.hashCode()
        return result
    }
}

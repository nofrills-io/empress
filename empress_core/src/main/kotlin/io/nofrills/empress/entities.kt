package io.nofrills.empress

/** Represents state of your application.
 * A model consists of a set of [patches][Patch].
 */
class Model<Patch : Any> {
    private val patchMap: Map<Class<out Patch>, Patch>
    private val updatedPatches: Set<Class<out Patch>>

    /** Constructs a model from a collection of patches.
     * All patches will be marked as updated.
     * @param updatedPatches Collection of patches from which to create the model.
     * @param skipDuplicates If `true`, any duplicates in [updatedPatches] will be skipped;
     *  if `false` and duplicates are detected, an exception will be thrown.
     *  @throws IllegalStateException In case [skipDuplicates] is `false` and [updatedPatches] contains two or more instances of the same class.
     */
    constructor(updatedPatches: Collection<Patch>, skipDuplicates: Boolean = false) {
        val map = mutableMapOf<Class<out Patch>, Patch>()
        for (p in updatedPatches) {
            val alreadyExists = map.contains(p::class.java)
            if (!alreadyExists) {
                map[p::class.java] = p
            } else if (!skipDuplicates) {
                error("Cannot use more than one patch of the same class ($p)")
            }
        }
        patchMap = map
        this.updatedPatches = updatedPatches.map { it::class.java }.toSet()
    }

    /** Constructs a model from an existing one, but applying a collection of updated patches.
     * Only patches from [updatedPatches] will be marked as updated.
     * @throws IllegalStateException In case [updatedPatches] contains two or more instances of the same class.
     */
    constructor(model: Model<Patch>, updatedPatches: Collection<Patch> = emptyList()) {
        val updatedPatchesMap = mutableMapOf<Class<out Patch>, Patch>()
        for (patch in updatedPatches) {
            if (updatedPatchesMap.contains(patch::class.java)) {
                error("Cannot use more than one patch of the same class ($patch)")
            } else {
                updatedPatchesMap[patch::class.java] = patch
            }
        }

        patchMap = model.patchMap.toMutableMap().apply { putAll(updatedPatchesMap) }
        this.updatedPatches = updatedPatches.map { it::class.java }.toSet()
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
        return patchMap.getValue(key)
    }

    /** Returns collection of recently updated patches. */
    fun updated(): Collection<Patch> {
        return patchMap.filter { updatedPatches.contains(it.key) }.values
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Model<*>

        if (patchMap != other.patchMap) return false
        if (updatedPatches != other.updatedPatches) return false

        return true
    }

    override fun hashCode(): Int {
        var result = patchMap.hashCode()
        result = 31 * result + updatedPatches.hashCode()
        return result
    }

    override fun toString(): String {
        return "Model(patchMap=$patchMap, updatedPatches=$updatedPatches)"
    }
}

/** Represents a running request. */
data class RequestId constructor(private val id: Int)

/** Represents an update in the [model], that resulted from processing an [event].
 * @see Model.updated
 */
data class Update<Event, Patch : Any> constructor(val model: Model<Patch>, val event: Event)

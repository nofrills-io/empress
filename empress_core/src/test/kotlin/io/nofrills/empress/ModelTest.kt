package io.nofrills.empress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.lang.IllegalStateException

class ModelTest {
    @Test
    fun fromSinglePatch() {
        val patch = Patch.Info("test")
        val patches = listOf(patch)
        val model: Model<Patch> = Model(patches)

        assertEquals(patches, model.all().toList())
        assertEquals(patches, model.updated().toList())

        assertEquals(patch, model[Patch.Info::class.java])
        assertEquals(patch, model.get<Patch.Info>())
    }

    @Test
    fun fromSeveralPatches() {
        val info = Patch.Info("test")
        val network = Patch.Network(true)
        val patches = listOf(info, network)
        val model = Model(patches)

        assertEquals(patches, model.all().toList())
        assertEquals(patches, model.updated().toList())

        assertEquals(network, model[Patch.Network::class.java])
        assertEquals(network, model.get<Patch.Network>())
    }

    @Test(expected = IllegalStateException::class)
    fun withDuplicatePatchClasses() {
        Model(
            listOf(
                Patch.Info("A"),
                Patch.Network(false),
                Patch.Network(true)
            )
        )
    }

    @Test
    fun skipsDuplicates() {
        val model = Model(
            listOf(
                Patch.Info("A"),
                Patch.Network(true),
                Patch.Network(false)
            ),
            skipDuplicates = true
        )
        assertEquals(Patch.Network(true), model.get<Patch.Network>())
    }

    @Test
    fun fromExistingModel() {
        val model: Model<Patch> = Model(listOf(Patch.Info("A")))
        val newModel = Model(model)
        assertNotEquals(model, newModel)
        assertEquals(1, model.updated().size)
        assertEquals(0, newModel.updated().size)
        assertEquals(listOf(Patch.Info("A")), newModel.all().toList())
        assertEquals(Patch.Info("A"), newModel.get<Patch.Info>())
    }

    @Test
    fun withUpdates() {
        val source: Model<Patch> = Model(listOf(Patch.Info("A"), Patch.Network(true)))
        val update = Patch.Info("B")
        val model = Model(source, listOf(update))
        assertEquals(2, model.all().size)
        assertEquals(1, model.updated().size)
        assertEquals(Patch.Network(true), model.get<Patch.Network>())
        assertEquals(Patch.Info("B"), model.get<Patch.Info>())
    }

    @Test(expected = IllegalStateException::class)
    fun withDuplicateUpdates() {
        val source: Model<Patch> = Model(listOf(Patch.Info("A"), Patch.Network(true)))
        val updates = listOf(Patch.Info("B"), Patch.Network(false), Patch.Info("C"))
        Model(source, updates)
    }

    internal sealed class Patch {
        data class Info(val name: String) : Patch()
        data class Network(val isSending: Boolean) : Patch()
    }
}
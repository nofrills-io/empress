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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.lang.IllegalStateException

class ModelTest {
    @Test
    fun fromSinglePatch() {
        val patch = Patch.Info("test")
        val patches = listOf(patch)
        val model: Model<Patch> = Model.from(patches)

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
        val model = Model.from(patches)

        assertEquals(patches, model.all().toList())
        assertEquals(patches, model.updated().toList())

        assertEquals(network, model[Patch.Network::class.java])
        assertEquals(network, model.get<Patch.Network>())
    }

    @Test(expected = IllegalStateException::class)
    fun withDuplicatePatchClasses() {
        Model.from(
            listOf(
                Patch.Info("A"),
                Patch.Network(false),
                Patch.Network(true)
            )
        )
    }

    @Test
    fun skipsDuplicates() {
        val model = Model.from(
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
        val model: Model<Patch> = Model.from(listOf(Patch.Info("A")))
        val newModel = Model.from(model)
        assertNotEquals(model, newModel)
        assertEquals(1, model.updated().size)
        assertEquals(0, newModel.updated().size)
        assertEquals(listOf(Patch.Info("A")), newModel.all().toList())
        assertEquals(Patch.Info("A"), newModel.get<Patch.Info>())
    }

    @Test
    fun withUpdates() {
        val source: Model<Patch> = Model.from(listOf(Patch.Info("A"), Patch.Network(true)))
        val update = Patch.Info("B")
        val model = Model.from(source, listOf(update))
        assertEquals(2, model.all().size)
        assertEquals(1, model.updated().size)
        assertEquals(Patch.Network(true), model.get<Patch.Network>())
        assertEquals(Patch.Info("B"), model.get<Patch.Info>())
    }

    @Test(expected = IllegalStateException::class)
    fun withDuplicateUpdates() {
        val source: Model<Patch> = Model.from(listOf(Patch.Info("A"), Patch.Network(true)))
        val updates = listOf(Patch.Info("B"), Patch.Network(false), Patch.Info("C"))
        Model.from(source, updates)
    }

    internal sealed class Patch {
        data class Info(val name: String) : Patch()
        data class Network(val isSending: Boolean) : Patch()
    }
}

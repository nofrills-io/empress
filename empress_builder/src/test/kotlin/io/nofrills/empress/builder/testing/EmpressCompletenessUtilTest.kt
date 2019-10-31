package io.nofrills.empress.builder.testing

import io.nofrills.empress.builder.Empress
import io.nofrills.empress.builder.testing.exception.ModelInitializerMissing
import io.nofrills.empress.builder.testing.exception.ModelNotSealed
import org.junit.Test

internal class EmpressCompletenessUtilTest {
    @Test(expected = ModelNotSealed::class)
    fun abstractModel() {
        Empress<EventSealedEmpty, ModelAbstract, RequestSealedEmpty>(true) {}
    }

    @Test(expected = ModelNotSealed::class)
    fun openModel() {
        Empress<EventSealedEmpty, ModelOpen, RequestSealedEmpty>(true) {}
    }

    @Test(expected = ModelInitializerMissing::class)
    fun modelClassMissingInitializer() {
        Empress<EventSealedEmpty, ModelClass, RequestSealedEmpty>(true) {}
    }

    @Test(expected = ModelInitializerMissing::class)
    fun modelDataMissingInitializer() {
        Empress<EventSealedEmpty, ModelData, RequestSealedEmpty>(true) {}
    }

    @Test
    fun emptyEmpressSealed() {
        Empress<EventSealedEmpty, ModelSealedEmpty, RequestSealedEmpty>(true) {}
    }

    @Test
    fun modelClassWithInitializer() {
        Empress<EventSealedEmpty, ModelClass, RequestSealedEmpty>(true) {
            initializer { ModelClass() }
        }
    }

    @Test
    fun modelDataWithInitializer() {
        Empress<EventSealedEmpty, ModelData, RequestSealedEmpty>(true) {
            initializer { ModelData(0) }
        }
    }

    @Test(expected = ModelInitializerMissing::class)
    fun modelSealedWithOneMissingInitializer() {
        Empress<EventSealedEmpty, ModelSealed, RequestSealedEmpty>(true) {
            initializer { ModelSealed.A() }
        }
    }

    @Test
    fun modelSealedWithInitializers() {
        Empress<EventSealedEmpty, ModelSealed, RequestSealedEmpty>(true) {
            initializer { ModelSealed.A() }
            initializer { ModelSealed.B(0) }
            initializer { ModelSealed.D.E() }
        }
    }

    @Test(expected = ModelNotSealed::class)
    fun modelSealedWithOpen() {
        Empress<EventSealedEmpty, ModelSealedWithOpen, RequestSealedEmpty>(true) {}
    }

    // TODO tests for events and requests
    // TODO test MutableEmpress

    // Event classes

    class EventClass
    sealed class EventSealedEmpty


    // Model classes

    abstract class ModelAbstract
    data class ModelData(val value: Int)
    class ModelClass
    open class ModelOpen
    sealed class ModelSealedEmpty
    sealed class ModelSealed {
        class A : ModelSealed()
        data class B(val b: Int) : ModelSealed()
        sealed class C : ModelSealed()
        sealed class D : ModelSealed() {
            class E : D()
        }
    }

    sealed class ModelSealedWithOpen {
        sealed class A : ModelSealedWithOpen() {
            open class B : A()
        }
    }

    // Request classes

    class RequestClass
    sealed class RequestSealedEmpty
}
package io.nofrills.empress.builder.testing

import io.nofrills.empress.builder.testing.exception.ClassNotHandled
import io.nofrills.empress.builder.testing.exception.ClassNotSupported
import org.junit.Test
import kotlin.reflect.KClass

internal class EmpressClassHandlerUtilTest {
    @Test(expected = ClassNotSupported::class)
    fun abstractModel() {
        check(ModelAbstract::class)
    }

    @Test(expected = ClassNotSupported::class)
    fun openModel() {
        check(ModelOpen::class)
    }

    @Test(expected = ClassNotHandled::class)
    fun modelClassMissingInitializer() {
        check(ModelClass::class)
    }

    @Test(expected = ClassNotHandled::class)
    fun modelDataMissingInitializer() {
        check(ModelData::class)
    }

    @Test
    fun emptyEmpressSealed() {
        check(ModelSealedEmpty::class)
    }

    @Test
    fun modelClassWithInitializer() {
        check(ModelClass::class, listOf(ModelClass::class.java))
    }

    @Test
    fun modelDataWithInitializer() {
        check(ModelData::class, listOf(ModelData::class.java))
    }

    @Test(expected = ClassNotHandled::class)
    fun modelSealedWithOneMissingInitializer() {
        check(ModelSealed::class, listOf(ModelSealed.A::class.java))
    }

    @Test
    fun modelSealedWithInitializers() {
        check(
            ModelSealed::class,
            listOf(
                ModelSealed.A::class.java,
                ModelSealed.B::class.java,
                ModelSealed.D.E::class.java
            )
        )
    }

    @Test(expected = ClassNotSupported::class)
    fun modelSealedWithOpen() {
        check(ModelSealedWithOpen::class)
    }

    private fun <T : Any> check(
        kClass: KClass<out T>,
        handledClasses: Collection<Class<out T>> = emptyList()
    ) {
        EmpressClassHandlerUtil.checkClassHandled(kClass, handledClasses)
    }

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
}
package io.nofrills.empress.builder.testing

import io.nofrills.empress.builder.testing.exception.ClassNotHandled
import io.nofrills.empress.builder.testing.exception.ClassNotSupported
import kotlin.reflect.KClass

internal object EmpressClassHandlerUtil {
    /** Given a final or sealed class [kClass], this method checks if all _final_ subclasses are present in [handledClasses].
     * @throws ClassNotSupported If [kClass] or one of its subclasses is not a sealed or final class.
     * @throws ClassNotHandled If at least one of the final subclasses of [kClass] is not present in [handledClasses].
     */
    fun <M : Any> checkClassHandled(
        kClass: KClass<out M>,
        handledClasses: Collection<Class<out M>>
    ) {
        when {
            kClass.isFinal -> {
                if (kClass.java !in handledClasses) {
                    throw ClassNotHandled(kClass)
                }
            }
            kClass.isSealed -> {
                for (modelSubclass in kClass.sealedSubclasses) {
                    checkClassHandled(modelSubclass, handledClasses)
                }
            }
            else -> throw ClassNotSupported(kClass)
        }
    }
}

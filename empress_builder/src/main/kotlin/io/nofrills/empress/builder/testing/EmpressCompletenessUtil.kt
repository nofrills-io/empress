package io.nofrills.empress.builder.testing

import io.nofrills.empress.builder.EmpressBuilder
import io.nofrills.empress.builder.MutableEmpressBuilder
import io.nofrills.empress.builder.RulerBuilder
import io.nofrills.empress.builder.testing.exception.ModelInitializerMissing
import io.nofrills.empress.builder.testing.exception.ModelNotSealed
import kotlin.reflect.KClass

internal object EmpressCompletenessUtil {
    fun <E : Any, M : Any, R : Any, B : EmpressBuilder<E, M, R>> check(
        builder: B,
        eventClass: KClass<E>,
        modelClass: KClass<M>,
        requestClass: KClass<R>
    ) {
        checkEventHandlers(eventClass, builder.builderData.eventHandlers.keys)
        checkRuler(builder, modelClass, requestClass)
    }

    fun <E : Any, M : Any, R : Any, B : MutableEmpressBuilder<E, M, R>> check(
        builder: B,
        eventClass: KClass<E>,
        modelClass: KClass<M>,
        requestClass: KClass<R>
    ) {
        checkEventHandlers(eventClass, builder.builderData.mutableEventHandlers.keys)
        checkRuler(builder, modelClass, requestClass)
    }

    private fun <M : Any, R : Any, B : RulerBuilder<*, M, R, *>> checkRuler(
        builder: B,
        modelClass: KClass<M>,
        requestClass: KClass<R>
    ) {
        checkInitializers(modelClass, builder.builderData.initializers.keys)
        checkRequestHandlers(requestClass, builder.builderData.requestHandlers.keys)
    }

    private fun <R : Any> checkEventHandlers(
        eventClass: KClass<out R>,
        handledEventClasses: Collection<Class<out R>>
    ) {
        // TODO
        handledEventClasses.size
        eventClass.isOpen
    }

    private fun <R : Any> checkRequestHandlers(
        requestClass: KClass<out R>,
        handledRequestClasses: Collection<Class<out R>>
    ) {
        // TODO
        handledRequestClasses.size
        requestClass.isOpen
    }

    private fun <M : Any> checkInitializers(
        modelClass: KClass<out M>,
        initializedModelClasses: Collection<Class<out M>>
    ) {
        when {
            modelClass.isFinal -> verifyFinalModel(modelClass, initializedModelClasses)
            modelClass.isSealed -> {
                for (modelSubclass in modelClass.sealedSubclasses) {
                    checkInitializers(modelSubclass, initializedModelClasses)
                }
            }
            else -> throw ModelNotSealed(modelClass)
        }
    }

    private fun <M : Any> verifyFinalModel(
        modelClass: KClass<out M>,
        initializedModelClasses: Collection<Class<out M>>
    ) {
        if (modelClass.java !in initializedModelClasses) {
            throw ModelInitializerMissing(modelClass)
        }
    }
}

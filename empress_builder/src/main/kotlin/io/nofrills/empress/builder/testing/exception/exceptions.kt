package io.nofrills.empress.builder.testing.exception

import kotlin.reflect.KClass

internal class ClassNotHandled(modelClass: KClass<*>) :
    Throwable("Class $modelClass is not handled.")

internal class ClassNotSupported(modelClass: KClass<*>) :
    Throwable("Class $modelClass is not a sealed or final class.")

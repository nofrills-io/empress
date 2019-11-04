package io.nofrills.empress.builder.testing.exception

import kotlin.reflect.KClass

class ClassNotHandled(modelClass: KClass<*>) :
    Throwable("Class $modelClass is not handled.")

class ClassNotSupported(modelClass: KClass<*>) :
    Throwable("Class $modelClass is not a sealed or final class.")

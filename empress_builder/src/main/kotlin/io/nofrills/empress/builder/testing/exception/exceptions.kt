package io.nofrills.empress.builder.testing.exception

import kotlin.reflect.KClass

class ModelInitializerMissing(modelClass: KClass<*>) :
    Throwable("Could not find initializer for $modelClass")

class ModelNotSealed(modelClass: KClass<*>) :
    Throwable("Class $modelClass is not a sealed class (and only sealed classes are supported).")

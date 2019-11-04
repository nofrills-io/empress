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

package io.nofrills.empress.builder

import androidx.annotation.RestrictTo
import io.nofrills.empress.Empress
import io.nofrills.empress.MutableEmpress
import kotlin.reflect.KClass

/** Builds an [Empress] instance.
 * @param checkCompleteness Checks the completeness of the [Empress] definition.
 *  Use only in tests or debug builds. Requires `kotlin-reflect` library.
 * @param body Specification for the new [Empress] instance.
 * @return New [Empress].
 */
@Suppress("FunctionName")
inline fun <reified E : Any, reified M : Any, reified R : Any> Empress(
    checkCompleteness: Boolean = false,
    noinline body: EmpressBuilder<E, M, R>.() -> Unit
): Empress<E, M, R> {
    return Empress(checkCompleteness, E::class, M::class, R::class, body)
}

@RestrictTo(RestrictTo.Scope.LIBRARY)
@Suppress("FunctionName")
fun <E : Any, M : Any, R : Any> Empress(
    checkCompleteness: Boolean = false,
    eventClass: KClass<E>,
    modelClass: KClass<M>,
    requestClass: KClass<R>,
    body: EmpressBuilder<E, M, R>.() -> Unit
): Empress<E, M, R> {
    val builder = EmpressBuilder<E, M, R>()
    body(builder)
    return builder.build(checkCompleteness, eventClass, modelClass, requestClass)
}

/** Builds an [MutableEmpress] instance.
 * @param checkCompleteness Checks the completeness of the [MutableEmpress] definition.
 *  Use only in tests or debug builds. Requires `kotlin-reflect` library.
 * @param body Specification for the new [MutableEmpress] instance.
 * @return New [MutableEmpress].
 */
@Suppress("FunctionName")
inline fun <reified E : Any, reified M : Any, reified R : Any> MutableEmpress(
    checkCompleteness: Boolean = false,
    noinline body: MutableEmpressBuilder<E, M, R>.() -> Unit
): MutableEmpress<E, M, R> {
    return MutableEmpress(checkCompleteness, E::class, M::class, R::class, body)
}

@RestrictTo(RestrictTo.Scope.LIBRARY)
@Suppress("FunctionName")
fun <E : Any, M : Any, R : Any> MutableEmpress(
    checkCompleteness: Boolean = false,
    eventClass: KClass<E>,
    modelClass: KClass<M>,
    requestClass: KClass<R>,
    body: MutableEmpressBuilder<E, M, R>.() -> Unit
): MutableEmpress<E, M, R> {
    val builder = MutableEmpressBuilder<E, M, R>()
    body(builder)
    return builder.build(checkCompleteness, eventClass, modelClass, requestClass)
}

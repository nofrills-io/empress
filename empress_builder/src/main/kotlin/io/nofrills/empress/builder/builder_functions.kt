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

import io.nofrills.empress.Empress
import io.nofrills.empress.MutableEmpress

/** Builds an [Empress] instance.
 * @param body Specification for the new [Empress] instance.
 * @return New [Empress].
 */
@Suppress("FunctionName")
fun <E : Any, M : Any, R : Any> Empress(
    body: EmpressBuilder<E, M, R>.() -> Unit
): Empress<E, M, R> {
    val builder = EmpressBuilder<E, M, R>()
    body(builder)
    return builder.build()
}

/** Builds an [MutableEmpress] instance.
 * @param body Specification for the new [MutableEmpress] instance.
 * @return New [MutableEmpress].
 */
@Suppress("FunctionName")
fun <E : Any, M : Any, R : Any> MutableEmpress(
    body: MutableEmpressBuilder<E, M, R>.() -> Unit
): MutableEmpress<E, M, R> {
    val builder = MutableEmpressBuilder<E, M, R>()
    body(builder)
    return builder.build()
}

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

package io.nofrills.empress.annotation

import kotlin.reflect.KClass

/** Marks a class that can be used to generate an `Empress` instance. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class EmpressModule(
    val id: String,
    val events: KClass<*>,
    val patches: KClass<*>,
    val requests: KClass<*>
)

/** Marks a function that returns initial value for a `Patch`
 * The function cannot receive any parameters, and should return concrete subclass of a `Patch`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Initializer

/** Marks a function that handles an `Event`.
 * The function can accept the following parameters:
 * - `Event` (abstract or concrete)
 * - `Model<Patch>`
 * - concrete `Patch`
 * - `Requests<Event, Request>`
 * Its return type should be `Collection<Patch>`, or `Patch?` (nullable).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class OnEvent(val event: KClass<*>)

/** Marks a function that handles a `Request`.
 * The function can accept the following parameters:
 * - `Request` (abstract or concrete)
 * Its return type should be always `Event`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class OnRequest(val request: KClass<*>)

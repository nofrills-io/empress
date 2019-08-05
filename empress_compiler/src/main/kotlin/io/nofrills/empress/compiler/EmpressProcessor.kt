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

package io.nofrills.empress.compiler

import com.google.auto.service.AutoService
import io.nofrills.empress.annotation.EmpressModule
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind.*
import kotlin.reflect.KClass

@AutoService(EmpressProcessor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes(EmpressProcessor.EMPRESS_MODULE_ANNOTATION_TYPE)
@SupportedOptions(EmpressProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class EmpressProcessor : AbstractProcessor() {
    override fun process(
        annotations: MutableSet<out TypeElement>?,
        roundEnv: RoundEnvironment
    ): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(EmpressModule::class.java)
        if (annotatedElements.isEmpty()) return false

        val kaptKotlinGeneratedDir =
            processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
                processingEnv.messager.printMessage(
                    ERROR,
                    "Can't find the target directory for generated Kotlin files."
                )
                return false
            }

        for (element in annotatedElements) {
            if (element !is TypeElement) {
                processingEnv.messager.printMessage(
                    ERROR,
                    "Expected a TypeElement but got ${element.javaClass.name}"
                )
                return false
            }

            try {
                processEmpressModule(element)
            } catch (e: UnsupportedClass) {
                processingEnv.messager.printMessage(
                    ERROR,
                    "Class ${e.kClass.qualifiedName} should be a sealed class."
                )
                return false
            }
        }

        return true
    }

    private fun processEmpressModule(element: TypeElement) {
        val empressModule = element.getAnnotation(EmpressModule::class.java)

        checkIsSealed(empressModule.events)
        checkIsSealed(empressModule.patches)
        checkIsSealed(empressModule.requests)
    }

    private fun checkIsSealed(kClass: KClass<*>) {
        if (!kClass.isSealed) {
            throw UnsupportedClass(kClass)
        }
    }

    internal class UnsupportedClass(val kClass: KClass<*>) : Throwable()

    companion object {
        const val EMPRESS_MODULE_ANNOTATION_TYPE = "io.nofrills.empress.annotation.EmpressModule"
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}
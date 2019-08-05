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
import com.squareup.kotlinpoet.asTypeName
import io.nofrills.empress.annotation.EmpressModule
import io.nofrills.empress.annotation.Initializer
import io.nofrills.empress.annotation.OnEvent
import io.nofrills.empress.annotation.OnRequest
import java.io.File
import java.lang.RuntimeException
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic.Kind.*
import kotlin.reflect.KClass

@AutoService(Processor::class)
class EmpressProcessor : AbstractProcessor() {
    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(EmpressModule::class.java.canonicalName)
    }

    override fun getSupportedOptions(): MutableSet<String> {
        return mutableSetOf(KAPT_KOTLIN_GENERATED_OPTION_NAME)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(
        annotations: MutableSet<out TypeElement>?,
        roundEnv: RoundEnvironment
    ): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(EmpressModule::class.java)
        if (annotatedElements.isEmpty()) return false

        val kaptKotlinGeneratedDir =
            processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
                logError("Can't find the target directory for generated Kotlin files.")
                return false
            }
        val outDir = File(kaptKotlinGeneratedDir)

        for (element in annotatedElements) {
            try {
                processEmpressModule(element, outDir)
            } catch (e: MoreThanOneEventHandler) {
                logError("There is more than one event handler for ${e.eventKClass}.")
                return false
            } catch (e: MoreThanOneInitializer) {
                logError("There is more than one initializer for ${e.returnType.asTypeName()} patch.")
                return false
            } catch (e: MoreThanOneRequestHandler) {
                logError("There is more than one request handler for ${e.requestKClass}.")
                return false
            } catch (e: UnexpectedEmpressAnnotation) {
                logError("Element ${e.element.simpleName} should be annotated with a single empress annotation.")
                return false
            } catch (e: UnsupportedClass) {
                logError("Class ${e.kClass.qualifiedName} should be a sealed class.")
                return false
            } catch (e: UnexpectedElement) {
                logError("Expected a TypeElement but got ${e.element.javaClass.name}.")
                return false
            }
        }

        return true
    }

    private fun processEmpressModule(element: Element, @Suppress("UNUSED_PARAMETER") outDir: File) {
        val empressModuleElement = element as? TypeElement ?: throw UnexpectedElement(element)
        val empressModule = element.getAnnotation(EmpressModule::class.java)

        checkIsSealed(empressModule.events)
        checkIsSealed(empressModule.patches)
        checkIsSealed(empressModule.requests)

        // TODO check if all patches are initialized
        // TODO check if there are handlers for every possible event and request

        processEnclosedElements(empressModule, empressModuleElement)
    }

    private fun processEnclosedElements(
        @Suppress("UNUSED_PARAMETER") empressModule: EmpressModule,
        empressModuleElement: TypeElement
    ) {
        val handledPatches = mutableSetOf<TypeMirror>()
        val handledEvents = mutableSetOf<KClass<*>>()
        val handledRequests = mutableSetOf<KClass<*>>()

        for (enclosedElement in empressModuleElement.enclosedElements) {
            val executableElement = enclosedElement as? ExecutableElement ?: continue
            var wasAnnotated = false // true if annotated by Initializer, OnEvent or OnRequest

            enclosedElement.getAnnotation(Initializer::class.java)?.let {
                checkAlreadyAnnotated(enclosedElement, wasAnnotated)
                wasAnnotated = true

                // TODO check if executableElement.returnType (patch returned by initializer) is subclass of empressModule.patches

                if (!handledPatches.add(executableElement.returnType)) {
                    throw MoreThanOneInitializer(executableElement.returnType)
                }

                logNote("Initializer for ${executableElement.returnType}")
            }

            enclosedElement.getAnnotation(OnEvent::class.java)?.let {
                checkAlreadyAnnotated(enclosedElement, wasAnnotated)
                wasAnnotated = true

                // TODO check if it.event is subclass of empressModule.events

                if (!handledEvents.add(it.event)) {
                    throw MoreThanOneEventHandler(it.event)
                }

                logNote("Event handler for ${it}")
            }

            enclosedElement.getAnnotation(OnRequest::class.java)?.let {
                checkAlreadyAnnotated(enclosedElement, wasAnnotated)
                wasAnnotated = true

                // TODO check if it.request is subclass of empressModule.requests

                if (!handledRequests.add(it.request)) {
                    throw MoreThanOneRequestHandler(it.request)
                }

                logNote("Request handler for ${it}")
            }
        }
    }

    private fun checkAlreadyAnnotated(element: Element, isHandled: Boolean) {
        if (isHandled) {
            throw UnexpectedEmpressAnnotation(element)
        }
    }

    private fun checkIsSealed(kClass: KClass<*>) {
        if (!kClass.isSealed) {
            throw UnsupportedClass(kClass)
        }
    }

    private fun logNote(msg: String) {
        processingEnv.messager.printMessage(WARNING, msg)
    }

    private fun logError(msg: String) {
        processingEnv.messager.printMessage(ERROR, msg)
    }

    internal class MoreThanOneEventHandler(val eventKClass: KClass<*>) : Throwable()
    internal class MoreThanOneInitializer(val returnType: TypeMirror) : Throwable()
    internal class MoreThanOneRequestHandler(val requestKClass: KClass<*>) : Throwable()
    internal class UnexpectedEmpressAnnotation(val element: Element) : Throwable()
    internal class UnsupportedClass(val kClass: KClass<*>) : Throwable()
    internal class UnexpectedElement(val element: Element) : Throwable()

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}
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
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.nofrills.empress.Empress
import io.nofrills.empress.Model
import io.nofrills.empress.Requests
import io.nofrills.empress.annotation.EmpressModule
import io.nofrills.empress.annotation.Initializer
import io.nofrills.empress.annotation.OnEvent
import io.nofrills.empress.annotation.OnRequest
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.MirroredTypeException
import javax.lang.model.type.MirroredTypesException
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.WARNING
import kotlin.coroutines.Continuation
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
            } catch (e: InvalidParameterCount) {
                logError("Method ${e.executableElement.simpleName} has wrong number of parameters (expected ${e.expectedParamCount} parameters).")
                return false
            } catch (e: InvalidParameterType) {
                logError("Parameter '${e.parameter}' in ${e.executableElement} has unsupported type.")
                return false
            } catch (e: MethodNotPublic) {
                logError("Method ${e.executableElement.simpleName} should be public.")
                return false
            } catch (e: MoreThanOneEventHandler) {
                logError("There is more than one event handler for ${e.eventType.asTypeName()}.")
                return false
            } catch (e: MoreThanOneInitializer) {
                logError("There is more than one initializer for ${e.returnType.asTypeName()} patch.")
                return false
            } catch (e: MoreThanOneRequestHandler) {
                logError("There is more than one request handler for ${e.requestType.asTypeName()}.")
                return false
            } catch (e: NoParametersAccepted) {
                logError("Method ${e.executableElement.asType().asTypeName()} must not contain any parameters.")
                return false
            } catch (e: NotSubclass) {
                logError("Type ${e.childType.asTypeName()} is not a proper subclass of ${e.parentType.asTypeName()} (in ${e.executableElement.enclosingElement.simpleName}#${e.executableElement}).")
                return false
            } catch (e: UnexpectedEmpressAnnotation) {
                logError("Element ${e.element.simpleName} should be annotated with a single empress annotation.")
                return false
            } catch (e: UnexpectedElement) {
                logError("Expected a TypeElement but got ${e.element.javaClass.name}.")
                return false
            }
        }

        return true
    }

    private fun processEmpressModule(element: Element, outDir: File) {
        val empressModuleElement = element as? TypeElement ?: throw UnexpectedElement(element)
        val empressModule = element.getAnnotation(EmpressModule::class.java)

        // TODO check if all patches are initialized
        // TODO check if there are handlers for every possible event and request

        val eventRoot = getTypeMirrorFromAnnotationValue { empressModule.events }
        val patchRoot = getTypeMirrorFromAnnotationValue { empressModule.patches }
        val requestRoot = getTypeMirrorFromAnnotationValue { empressModule.requests }

        val initialPatches = getInitialPatches(empressModuleElement, patchRoot)
        val eventHandlers =
            getEventHandlers(empressModuleElement, eventRoot, patchRoot, requestRoot)
        val requestHandlers = getRequestHandlers(empressModuleElement, eventRoot, requestRoot)

        val empressInterface = Empress::class.asClassName().parameterizedBy(
            eventRoot.asTypeName(),
            patchRoot.asTypeName(),
            requestRoot.asTypeName()
        )

        val empressModuleProperty = PropertySpec
            .builder(
                EMPRESS_MODULE_FIELD,
                empressModuleElement.asType().asTypeName(),
                KModifier.PRIVATE
            )
            .initializer(EMPRESS_MODULE_FIELD)
            .build()

        val primaryConstructor = FunSpec
            .constructorBuilder()
            .addParameter(EMPRESS_MODULE_FIELD, empressModuleElement.asType().asTypeName())
            .build()

        val empressImplType = TypeSpec.classBuilder("Empress_${element.asClassName().simpleName}")
            .addSuperinterface(empressInterface)
            .primaryConstructor(primaryConstructor)
            .addProperty(empressModuleProperty)
            .addFunction(buildPatchInitializer(initialPatches, patchRoot))
            .addFunction(buildEventHandler(eventRoot, patchRoot, requestRoot, eventHandlers))
            .addFunction(buildRequestHandler(eventRoot, requestRoot, requestHandlers))
            .build()

        val file = FileSpec
            .builder(element.asClassName().packageName, element.simpleName.toString())
            .addType(empressImplType)
            .build()

        logWarning(file.toString())
    }

    private fun buildPatchInitializer(
        initialPatches: Map<TypeMirror, ExecutableElement>,
        patchRoot: TypeMirror
    ): FunSpec {
        val collection = ClassName("kotlin.collections", "Collection")
        val initializerMethodCalls = initialPatches
            .map { (_, e) ->
                "$EMPRESS_MODULE_FIELD.${e.simpleName}()"
            }.joinToString(", ")
        return FunSpec
            .builder("initializer")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(collection.parameterizedBy(patchRoot.asTypeName()))
            .addCode("return listOf(%L)", initializerMethodCalls)
            .build()
    }

    private fun buildEventHandler(
        eventRoot: TypeMirror,
        patchRoot: TypeMirror,
        requestRoot: TypeMirror,
        eventHandlers: Map<TypeMirror, ExecutableElement>
    ): FunSpec {
        val collection = ClassName("kotlin.collections", "Collection")
        val eventParamName = "event"
        val eventCases = eventHandlers
            .map { (t, e) -> "is ${t.asTypeName()} -> $EMPRESS_MODULE_FIELD.${e.simpleName}()" } // TODO pass params; TODO return type (either list or a single (nullable?) patch)
            .joinToString("\n")
        return FunSpec
            .builder("onEvent")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter(eventParamName, eventRoot.asTypeName())
            .addParameter(
                "model",
                Model::class.asClassName().parameterizedBy(patchRoot.asTypeName())
            )
            .addParameter(
                "requests",
                Requests::class.asClassName().parameterizedBy(
                    eventRoot.asTypeName(),
                    requestRoot.asTypeName()
                )
            )
            .returns(collection.parameterizedBy(patchRoot.asTypeName()))
            .addCode("return when(%L) {\n %L \n}", eventParamName, eventCases)
            .build()
    }

    private fun buildRequestHandler(
        eventRoot: TypeMirror,
        requestRoot: TypeMirror,
        requestHandlers: Map<TypeMirror, ExecutableElement>
    ): FunSpec {
        val requestParamName = "request"
        val whenCases = requestHandlers
            .map { (t, e) -> "is ${t.asTypeName()} -> $EMPRESS_MODULE_FIELD.${e.simpleName}($requestParamName)" }
            .joinToString("\n")
        return FunSpec
            .builder("onRequest")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter(requestParamName, requestRoot.asTypeName())
            .returns(eventRoot.asTypeName())
            .addCode("return when(%L) {\n %L \n}", requestParamName, whenCases)
            .build()
    }

    private fun getInitialPatches(
        empressModuleElement: TypeElement,
        patchRoot: TypeMirror
    ): Map<TypeMirror, ExecutableElement> {
        val mutableMap = mutableMapOf<TypeMirror, ExecutableElement>()

        empressModuleElement.enclosedElements
            .asSequence()
            .filter { it is ExecutableElement && it.getAnnotation(Initializer::class.javaObjectType) != null }
            .map { it as ExecutableElement }
            .forEach { executableElement ->
                if (!executableElement.modifiers.contains(Modifier.PUBLIC)) {
                    throw MethodNotPublic(executableElement)
                }

                if (executableElement.parameters.isNotEmpty()) {
                    throw NoParametersAccepted(executableElement)
                }
                if (!isProperSubtype(executableElement.returnType, patchRoot)) {
                    throw NotSubclass(executableElement, executableElement.returnType, patchRoot)
                }

                if (mutableMap.put(executableElement.returnType, executableElement) != null) {
                    throw MoreThanOneInitializer(executableElement.returnType)
                }
            }

        return mutableMap
    }

    private fun getEventHandlers(
        empressModuleElement: TypeElement,
        eventRoot: TypeMirror,
        patchRoot: TypeMirror,
        requestRoot: TypeMirror
    ): Map<TypeMirror, ExecutableElement> {
        val mutableMap = mutableMapOf<TypeMirror, ExecutableElement>()
        val requestsTypeName = Requests::class.asClassName()
            .parameterizedBy(eventRoot.asTypeName(), requestRoot.asTypeName())

        empressModuleElement.enclosedElements
            .asSequence()
            .filter { it is ExecutableElement && it.getAnnotation(OnEvent::class.javaObjectType) != null }
            .map { Pair(it as ExecutableElement, it.getAnnotation(OnEvent::class.javaObjectType)) }
            .forEach { (executableElement, onEventAnnotation) ->
                if (!executableElement.modifiers.contains(Modifier.PUBLIC)) {
                    throw MethodNotPublic(executableElement)
                }
                getTypeMirrorsFromAnnotationValue { onEventAnnotation.event }
                    .forEach { eventType ->
                        if (!isProperSubtype(eventType, eventRoot)) {
                            // TODO actually this refers to annotation value
                            throw NotSubclass(executableElement, eventType, eventRoot)
                        }

                        for (param in executableElement.parameters) {
                            if (
                                !isSubtype(param.asType(), eventRoot)
                                && !isProperSubtype(param.asType(), patchRoot)
                                && param.asType().asTypeName() != requestsTypeName
                            ) {
                                throw InvalidParameterType(executableElement, param)
                            }
                        }

                        // TODO make sure if there's an argument, it's a patch, or Requests
                        // TODO make sure returns a patch or collection of patches

                        if (mutableMap.put(eventType, executableElement) != null) {
                            throw MoreThanOneEventHandler(eventType)
                        }
                    }
            }

        return mutableMap
    }

    private fun getRequestHandlers(
        empressModuleElement: TypeElement,
        eventRoot: TypeMirror,
        requestRoot: TypeMirror
    ): Map<TypeMirror, ExecutableElement> {
        val mutableMap = mutableMapOf<TypeMirror, ExecutableElement>()

        empressModuleElement.enclosedElements
            .asSequence()
            .filter { it is ExecutableElement && it.getAnnotation(OnRequest::class.javaObjectType) != null }
            .map { Pair(it as ExecutableElement, it.getAnnotation(OnRequest::class.java)) }
            .forEach { (executableElement, onRequestAnnotation) ->
                if (!executableElement.modifiers.contains(Modifier.PUBLIC)) {
                    throw MethodNotPublic(executableElement)
                }

                val requestType = getTypeMirrorFromAnnotationValue { onRequestAnnotation.request }
                if (!isProperSubtype(requestType, requestRoot)) {
                    // TODO this actually should refer to class in annotation, not the executableElement
                    throw NotSubclass(executableElement, requestType, requestRoot)
                }

                val paramsCount = executableElement.parameters.size
                val isSuspendable =
                    executableElement.parameters.lastOrNull()?.asType()?.asTypeName().toString()
                        .startsWith(Continuation::class.asTypeName().toString())
                val maxParamCount = when {
                    isSuspendable -> 2
                    else -> 1
                }
                if (paramsCount > maxParamCount) {
                    throw InvalidParameterCount(
                        executableElement,
                        expectedParamCount = 1
                    )
                } else if (paramsCount == maxParamCount) {
                    if (!isProperSubtype(executableElement.parameters[0].asType(), requestRoot)) {
                        throw NotSubclass(executableElement, requestType, requestRoot)
                    }

                    // return type
                    if (isSuspendable) {
                        val continuationTypeName =
                            executableElement.parameters[1].asType().asTypeName()
                        val expectedContinuation = Continuation::class.asClassName()
                            .parameterizedBy(TypeVariableName("in ${eventRoot.asTypeName()}"))
                        if (continuationTypeName != expectedContinuation) {
                            throw NotSubclass(
                                executableElement,
                                executableElement.parameters[1].asType(), // TODO this reports Continuation<x>, and below we only have an event
                                eventRoot
                            )
                        }
                    }
                }

                if (!isSuspendable) {
                    if (!isSubtype(executableElement.returnType, eventRoot)) {
                        throw NotSubclass(executableElement, executableElement.returnType, eventRoot)
                    }
                }

                if (mutableMap.put(requestType, executableElement) != null) {
                    throw MoreThanOneRequestHandler(requestType)
                }
            }

        return mutableMap
    }

    private fun getTypeMirrorFromAnnotationValue(accessor: () -> KClass<*>): TypeMirror {
        try {
            accessor()
        } catch (e: MirroredTypeException) {
            return e.typeMirror
        }

        throw IllegalStateException("accessor didn't throw MirroredTypeException")
    }

    private fun getTypeMirrorsFromAnnotationValue(accessor: () -> Array<out KClass<*>>): List<TypeMirror> {
        try {
            accessor()
        } catch (e: MirroredTypesException) {
            return e.typeMirrors
        }

        throw IllegalStateException("accessor didn't throw MirroredTypesException")
    }

    private fun isSubtype(childType: TypeMirror, parentType: TypeMirror): Boolean {
        return processingEnv.typeUtils.isSubtype(
            childType,
            parentType
        )
    }

    private fun isProperSubtype(childType: TypeMirror, parentType: TypeMirror): Boolean {
        return processingEnv.typeUtils.isSubtype(
            childType,
            parentType
        ) && childType.asTypeName() != parentType.asTypeName()
    }

    private fun logWarning(msg: String) {
        processingEnv.messager.printMessage(WARNING, msg)
    }

    private fun logError(msg: String) {
        processingEnv.messager.printMessage(ERROR, msg)
    }

    internal class InvalidParameterCount(
        val executableElement: ExecutableElement,
        val expectedParamCount: Int
    ) : Throwable()

    internal class InvalidParameterType(
        val executableElement: ExecutableElement,
        val parameter: VariableElement
    ) : Throwable()

    internal class MethodNotPublic(val executableElement: ExecutableElement) : Throwable()
    internal class MoreThanOneEventHandler(val eventType: TypeMirror) : Throwable()
    internal class MoreThanOneInitializer(val returnType: TypeMirror) : Throwable()
    internal class MoreThanOneRequestHandler(val requestType: TypeMirror) : Throwable()
    internal class NoParametersAccepted(val executableElement: ExecutableElement) : Throwable()
    internal class NotSubclass(
        val executableElement: ExecutableElement,
        val childType: TypeMirror,
        val parentType: TypeMirror
    ) : Throwable()

    internal class UnexpectedEmpressAnnotation(val element: Element) : Throwable()
    internal class UnexpectedElement(val element: Element) : Throwable()

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

        private const val EMPRESS_MODULE_FIELD = "empressModule"
    }
}
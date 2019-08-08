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
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.MirroredTypeException
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.WildcardType
import javax.tools.Diagnostic.Kind.ERROR
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
                processingEnv.messager.printMessage(
                    ERROR,
                    "Can't find the target directory for generated Kotlin files."
                )
                return false
            }
        val outDir = File(kaptKotlinGeneratedDir)

        for (element in annotatedElements) {
            try {
                processEmpressModule(element, outDir)
            } catch (e: EmpressProcessorException) {
                processingEnv.messager.printMessage(ERROR, e.localizedMessage, e.element)
                return false
            }
        }

        return true
    }

    private fun processEmpressModule(element: Element, outDir: File) {
        val empressModuleElement = element as? TypeElement ?: throw UnexpectedElement(element)
        val empressModule = element.getAnnotation(EmpressModule::class.java)

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

        val className = "Empress_${element.asClassName().simpleName}"
        val suppressAnnotation = AnnotationSpec.builder(Suppress::class.java)
            .addMember("\"USELESS_ELVIS\"")
            .addMember("\"UNNECESSARY_SAFE_CALL\"")
            .build()

        val empressImplType = TypeSpec.classBuilder(className)
            .addAnnotation(suppressAnnotation)
            .addSuperinterface(empressInterface)
            .primaryConstructor(primaryConstructor)
            .addProperty(empressModuleProperty)
            .addFunction(buildPatchInitializer(initialPatches, patchRoot))
            .addFunction(buildEventHandler(eventRoot, patchRoot, requestRoot, eventHandlers))
            .addFunction(buildRequestHandler(eventRoot, requestRoot, requestHandlers))
            .build()

        val fileSpec = FileSpec
            .builder(element.asClassName().packageName, className)
            .addType(empressImplType)
            .build()

        val packageDir = File(outDir, fileSpec.packageName.replace(".", File.separator))
        val outFile = File(packageDir, "$className.kt")
        packageDir.mkdirs()
        outFile.writeText(fileSpec.toString())
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
            .builder(INITIALIZER_METHOD_NAME)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(collection.parameterizedBy(patchRoot.asTypeName()))
            .addCode("return listOf(%L)", initializerMethodCalls)
            .build()
    }

    private fun generateEventHandlerCall(
        handler: ExecutableElement,
        eventRoot: TypeMirror,
        patchRoot: TypeMirror,
        requestRoot: TypeMirror
    ): String {
        val modelTypeName = Model::class.asClassName()
            .parameterizedBy(patchRoot.asTypeName())
        val requestsTypeName = Requests::class.asClassName()
            .parameterizedBy(eventRoot.asTypeName(), requestRoot.asTypeName())

        // allowed param types: event, whole model, selected patch, Requests
        val params = handler.parameters.joinToString(", ") { param ->
            when {
                isSubtype(param.asType(), eventRoot) -> ON_EVENT_PARAM_EVENT
                isProperSubtype(
                    param.asType(),
                    patchRoot
                ) -> "$ON_EVENT_PARAM_MODEL.get<${param.asType().asTypeName()}>()"
                param.asType().asTypeName() == modelTypeName -> ON_EVENT_PARAM_MODEL
                param.asType().asTypeName() == requestsTypeName -> ON_EVENT_PARAM_REQUESTS
                else -> throw InvalidParameterType(handler, param)
            }
        }
        val invocation = "$EMPRESS_MODULE_FIELD.${handler.simpleName}($params)"
        return if (isSubtype(handler.returnType, patchRoot)) {
            "$invocation?.let { listOf(it) } ?: emptyList()"
        } else {
            invocation
        }
    }

    private fun buildEventHandler(
        eventRoot: TypeMirror,
        patchRoot: TypeMirror,
        requestRoot: TypeMirror,
        eventHandlers: Map<TypeMirror, ExecutableElement>
    ): FunSpec {
        val collection = ClassName("kotlin.collections", "Collection")
        val eventCases = eventHandlers
            .map { (t, e) ->
                "is ${t.asTypeName()} -> ${generateEventHandlerCall(
                    handler = e,
                    eventRoot = eventRoot,
                    patchRoot = patchRoot,
                    requestRoot = requestRoot
                )}"
            }
            .plus("else -> throw IllegalArgumentException(\"No handler for event: \$$ON_EVENT_PARAM_EVENT\")")
            .joinToString("\n")

        return FunSpec
            .builder(ON_EVENT_METHOD_NAME)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter(ON_EVENT_PARAM_EVENT, eventRoot.asTypeName())
            .addParameter(
                ON_EVENT_PARAM_MODEL,
                Model::class.asClassName().parameterizedBy(patchRoot.asTypeName())
            )
            .addParameter(
                ON_EVENT_PARAM_REQUESTS,
                Requests::class.asClassName().parameterizedBy(
                    eventRoot.asTypeName(),
                    requestRoot.asTypeName()
                )
            )
            .returns(collection.parameterizedBy(patchRoot.asTypeName()))
            .addCode("return when(%L) {\n %L \n}", ON_EVENT_PARAM_EVENT, eventCases)
            .build()
    }

    private fun buildRequestHandler(
        eventRoot: TypeMirror,
        requestRoot: TypeMirror,
        requestHandlers: Map<TypeMirror, ExecutableElement>
    ): FunSpec {
        val whenCases = requestHandlers
            .map { (t, e) ->
                val isSuspendable = isSuspendable(e)
                val params = when {
                    isSuspendable && e.parameters.size > 1
                            || !isSuspendable && e.parameters.size > 0 -> ON_REQUEST_PARAM_REQUEST
                    else -> ""

                }
                "is ${t.asTypeName()} -> $EMPRESS_MODULE_FIELD.${e.simpleName}($params)"
            }
            .plus("else -> throw IllegalArgumentException(\"No handler for request: \$$ON_REQUEST_PARAM_REQUEST\")")
            .joinToString("\n")
        return FunSpec
            .builder(ON_REQUEST_METHOD_NAME)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter(ON_REQUEST_PARAM_REQUEST, requestRoot.asTypeName())
            .returns(eventRoot.asTypeName())
            .addCode("return when(%L) {\n %L \n}", ON_REQUEST_PARAM_REQUEST, whenCases)
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
                    throw NotProperSubclass(
                        executableElement,
                        executableElement.returnType,
                        patchRoot
                    )
                }

                if (mutableMap.put(executableElement.returnType, executableElement) != null) {
                    throw MoreThanOneInitializer(executableElement, executableElement.returnType)
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
        val modelTypeName = Model::class.asClassName()
            .parameterizedBy(patchRoot.asTypeName())
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

                val eventType = getTypeMirrorFromAnnotationValue { onEventAnnotation.event }

                if (!isProperSubtype(eventType, eventRoot)) {
                    throw NotProperSubclass(executableElement, eventType, eventRoot)
                }

                // If the method has parameters,
                // it has to be an event, patch, Model or Requests
                for (param in executableElement.parameters) {
                    if (
                        !processingEnv.typeUtils.isAssignable(eventType, param.asType())
                        && !isProperSubtype(param.asType(), patchRoot)
                        && param.asType().asTypeName() != modelTypeName
                        && param.asType().asTypeName() != requestsTypeName
                    ) {
                        throw InvalidParameterType(executableElement, param)
                    }
                }

                // Method should return either a single patch, or a collection of patches
                if (!isSubtype(executableElement.returnType, patchRoot)
                    && !isCollectionOfItems(executableElement.returnType, patchRoot)
                ) {
                    throw InvalidReturnType(executableElement, executableElement.returnType)
                }

                if (mutableMap.put(eventType, executableElement) != null) {
                    throw MoreThanOneEventHandler(executableElement, eventType)
                }
            }

        return mutableMap
    }

    /** Checks if a [type] is a Collection<T>, where T is represented by [expectedItemType]. */
    private fun isCollectionOfItems(type: TypeMirror, expectedItemType: TypeMirror): Boolean {
        val declaredType = type as? DeclaredType ?: return false
        val firstTypeArg = declaredType.typeArguments?.firstOrNull() ?: return false
        return declaredType.asTypeName().toString().startsWith("java.util.Collection") && isSubtype(
            firstTypeArg,
            expectedItemType
        )
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
                    throw NotProperSubclass(executableElement, requestType, requestRoot)
                }

                val paramsCount = executableElement.parameters.size
                val isSuspendable = isSuspendable(executableElement)
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
                    val firstParam = executableElement.parameters[0]
                    val firstParamType = firstParam.asType()

                    if (!processingEnv.typeUtils.isAssignable(requestType, firstParamType)) {
                        throw InvalidParameterType(executableElement, firstParam)
                    }

                    // if function is suspendable, return type is part of the last argument
                    if (isSuspendable) {
                        val continuationType =
                            executableElement.parameters[paramsCount - 1].asType() as DeclaredType
                        val continuationTypeName = continuationType.asTypeName()
                        val continuationParamType = continuationType.typeArguments.first()
                        val superBound = (continuationParamType as WildcardType).superBound
                        if (!continuationTypeName.toString().startsWith("kotlin.coroutines.Continuation") || !isSubtype(
                                superBound,
                                eventRoot
                            )
                        ) {
                            throw NotSubclass(
                                executableElement,
                                continuationType.typeArguments.first(),
                                eventRoot
                            )
                        }
                    }
                }

                if (!isSuspendable) {
                    if (!isSubtype(executableElement.returnType, eventRoot)) {
                        throw NotSubclass(
                            executableElement,
                            executableElement.returnType,
                            eventRoot
                        )
                    }
                }

                if (mutableMap.put(requestType, executableElement) != null) {
                    throw MoreThanOneRequestHandler(executableElement, requestType)
                }
            }

        return mutableMap
    }

    private fun isSuspendable(executableElement: ExecutableElement): Boolean {
        return executableElement.parameters.lastOrNull()?.asType()?.asTypeName().toString()
            .startsWith(Continuation::class.asTypeName().toString())
    }

    private fun getTypeMirrorFromAnnotationValue(accessor: () -> KClass<*>): TypeMirror {
        return try {
            processingEnv.elementUtils
                .getTypeElement(accessor().java.canonicalName)
                .asType()
        } catch (e: MirroredTypeException) {
            e.typeMirror
        }
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

    companion object {
        private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

        private const val EMPRESS_MODULE_FIELD = "empressModule"

        private const val INITIALIZER_METHOD_NAME = "initializer"

        private const val ON_EVENT_METHOD_NAME = "onEvent"
        private const val ON_EVENT_PARAM_EVENT = "event"
        private const val ON_EVENT_PARAM_MODEL = "model"
        private const val ON_EVENT_PARAM_REQUESTS = "requests"

        private const val ON_REQUEST_METHOD_NAME = "onRequest"
        private const val ON_REQUEST_PARAM_REQUEST = "request"
    }
}
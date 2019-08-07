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

import com.squareup.kotlinpoet.asTypeName
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

internal sealed class EmpressProcessorException(val element: Element, msg: String) : Throwable(msg)

internal class InvalidParameterCount(
    executableElement: ExecutableElement,
    expectedParamCount: Int
) : EmpressProcessorException(
    executableElement,
    "Method '${executableElement.simpleName}' has wrong number of parameters (expected $expectedParamCount parameters)."
)

internal class InvalidParameterType(
    executableElement: ExecutableElement,
    parameter: VariableElement
) : EmpressProcessorException(
    executableElement,
    "Parameter '$parameter' has unsupported type."
)

internal class InvalidReturnType(
    executableElement: ExecutableElement,
    returnType: TypeMirror
) : EmpressProcessorException(
    executableElement,
    "Invalid return type: '$returnType'."
)

internal class MethodNotPublic(executableElement: ExecutableElement) :
    EmpressProcessorException(
        executableElement,
        "Method '${executableElement.simpleName}' should be public."
    )

internal class MoreThanOneEventHandler(element: Element, eventType: TypeMirror) :
    EmpressProcessorException(
        element,
        "There is more than one event handler for '${eventType.asTypeName()}'."
    )

internal class MoreThanOneInitializer(element: Element, returnType: TypeMirror) :
    EmpressProcessorException(
        element,
        "There is more than one initializer for ${returnType.asTypeName()} patch."
    )

internal class MoreThanOneRequestHandler(element: Element, requestType: TypeMirror) :
    EmpressProcessorException(
        element,
        "There is more than one request handler for '${requestType.asTypeName()}'."
    )

internal class NoParametersAccepted(executableElement: ExecutableElement) :
    EmpressProcessorException(
        executableElement,
        "Method '${executableElement.simpleName}' must not contain any parameters."
    )

internal class NotProperSubclass(
    executableElement: ExecutableElement,
    childType: TypeMirror,
    parentType: TypeMirror
) : EmpressProcessorException(
    executableElement,
    "Type ${childType.asTypeName()} is not a proper subclass of ${parentType.asTypeName()}."
)

internal class NotSubclass(
    executableElement: ExecutableElement,
    childType: TypeMirror,
    parentType: TypeMirror
) : EmpressProcessorException(
    executableElement,
    "Type ${childType.asTypeName()} is not a subclass of ${parentType.asTypeName()}."
)

internal class UnexpectedElement(element: Element) : EmpressProcessorException(
    element,
    "Expected a TypeElement but got ${element.javaClass.name}."
)

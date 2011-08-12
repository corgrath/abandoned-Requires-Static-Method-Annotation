/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 */

package com.osbcp.requiresmethodannotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.tools.Diagnostic.Kind;

/**
 * Annotation processor
 * 
 * @author <a href=\"mailto:christoffer@christoffer.me\">Christoffer Pettersson</a>
 */

@SupportedAnnotationTypes("com.osbcp.requiresmethodannotation.RequiresStaticMethod")
public class RequiresMethodAnnotationProcessor extends AbstractProcessor {

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnvironment) {

		// Create a string builder for out debug messages
		StringBuilder debug = new StringBuilder();

		// Get all classes that has the annotation
		Set<? extends Element> classElements = roundEnvironment.getElementsAnnotatedWith(RequiresStaticMethod.class);

		// For each class that has the annotation
		for (final Element classElement : classElements) {

			// Get the annotation information
			RequiresStaticMethod annotation = classElement.getAnnotation(RequiresStaticMethod.class);
			String expectedMethodName = annotation.name();
			String expectedReturnType = annotation.returns();
			String[] expectedMethodParameters = annotation.parameters();
			String[] expectedMethodThrows = annotation.exceptions();

			// Add some debug information 
			debug.append("annotation-method-name:" + expectedMethodName + "\n");
			debug.append("annotation-method-return-type:" + expectedReturnType + "\n");
			debug.append("annotation-method-expected-parameter:" + toString(expectedMethodParameters) + "\n");
			debug.append("annotation-method-expected-throws:" + toString(expectedMethodThrows) + "\n");

			// Try and fetch the expected method
			Element methodElement = getMethod(debug, classElement, expectedMethodName);

			// Check that the method exists
			if (methodElement == null) {
				error(classElement, debug, "The class '" + classElement.getSimpleName() + "' requires a method namned '" + expectedMethodName + "'.");
				return true;
			}

			// Check that the method is both public and static
			if (!methodElement.getModifiers().contains(Modifier.PUBLIC) && !methodElement.getModifiers().contains(Modifier.STATIC)) {
				error(classElement, debug, "The method '" + expectedMethodName + "' has to be both public and static.");
				return true;
			}

			// Get the method data
			MethodData methodData = getMethodData(debug, methodElement);

			// Check the return type
			if (!methodData.getReturnType().toString().equals(expectedReturnType)) {
				error(classElement, debug, "The method '" + expectedMethodName + "' has to return a '" + expectedReturnType + "'.");
				return true;
			}

			// Check that the correct number of parameters
			if (methodData.getParameterTypes().size() != expectedMethodParameters.length) {
				error(classElement, debug, "The method '" + expectedMethodName + "' requires the parameters '" + toString(expectedMethodParameters) + "'.");
				return true;
			}

			// Check the paramters
			for (int i = 0; i < expectedMethodParameters.length; i++) {
				debug.append("checking-paramter-" + i + ":" + methodData.getParameterTypes().get(i) + ":" + expectedMethodParameters[i] + "\n");
				if (!methodData.getParameterTypes().get(i).toString().equals(expectedMethodParameters[i])) {
					error(classElement, debug, "The method '" + expectedMethodName + "' requires the parameters '" + toString(expectedMethodParameters) + "'.");
					return true;
				}
			}

			// Check that the correct number of throws
			if (methodData.getThrownTypes().size() != expectedMethodThrows.length) {
				error(classElement, debug, "The method '" + expectedMethodName + "' has to throw '" + toString(expectedMethodThrows) + "'.");
				return true;
			}

			// Check the paramters
			for (int i = 0; i < expectedMethodThrows.length; i++) {
				debug.append("checking-throw-" + i + ":" + methodData.getThrownTypes().get(i) + ":" + expectedMethodThrows[i] + "\n");
				if (!methodData.getThrownTypes().get(i).toString().equals(expectedMethodThrows[i])) {
					error(classElement, debug, "The method '" + expectedMethodName + "' has to throw '" + toString(expectedMethodThrows) + "'.");
					return true;
				}
			}

		}

		return true;

	}

	private final Element getMethod(final StringBuilder debug, final Element classElement, final String methodName) {

		List<Element> methods = getEnclosedElements(debug, classElement, ElementKind.METHOD);

		// For each method
		for (final Element methodElement : methods) {

			debug.append("getMethod:" + methodElement.getSimpleName() + ":" + methodName + ":" + methodElement.getSimpleName().toString().equals(methodName) + "\n");
			if (methodElement.getSimpleName().toString().equals(methodName)) {
				return methodElement;
			}

		}

		return null;

	}

	private void error(final Element element, final StringBuilder debug, final String message) {

		// Debug output (uncomment this to show debug message)
		processingEnv.getMessager().printMessage(Kind.ERROR, debug.toString() + "\n\n" + message, element);

		// Production output (uncomment this for production message)
		// processingEnv.getMessager().printMessage(Kind.ERROR, message, element);

	}

	private MethodData getMethodData(final StringBuilder debug, final Element methodElement) {

		MethodData data = new MethodData();

		TypeMirror mirror = methodElement.asType();
		mirror.accept(visitor(data), null);

		return data;

	}

	private TypeVisitor<Boolean, Void> visitor(final MethodData data) {

		return new SimpleTypeVisitor6<Boolean, Void>() {

			public Boolean visitExecutable(ExecutableType t, Void v) {
				data.setReturnType(t.getReturnType());
				data.setParameterTypes(t.getParameterTypes());
				data.setThrownTypes(t.getThrownTypes());
				return true;
			}

		};

	}

	private String toString(final String[] array) {

		StringBuilder output = new StringBuilder();

		for (String s : array) {
			output.append(s.toString() + " ");
		}

		return output.toString().trim();

	}

	private List<Element> getEnclosedElements(final StringBuilder debug, final Element element, final ElementKind elementKind) {

		List<Element> list = new ArrayList<Element>();

		for (Element enclosedElement : element.getEnclosedElements()) {
			debug.append("enclosed:" + enclosedElement.getSimpleName() + ":" + enclosedElement.getKind() + "\n");
			if (enclosedElement.getKind() == elementKind) {
				list.add(enclosedElement);
			}
		}

		return list;

	}

}
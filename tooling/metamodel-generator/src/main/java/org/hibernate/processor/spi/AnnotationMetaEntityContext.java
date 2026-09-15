/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.processor.spi;


import jakarta.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import org.hibernate.processor.model.MetaAttribute;
import org.hibernate.processor.model.Metamodel;

/**
 * Context passed to {@link QuarkusDataTypeNames#addRepositoryMembers(TypeElement, AnnotationMetaEntityContext)}
 * to give the SPI access to the annotation meta entity's internal state needed for generating
 * repository accessors.
 */
public interface AnnotationMetaEntityContext {

	Metamodel metamodel();

	void addMember(String name, MetaAttribute attribute);

	boolean hasMember(String name);

	@Nullable TypeMirror findIdType();

	@Nullable TypeElement primaryEntity();

	boolean usesReactiveClasspath();

	boolean quarkusInjection();

	void message(Element element, String message, Diagnostic.Kind kind);
}

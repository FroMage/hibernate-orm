/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.processor.spi;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import org.hibernate.processor.annotation.CDIAccessorMetaAttribute;
import org.hibernate.processor.annotation.CDITypeMetaAttribute;

import static org.hibernate.processor.util.StringUtil.decapitalize;
import static org.hibernate.processor.util.TypeUtils.implementsInterface;

/**
 * Default implementation returning the original Panache Next type names.
 */
public class DefaultQuarkusDataTypeNames implements QuarkusDataTypeNames {

	@Override
	public String packageName() {
		return "io.quarkus.hibernate.panache";
	}

	@Override
	public String entityMarker() {
		return "io.quarkus.hibernate.panache.PanacheEntityMarker";
	}

	@Override
	public String managedBlockingRepositoryBase() {
		return "io.quarkus.hibernate.panache.managed.blocking.PanacheManagedBlockingRepositoryBase";
	}

	@Override
	public String statelessBlockingRepositoryBase() {
		return "io.quarkus.hibernate.panache.stateless.blocking.PanacheStatelessBlockingRepositoryBase";
	}

	@Override
	public String managedReactiveRepositoryBase() {
		return "io.quarkus.hibernate.panache.managed.reactive.PanacheManagedReactiveRepositoryBase";
	}

	@Override
	public String statelessReactiveRepositoryBase() {
		return "io.quarkus.hibernate.panache.stateless.reactive.PanacheStatelessReactiveRepositoryBase";
	}

	@Override
	public void addRepositoryMembers(TypeElement element, AnnotationMetaEntityContext context) {
		Element managedBlockingRepository = null;
		Element statelessBlockingRepository = null;
		Element managedReactiveRepository = null;
		Element statelessReactiveRepository = null;
		final var nestedRepositories = new ArrayList<String>();

		for ( var enclosedElement : element.getEnclosedElements() ) {
			if ( enclosedElement.getKind() == ElementKind.INTERFACE ) {
				if ( !addUserDefinedRepositoryAccessor( context, element, enclosedElement, nestedRepositories ) ) {
					continue;
				}
				if ( implementsInterface( (TypeElement) enclosedElement, managedBlockingRepositoryBase() ) ) {
					managedBlockingRepository = enclosedElement;
				}
				else if ( implementsInterface( (TypeElement) enclosedElement, statelessBlockingRepositoryBase() ) ) {
					statelessBlockingRepository = enclosedElement;
				}
				else if ( implementsInterface( (TypeElement) enclosedElement, managedReactiveRepositoryBase() ) ) {
					managedReactiveRepository = enclosedElement;
				}
				else if ( implementsInterface( (TypeElement) enclosedElement, statelessReactiveRepositoryBase() ) ) {
					statelessReactiveRepository = enclosedElement;
				}
			}
		}

		if ( context.quarkusInjection() ) {
			final var idType = context.findIdType();
			addAccessor( context, managedBlockingRepository, idType, "managedBlocking",
					managedBlockingRepositoryBase(), nestedRepositories );
			addAccessor( context, statelessBlockingRepository, idType, "statelessBlocking",
					statelessBlockingRepositoryBase(), nestedRepositories );
			if ( context.usesReactiveClasspath() ) {
				addAccessor( context, managedReactiveRepository, idType, "managedReactive",
						managedReactiveRepositoryBase(), nestedRepositories );
				addAccessor( context, statelessReactiveRepository, idType, "statelessReactive",
						statelessReactiveRepositoryBase(), nestedRepositories );
			}
		}
	}

	private boolean addUserDefinedRepositoryAccessor(
			AnnotationMetaEntityContext context, TypeElement entity,
			Element enclosedElement, List<String> nestedRepositories) {
		final var name = enclosedElement.getSimpleName().toString();
		if ( name.endsWith( "_" ) ) {
			context.message( entity,
					"Nested repositories may not have names that end with '_': "
							+ entity.getQualifiedName() + "." + name,
					Diagnostic.Kind.ERROR );
			return false;
		}
		nestedRepositories.add( name + "_" );
		final var propertyName = decapitalize( name );
		final var qualifiedName = ((TypeElement) enclosedElement).getQualifiedName().toString();
		context.addMember( propertyName,
				new CDIAccessorMetaAttribute( context.metamodel(), propertyName, qualifiedName ) );
		return true;
	}

	private void addAccessor(
			AnnotationMetaEntityContext context,
			@Nullable Element repositoryType, @Nullable TypeMirror idType,
			String accessorName, String repositoryBaseType, List<String> nestedRepositories) {
		final var primaryEntity = context.primaryEntity();
		if ( repositoryType != null ) {
			addRepositoryAccessor( context, accessorName,
					((TypeElement) repositoryType).getQualifiedName().toString() );
		}
		else if ( idType != null && primaryEntity != null ) {
			final var typeName = repositoryTypeName( context, accessorName, nestedRepositories );
			final var superType =
					repositoryBaseType + "<" + primaryEntity.getSimpleName() + ", " + idType + ">";
			context.addMember( typeName,
					new CDITypeMetaAttribute( context.metamodel(), typeName, superType ) );
			addRepositoryAccessor( context, accessorName, typeName );
		}
	}

	private void addRepositoryAccessor(
			AnnotationMetaEntityContext context,
			String accessorName, String repositoryType) {
		if ( !context.hasMember( accessorName ) ) {
			context.addMember( accessorName,
					new CDIAccessorMetaAttribute( context.metamodel(), accessorName, repositoryType ) );
		}
		else {
			context.message( context.metamodel().getElement(),
					"Failed to generate accessor in '" + context.primaryEntity()
							+ "' for the generated repository under name '" + accessorName
							+ "' since it is already defined by the user",
					Diagnostic.Kind.WARNING );
		}
	}

	private String repositoryTypeName(
			AnnotationMetaEntityContext context,
			String accessorName, List<String> nestedRepositories) {
		final var typeName = "Panache"
				+ Character.toUpperCase( accessorName.charAt( 0 ) )
				+ accessorName.substring( 1 )
				+ "Repository_";
		return context.hasMember( typeName ) || nestedRepositories.contains( typeName )
				? typeName + "_"
				: typeName;
	}
}

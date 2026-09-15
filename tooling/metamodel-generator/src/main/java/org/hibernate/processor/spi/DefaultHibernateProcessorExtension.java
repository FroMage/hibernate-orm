/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.processor.spi;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import org.hibernate.processor.annotation.CDIAccessorMetaAttribute;
import org.hibernate.processor.annotation.CDITypeMetaAttribute;

import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.hibernate.processor.util.StringUtil.decapitalize;
import static org.hibernate.processor.util.TypeUtils.containsAnnotation;
import static org.hibernate.processor.util.TypeUtils.extendsClass;
import static org.hibernate.processor.util.TypeUtils.implementsInterface;

/**
 * Default implementation handling original Panache type names.
 */
public class DefaultHibernateProcessorExtension implements HibernateProcessorExtension {

	private static final String PANACHE_ORM_REPOSITORY_BASE =
			"io.quarkus.hibernate.orm.panache.PanacheRepositoryBase";
	private static final String PANACHE_ORM_ENTITY_BASE =
			"io.quarkus.hibernate.orm.panache.PanacheEntityBase";
	private static final String PANACHE_REACTIVE_REPOSITORY_BASE =
			"io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase";
	private static final String PANACHE_REACTIVE_ENTITY_BASE =
			"io.quarkus.hibernate.reactive.panache.PanacheEntityBase";

	private static final String QUARKUS_SESSION_OPERATIONS =
			"io.quarkus.hibernate.reactive.panache.common.runtime.SessionOperations";

	private static final String HIB_SESSION = "org.hibernate.Session";
	private static final String HIB_STATELESS_SESSION = "org.hibernate.StatelessSession";
	private static final String MUTINY_SESSION = "org.hibernate.reactive.mutiny.Mutiny.Session";
	private static final String MUTINY_STATELESS_SESSION = "org.hibernate.reactive.mutiny.Mutiny.StatelessSession";
	private static final String UNI = "io.smallrye.mutiny.Uni";
	private static final String UNI_MUTINY_SESSION = UNI + "<" + MUTINY_SESSION + ">";
	private static final String UNI_MUTINY_STATELESS_SESSION = UNI + "<" + MUTINY_STATELESS_SESSION + ">";

	private static final String HQL = "org.hibernate.annotations.processing.HQL";
	private static final String SQL = "org.hibernate.annotations.processing.SQL";
	private static final String JD_QUERY = "jakarta.data.repository.Query";
	private static final String FIND = "org.hibernate.annotations.processing.Find";
	private static final String JD_FIND = "jakarta.data.repository.Find";

	protected String packageName() {
		return "io.quarkus.hibernate.panache";
	}

	protected String entityMarker() {
		return "io.quarkus.hibernate.panache.PanacheEntityMarker";
	}

	protected String managedBlockingRepositoryBase() {
		return "io.quarkus.hibernate.panache.managed.blocking.PanacheManagedBlockingRepositoryBase";
	}

	protected String statelessBlockingRepositoryBase() {
		return "io.quarkus.hibernate.panache.stateless.blocking.PanacheStatelessBlockingRepositoryBase";
	}

	protected String managedReactiveRepositoryBase() {
		return "io.quarkus.hibernate.panache.managed.reactive.PanacheManagedReactiveRepositoryBase";
	}

	protected String statelessReactiveRepositoryBase() {
		return "io.quarkus.hibernate.panache.stateless.reactive.PanacheStatelessReactiveRepositoryBase";
	}

	// --- Classpath detection state ---

	private boolean usesQuarkusOrm;
	private boolean usesQuarkusReactive;
	private boolean usesQuarkusDataHibernate;
	private boolean usesQuarkusReactiveCommon;
	private boolean injectionAvailable;

	@Override
	public void init(ProcessingEnvironment processingEnvironment) {
		final var elements = processingEnvironment.getElementUtils();

		final var quarkusOrmPackage = elements.getPackageElement( "io.quarkus.hibernate.orm" );
		final var quarkusReactivePackage =
				elements.getPackageElement( "io.quarkus.hibernate.reactive.runtime" );

		var quarkusOrmPanachePackage = elements.getPackageElement( "io.quarkus.hibernate.orm.panache" );
		var quarkusReactivePanachePackage =
				elements.getPackageElement( "io.quarkus.hibernate.reactive.panache" );
		var quarkusReactivePanacheCommonPackage =
				elements.getPackageElement( "io.quarkus.hibernate.reactive.panache.common" );
		var quarkusDataHibernatePackage = elements.getPackageElement( packageName() );

		if ( packagePresent( quarkusReactivePanachePackage )
				&& packagePresent( quarkusOrmPanachePackage ) ) {
			processingEnvironment.getMessager().printMessage( Diagnostic.Kind.WARNING,
					"Both Quarkus Hibernate ORM and Hibernate Reactive with Panache detected:"
							+ " this is not supported, so will proceed as if none were there" );
			quarkusOrmPanachePackage = null;
			quarkusReactivePanachePackage = null;
		}

		injectionAvailable = packagePresent( quarkusOrmPackage ) || packagePresent( quarkusReactivePackage );
		usesQuarkusOrm = packagePresent( quarkusOrmPanachePackage );
		usesQuarkusReactive = packagePresent( quarkusReactivePanachePackage );
		usesQuarkusDataHibernate = packagePresent( quarkusDataHibernatePackage );
		usesQuarkusReactiveCommon = packagePresent( quarkusReactivePanacheCommonPackage );
	}

	private static boolean packagePresent(@Nullable PackageElement pack) {
		return pack != null && !pack.getEnclosedElements().isEmpty();
	}

	@Override
	public boolean isInjectionAvailable() {
		return injectionAvailable;
	}

	@Override
	public @Nullable String qualifierAnnotation() {
		return injectionAvailable ? "io.quarkus.hibernate.orm.PersistenceUnit" : null;
	}

	protected boolean usesReactiveClasspath() {
		return usesQuarkusReactiveCommon;
	}

	// --- Type classification ---

	@Override
	public boolean isExtensionEntity(TypeElement type) {
		return isPanacheType( type ) || isQuarkusDataType( type );
	}

	@Override
	public boolean isExtensionRepository(TypeElement type) {
		return isQuarkusDataRepository( type );
	}

	private boolean isPanacheType(TypeElement type) {
		return usesQuarkusOrm && isOrmPanacheType( type )
				|| usesQuarkusReactive && isReactivePanacheType( type );
	}

	private static boolean isOrmPanacheType(TypeElement type) {
		return implementsInterface( type, PANACHE_ORM_REPOSITORY_BASE )
				|| extendsClass( type, PANACHE_ORM_ENTITY_BASE );
	}

	private static boolean isReactivePanacheType(TypeElement type) {
		return implementsInterface( type, PANACHE_REACTIVE_REPOSITORY_BASE )
				|| extendsClass( type, PANACHE_REACTIVE_ENTITY_BASE );
	}

	protected boolean isQuarkusDataType(TypeElement type) {
		return implementsInterface( type, entityMarker() )
				|| isQuarkusDataRepository( type );
	}

	private boolean isQuarkusDataRepository(TypeElement type) {
		return implementsInterface( type, managedBlockingRepositoryBase() )
				|| implementsInterface( type, statelessBlockingRepositoryBase() )
				|| implementsInterface( type, managedReactiveRepositoryBase() )
				|| implementsInterface( type, statelessReactiveRepositoryBase() );
	}

	private boolean isQuarkusDataBlockingRepository(TypeElement type) {
		return implementsInterface( type, managedBlockingRepositoryBase() )
				|| implementsInterface( type, statelessBlockingRepositoryBase() );
	}

	private boolean isQuarkusDataStatelessReactiveRepository(TypeElement type) {
		return implementsInterface( type, statelessReactiveRepositoryBase() );
	}

	private boolean isQuarkusDataStatelessBlockingRepository(TypeElement type) {
		return implementsInterface( type, statelessBlockingRepositoryBase() );
	}

	// --- Repository members ---

	@Override
	public void addRepositoryMembers(TypeElement element, AnnotationMetaEntityContext context) {
		if ( !isQuarkusDataType( element ) ) {
			return;
		}
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

		if ( injectionAvailable ) {
			final var idType = context.findIdType();
			addAccessor( context, managedBlockingRepository, idType, "managedBlocking",
					managedBlockingRepositoryBase(), nestedRepositories );
			addAccessor( context, statelessBlockingRepository, idType, "statelessBlocking",
					statelessBlockingRepositoryBase(), nestedRepositories );
			if ( usesQuarkusReactiveCommon ) {
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

	// --- Session setup ---

	@Override
	public @Nullable SessionSetup setupRepositorySession(
			TypeElement element,
			@Nullable ExecutableElement getter,
			AnnotationMetaEntityContext context) {

		final boolean entity = isExtensionEntity( element );
		final boolean repo = isExtensionRepository( element );

		if ( getter != null ) {
			if ( !entity && !repo ) {
				return null;
			}
			if ( repo ) {
				return new SessionSetup( doSetupRepositoryConstructor( getter, element, context ), true );
			}
			else {
				// Panache 1 entity with session getter: just capture session type, no DAO
				return new SessionSetup( context.fullReturnType( getter ), false );
			}
		}

		// No getter: handle any interface in a Quarkus environment
		if ( element.getKind() == ElementKind.INTERFACE
				&& !context.isJakartaDataRepository()
				&& isInQuarkusEnvironment() ) {
			return new SessionSetup( doSetupRepositoryConstructor( null, element, context ), true );
		}

		return null;
	}

	private boolean isInQuarkusEnvironment() {
		return usesQuarkusOrm || usesQuarkusReactive || usesQuarkusDataHibernate;
	}

	private String doSetupRepositoryConstructor(
			@Nullable ExecutableElement getter,
			@Nullable TypeElement element,
			AnnotationMetaEntityContext context) {
		if ( isBlockingFavored( element, context ) ) {
			final var name = sessionGetterName( getter, element );
			final var sessionType = sessionType( getter, element, context );
			context.addRepositoryConstructor( name, sessionType );
			return sessionType;
		}
		else {
			context.metamodel().importType( QUARKUS_SESSION_OPERATIONS );
			if ( element != null && isQuarkusDataStatelessReactiveRepository( element ) ) {
				context.setSessionGetter( "SessionOperations.getStatelessSession()" );
				return UNI_MUTINY_STATELESS_SESSION;
			}
			else {
				context.setSessionGetter( "SessionOperations.getSession()" );
				return UNI_MUTINY_SESSION;
			}
		}
	}

	private boolean isBlockingFavored(@Nullable TypeElement element, AnnotationMetaEntityContext context) {
		if ( element != null ) {
			if ( usesQuarkusDataHibernate && isQuarkusDataRepository( element ) ) {
				return isQuarkusDataBlockingRepository( element );
			}
			else {
				for ( var method : methodsIn( context.getAllMembers( element ) ) ) {
					if ( containsAnnotation( method, HQL, SQL, JD_QUERY, FIND, JD_FIND ) ) {
						return !isUni( method.getReturnType() );
					}
				}
			}
		}
		return usesQuarkusOrm;
	}

	private String sessionGetterName(
			@Nullable ExecutableElement getter, @Nullable TypeElement element) {
		if ( getter != null ) {
			return getter.getSimpleName().toString();
		}
		else if ( element != null && isQuarkusDataStatelessBlockingRepository( element ) ) {
			return "getStatelessSession";
		}
		else {
			return "getSession";
		}
	}

	private String sessionType(
			@Nullable ExecutableElement getter, @Nullable TypeElement element,
			AnnotationMetaEntityContext context) {
		if ( getter != null ) {
			return context.fullReturnType( getter );
		}
		else if ( element != null && isQuarkusDataStatelessBlockingRepository( element ) ) {
			return HIB_STATELESS_SESSION;
		}
		else {
			return HIB_SESSION;
		}
	}

	private static boolean isUni(TypeMirror returnType) {
		if ( returnType.getKind() == TypeKind.DECLARED ) {
			final var declaredType = (DeclaredType) returnType;
			final var typeElement = (TypeElement) declaredType.asElement();
			return typeElement.getQualifiedName().contentEquals( UNI );
		}
		return false;
	}
}

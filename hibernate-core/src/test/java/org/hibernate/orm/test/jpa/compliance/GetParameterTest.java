/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.compliance;

import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Query;

@Jpa(
		annotatedClasses = GetParameterTest.Person.class
)
public class GetParameterTest {

	@Test
	public void testGetParameterByNameAfterClosingEntityManager(EntityManagerFactoryScope scope) {
		scope.inEntityManager(
				entityManager ->
						Assertions.assertThrows(
								IllegalStateException.class, () -> {
									Query query = entityManager
											.createQuery( "select p from Person p where p.id = :id" );
									query.setParameter( "id", 1 );
									entityManager.close();
									query.getParameterValue( "id" );
								}
						)
		);
	}

	@Test
	public void testGetParameterByPositionAfterClosingEntityManager(EntityManagerFactoryScope scope) {
		scope.inEntityManager(
				entityManager ->
						Assertions.assertThrows(
								IllegalStateException.class, () -> {
									Query query = entityManager
											.createQuery( "select p from Person p where p.id = ?1" );
									query.setParameter( 1, 1 );
									entityManager.close();
									query.getParameterValue( 1 );
								}
						)
		);
	}

	@Entity(name = "Person")
	public static class Person {
		@Id
		private Integer id;

		private String name;
	}

}

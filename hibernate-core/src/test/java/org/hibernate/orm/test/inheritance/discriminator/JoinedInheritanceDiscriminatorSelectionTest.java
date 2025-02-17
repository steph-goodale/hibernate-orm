/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.inheritance.discriminator;

import org.hibernate.testing.jdbc.SQLStatementInspector;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.Jira;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marco Belladelli
 */
@DomainModel( annotatedClasses = {
		JoinedInheritanceDiscriminatorSelectionTest.ParentEntity.class,
		JoinedInheritanceDiscriminatorSelectionTest.ChildA.class,
		JoinedInheritanceDiscriminatorSelectionTest.SubChildA.class,
		JoinedInheritanceDiscriminatorSelectionTest.ChildB.class,
} )
@SessionFactory( useCollectingStatementInspector = true )
@Jira( "https://hibernate.atlassian.net/browse/HHH-17727" )
public class JoinedInheritanceDiscriminatorSelectionTest {
	@BeforeAll
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.persist( new ParentEntity( 1L, "parent" ) );
			session.persist( new ChildA( 2L, "child_a", 2 ) );
			session.persist( new SubChildA( 3L, "sub_child_a", 3, 3 ) );
			session.persist( new ChildB( 4L, "child_b" ) );
		} );
	}

	@AfterAll
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction( session -> session.createMutationQuery( "delete from ParentEntity" ).executeUpdate() );
	}

	@Test
	public void testSelectParentAttribute(SessionFactoryScope scope) {
		final SQLStatementInspector inspector = scope.getCollectingStatementInspector();
		inspector.clear();

		scope.inTransaction( session -> {
			assertThat( session.createQuery(
					"select p.name from ParentEntity p",
					String.class
			).getResultList() ).hasSize( 4 );
			inspector.assertNumberOfJoins( 0, 0 );
			inspector.clear();

			assertThat( session.createQuery(
					"select p.name from ParentEntity p where type(p) = ParentEntity",
					String.class
			).getSingleResult() ).isEqualTo( "parent" );
			inspector.assertNumberOfJoins( 0, 0 );
			inspector.clear();

			assertThat( session.createQuery(
					"select p.name from ParentEntity p where type(p) = ChildA",
					String.class
			).getSingleResult() ).isEqualTo( "child_a" );
			// We still inner-join the subtype table, this could be avoided since we have a discriminator condition
			inspector.assertNumberOfJoins( 0, 1 );
		} );
	}

	@Test
	public void testSelectDiscriminator(SessionFactoryScope scope) {
		final SQLStatementInspector inspector = scope.getCollectingStatementInspector();
		inspector.clear();

		scope.inTransaction( session -> {
			assertThat( session.createQuery(
					"select p.class from ParentEntity p",
					String.class
			).getResultList() ).hasSize( 4 );
			inspector.assertNumberOfJoins( 0, 0 );
			inspector.clear();

			assertThat( session.createQuery(
					"select type(p) from ParentEntity p",
					String.class
			).getResultList() ).hasSize( 4 );
			inspector.assertNumberOfJoins( 0, 0 );
		} );
	}

	@Test
	public void testSelectInstance(SessionFactoryScope scope) {
		final SQLStatementInspector inspector = scope.getCollectingStatementInspector();
		inspector.clear();

		scope.inTransaction( session -> {
			// NOTE: we currently always join all subclasses when selecting the entity instance. We could
			//  maybe avoid this when we have a physical discriminator column and a type filter
			assertThat( session.createQuery(
					"from ParentEntity p where type(p) = ParentEntity",
					ParentEntity.class
			).getResultList() ).hasSize( 1 );
			inspector.assertNumberOfJoins( 0, 3 );
			inspector.clear();

			assertThat( session.createQuery(
					"from ParentEntity p where type(p) = ChildA",
					ParentEntity.class
			).getResultList() ).hasSize( 1 );
			inspector.assertNumberOfJoins( 0, 3 );
			inspector.clear();

			assertThat( session.createQuery(
					"from ParentEntity p where type(p) = SubChildA",
					ParentEntity.class
			).getResultList() ).hasSize( 1 );
			inspector.assertNumberOfJoins( 0, 3 );
			inspector.clear();
		} );
	}

	@Entity( name = "ParentEntity" )
	@Inheritance( strategy = InheritanceType.JOINED )
	@DiscriminatorColumn( discriminatorType = DiscriminatorType.STRING )
	public static class ParentEntity {
		@Id
		private Long id;

		private String name;

		public ParentEntity() {
		}

		public ParentEntity(Long id, String name) {
			this.id = id;
			this.name = name;
		}
	}

	@Entity( name = "ChildA" )
	public static class ChildA extends ParentEntity {
		private Integer propertyA;

		public ChildA() {
		}

		public ChildA(Long id, String name, Integer propertyA) {
			super( id, name );
			this.propertyA = propertyA;
		}
	}

	@Entity( name = "SubChildA" )
	public static class SubChildA extends ChildA {
		private Integer subPropertyA;

		public SubChildA() {
		}

		public SubChildA(Long id, String name, Integer propertyA, Integer subPropertyA) {
			super( id, name, propertyA );
			this.subPropertyA = subPropertyA;
		}
	}

	@Entity( name = "ChildB" )
	public static class ChildB extends ParentEntity {
		public ChildB() {
		}

		public ChildB(Long id, String name) {
			super( id, name );
		}
	}
}

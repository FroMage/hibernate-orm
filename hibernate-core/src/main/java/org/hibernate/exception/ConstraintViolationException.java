/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.exception;
import java.sql.SQLException;

import org.hibernate.JDBCException;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A {@link JDBCException} indicating that the requested DML operation
 * resulted in violation of a defined integrity constraint.
 *
 * @author Steve Ebersole
 */
public class ConstraintViolationException extends JDBCException {

	private final @Nullable String constraintName;

	public ConstraintViolationException(String message, SQLException root, @Nullable String constraintName) {
		super( message, root );
		this.constraintName = constraintName;
	}

	public ConstraintViolationException(String message, SQLException root, String sql, @Nullable String constraintName) {
		super( message, root, sql );
		this.constraintName = constraintName;
	}

	/**
	 * Returns the name of the violated constraint, if known.
	 *
	 * @return The name of the violated constraint, or null if not known.
	 */
	public @Nullable String getConstraintName() {
		return constraintName;
	}
}

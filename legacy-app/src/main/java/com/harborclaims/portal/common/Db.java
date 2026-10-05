package com.harborclaims.portal.common;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** JNDI DataSource lookup, the way it was done in 2008. */
public final class Db {

    private static DataSource dataSource;

    private Db() {
    }

    public static synchronized Connection get() throws SQLException {
        if (dataSource == null) {
            try {
                Context ctx = new InitialContext();
                dataSource = (DataSource) ctx.lookup("java:comp/env/jdbc/HarborDS");
            } catch (NamingException e) {
                throw new SQLException("DataSource lookup failed: " + e.getMessage());
            }
        }
        return dataSource.getConnection();
    }
}

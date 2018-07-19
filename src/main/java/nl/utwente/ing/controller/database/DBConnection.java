/*
 * Copyright (c) 2018, Joost Prins <github.com/joostprins>, Tom Leemreize <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nl.utwente.ing.controller.database;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class DBConnection {

    /**
     * SQL Connection pool to the database.
     */
    private static ComboPooledDataSource databasePool;
    public static DBConnection instance = new DBConnection();

    /**
     * The name of the SQLite database in the project resources
     */
    private static final String DATABASE_NAME = "database.sqlite";

    /**
     * Creates a new connection pool to the local SQLite database.
     * Requires the SQLite database to exist.
     */
    private DBConnection() {
        if (databasePool == null) {
            initializePool();
        }
    }

    private void initializePool() {
        try {
            databasePool = new ComboPooledDataSource();
            databasePool.setDriverClass( "org.sqlite.JDBC" ); //loads the jdbc driver

            String path = "jdbc:sqlite:" + Objects.requireNonNull(this.getClass().getClassLoader().
                    getResource(DATABASE_NAME)).getPath().replace("/", System.getProperty("file.separator")).replace("%20", " ");
            databasePool.setJdbcUrl(path);

            databasePool.setMinPoolSize(5);
            databasePool.setAcquireIncrement(5);
            databasePool.setMaxPoolSize(20);
        } catch (PropertyVetoException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns an initialized connection to the SQLite database.
     * Enables Foreign Key Support for this connection as they are disabled by default.
     *
     * @return connection to the SQLite database
     */
    public Connection getConnection() throws SQLException {
        Connection connection = databasePool.getConnection();
        connection.createStatement().execute("PRAGMA foreign_keys = ON");
        connection.setAutoCommit(false);
        return connection;
    }
}

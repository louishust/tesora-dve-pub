package com.tesora.dve.dbc;

/*

 * #%L
 * Tesora Inc.
 * Database Virtualization Engine
 * %%
 * Copyright (C) 2011 - 2014 Tesora Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */



import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.tesora.dve.common.DBHelper;
import com.tesora.dve.common.PEConstants;
import com.tesora.dve.common.catalog.TestCatalogHelper;
import com.tesora.dve.errmap.MySQLErrors;
import com.tesora.dve.server.bootstrap.BootstrapHost;
import com.tesora.dve.server.global.HostService;
import com.tesora.dve.singleton.Singletons;
import com.tesora.dve.sql.SchemaTest;
import com.tesora.dve.standalone.PETest;

public class DBCTest extends PETest {

	private static final String myDB = "NotTheDefault";
	
	ServerDBConnection conn;

	@BeforeClass
	public static void setup() throws Exception {
		Class<?> bootClass = PETest.class;
		TestCatalogHelper.createTestCatalog(bootClass, 2);
		bootHost = BootstrapHost.startServices(bootClass);
        populateMetadata(bootClass, Singletons.require(HostService.class).getProperties());
        populateSites(DBCTest.class, Singletons.require(HostService.class).getProperties());
	}

	@Before
	public void setupTest() throws Exception {
		Properties props = bootHost.getProperties();
		conn = new ServerDBConnection(props.getProperty(DBHelper.CONN_USER),
				props.getProperty(DBHelper.CONN_PASSWORD),
				null);
		SchemaTest.setTemplateModeOptional();
	}

	@After
	public void cleanupTest() throws Exception {
		if(conn != null)
			conn.closeComms();
	}

	private void createDB() throws Exception {
		conn.execute("create database " + myDB + " default persistent group " + PEConstants.DEFAULT_GROUP_NAME);
		conn.execute("use " + myDB);
	}
	
	private void dropDB() throws Exception {
		conn.execute("drop database if exists " + myDB);
	}
	
	@Test
	public void executeWithResultsTest() throws Exception {
		try {
			createDB();
			conn.execute("CREATE TABLE resultstest (col1 int, col2 varchar(10))");
			conn.executeUpdate("INSERT INTO resultstest VALUES (1,'row1'),(2,'row2'),(3,'row3')");
			ResultSet rs = conn.executeQuery("SELECT * FROM resultstest");

			ResultSetMetaData rsmd = rs.getMetaData();
			assertEquals(2, rsmd.getColumnCount());

			int rc = 0;
			while (rs.next()) {
				assertNotNull(rs.getString(1));
				rc++;
			}
			assertEquals(3, rc);
		} finally {
			dropDB();
		}
	}
	
	@Test
	public void executeUpdateTest() throws Exception {
		try {
			createDB();
			assertEquals(0, conn.executeUpdate("CREATE TABLE updatetest (col1 int)"));
			assertEquals(3, conn.executeUpdate("INSERT INTO updatetest VALUES (1),(2),(3)"));			
		} finally {
			dropDB();
		}
	}

	@Test
	public void executeUpdateFailureTest() throws Throwable {
		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				conn.executeUpdate("INSERT INTO foo VALUES (1)");
			}
		}.assertSqlError(SQLException.class, MySQLErrors.missingDatabaseFormatter);
	}
}

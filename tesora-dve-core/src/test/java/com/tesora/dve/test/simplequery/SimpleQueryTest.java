package com.tesora.dve.test.simplequery;

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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.PooledDataSource;
import com.tesora.dve.common.DBHelper;
import com.tesora.dve.common.catalog.TemplateMode;
import com.tesora.dve.common.catalog.TestCatalogHelper;
import com.tesora.dve.errmap.MySQLErrors;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.server.bootstrap.BootstrapHost;
import com.tesora.dve.server.global.HostService;
import com.tesora.dve.singleton.Singletons;
import com.tesora.dve.sql.SchemaException;
import com.tesora.dve.sql.SchemaTest;
import com.tesora.dve.sql.util.ConnectionResource;
import com.tesora.dve.sql.util.ProxyConnectionResource;
import com.tesora.dve.sql.util.ResourceResponse;
import com.tesora.dve.standalone.PETest;
import com.tesora.dve.variable.VariableConstants;
import com.tesora.dve.worker.DBConnectionParameters;

public class SimpleQueryTest extends SchemaTest {

	static {
		logger = Logger.getLogger(SimpleQueryTest.class);
	}

	protected static ProxyConnectionResource conn = null;
	protected static DBConnectionParameters dbParams;

	static final String COUNT_ROWS_SELECT = "select * from foo";

	public static void declareSchema(ConnectionResource conn) throws Throwable {
		declareSchema(conn,null, null);
	}

	public static void cleanupSites(int nSites, String...dbNames) throws Throwable {
		DBHelper helper = null;
		try {
			helper = PETest.buildHelper();
			for(int i = 1; i <= nSites; i++) {
				for(String s : dbNames) {
					helper.executeQuery(String.format("drop database if exists site%d_%s",i,s));
				}
			}
		} finally {
			helper.disconnect();
		}
	}
	
	public static void createSites(int nSites, ConnectionResource cr) throws Throwable {
		for(int i = 1; i <= nSites; i++) {
			cr.execute(String.format("create persistent site site%d url='%s' user='%s' password='%s'",
					i,
					TestCatalogHelper.getInstance().getCatalogBaseUrl(),
					TestCatalogHelper.getInstance().getCatalogUser(),
					TestCatalogHelper.getInstance().getCatalogPassword()));
		}
	}
	
	public static void createGroupAndTestDB(int nSites, ConnectionResource cr) throws Throwable {
		StringBuilder buf = new StringBuilder();
		for(int i = 1; i <= nSites; i++) {
			if (i > 1) buf.append(",");
			buf.append("site"+i);
		}
		cr.execute("create persistent group DefaultGroup add " + buf.toString());
		cr.execute(String.format("alter dve set %s = '%s'",VariableConstants.TEMPLATE_MODE_NAME, TemplateMode.OPTIONAL));
		cr.execute("create database TestDB default character set utf8 default persistent group DefaultGroup");
		cr.execute("use TestDB");		
	}
	
	public static void declareSchema(ConnectionResource conn, String distOn,
			String rangeDecl) throws Throwable {
		// do the cleanup that formerly occurred in the per site loads
		if (distOn == null)
			distOn = "random distribute";
		cleanupSites(2,"TestDB2","TestDB","MyTestDB");
		// create site1, site2, build DefaultGroup
		createSites(2,conn);
		createGroupAndTestDB(2,conn);
		if (rangeDecl != null)
			conn.execute(rangeDecl);
		conn.execute("create table foo (id int, value varchar(20)) " + distOn);
		conn.execute("create table bar (id int, value varchar(20)) " + distOn);
	}
	
	@BeforeClass
	public static void setup() throws Throwable {
		TestCatalogHelper.createTestCatalog(PETest.class);
		bootHost = BootstrapHost.startServices(PETest.class);
        dbParams = new DBConnectionParameters(Singletons.require(HostService.class).getProperties());
		conn = new ProxyConnectionResource(dbParams.getUserid(), dbParams.getPassword());
		declareSchema(conn);
        populateSites(SimpleQueryTest.class, Singletons.require(HostService.class).getProperties());
	}

	@AfterClass
	public static void shutdown() throws Exception {
		try {
			if (conn != null && conn.isConnected()) {
				conn.disconnect();
			}
		} catch (Throwable e) {
			failWithStackTrace(e);
		}
		conn = null;
	}

	@Test
	public void checkRowCount() throws Throwable {
		assertEquals(5, getRowCount(COUNT_ROWS_SELECT));
	}

	@Test
	public void emptyResultSet() throws Throwable {
		conn.assertResults("select * from foo where 0=1", SchemaTest.br());
	}

	@Test(expected = SchemaException.class)
	public void badTableName() throws Throwable {
		conn.execute("select * from no_table_exists");
	}

	@Test
	public void twoStatementsSingleConnection() throws Throwable {

		conn.assertResults("select * from foo where id = 2", SchemaTest.br(SchemaTest.nr, 2, "value2"));
		conn.assertResults("select * from foo where id = 3", SchemaTest.br(SchemaTest.nr, 3, "value3"));
	}

	@Test
	public void manyTest() throws Throwable {
		 if (!Boolean.getBoolean("SimpleQueryTest.manyTest")) {
			for (int i = 0; i < 10000; ++i)
				try {
					twoStatementsSingleConnection();
				} catch (Exception e) {
//					System.out.println(i);
				}
		 }
//		Thread.sleep(1000000);
	}

	private int getRowCount(String sql) throws Throwable {
		ResourceResponse response = conn.execute(sql);
		return response.getResults().size();
	}
	
	@Test
	public void badTableNameQueryBeforeGoodQuery() throws Throwable {

		// exception not thrown for bad table name
		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("select * from no_table_exists");
			}
		}.assertError(SchemaException.class, MySQLErrors.missingTableFormatter,
					"TestDB","no_table_exists");

		assertEquals(5, getRowCount(COUNT_ROWS_SELECT));
	}


	@SuppressWarnings({ "unused", "resource" })
	@Test(expected = PEException.class)
	public void invalidUserConnectTest() throws Throwable {
		new ProxyConnectionResource("bad_user", "bad_pwd");
	}

	@Ignore @Test
	public void c3p0Test() {
//		ComboPooledDataSource ds = new ComboPooledDataSource();
//		ds.setJdbcUrl("jdbc:mysql://localhost:3307");
//		ds.setUser("root");
//		ds.setPassword("password");
		
		@SuppressWarnings("unchecked")
		Set<PooledDataSource> cp = C3P0Registry.getPooledDataSources();
		PooledDataSource pds = cp.iterator().next();
		System.out.println("DS name: " + pds.getDataSourceName());
		DataSource ds = cp.iterator().next();

		try {
			System.out.println(pds.getNumConnectionsDefaultUser());
//			DataSource uds = DataSources.unpooledDataSource("jdbc:mysql://localhost:3307", "root", "password");
//			DataSource ds = DataSources.pooledDataSource(uds);
			
			ArrayList<Connection> ca = new ArrayList<Connection>();
			for (int i = 0; i < 6; ++i) {
				Connection s = ds.getConnection();
				ca.add(s);
				System.out.println(s);
			}
			
			Connection c = ds.getConnection();
			Statement s = c.createStatement();
			boolean hasResult = s.execute("select * from persistent_group");
			if (hasResult) {
				ResultSet rs = s.getResultSet();
				while (rs.next())
					System.out.println(rs.getString("name"));
			}
			else {
				System.out.println("Wrong result type");
			}
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}

// OS_STATUS: public
package com.tesora.dve.standalone;

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

import static org.junit.Assert.assertTrue;

import com.tesora.dve.singleton.Singletons;
import io.netty.buffer.ByteBuf;
import io.netty.util.ResourceLeakDetector;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.tesora.dve.common.DBHelper;
import com.tesora.dve.common.PEBaseTest;
import com.tesora.dve.common.PEFileUtils;
import com.tesora.dve.common.catalog.CatalogDAO;
import com.tesora.dve.common.catalog.PersistentSite;
import com.tesora.dve.common.catalog.StorageSite;
import com.tesora.dve.common.catalog.TestCatalogHelper;
import com.tesora.dve.common.catalog.CatalogDAO.CatalogDAOFactory;
import com.tesora.dve.comms.client.messages.ConnectRequest;
import com.tesora.dve.comms.client.messages.ConnectResponse;
import com.tesora.dve.comms.client.messages.CreateStatementRequest;
import com.tesora.dve.comms.client.messages.CreateStatementResponse;
import com.tesora.dve.comms.client.messages.ExecuteRequest;
import com.tesora.dve.comms.client.messages.ExecuteResponse;
import com.tesora.dve.comms.client.messages.FetchRequest;
import com.tesora.dve.comms.client.messages.FetchResponse;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.lockmanager.LockManager;
import com.tesora.dve.resultset.ResultChunk;
import com.tesora.dve.resultset.ResultColumn;
import com.tesora.dve.resultset.ResultRow;
import com.tesora.dve.server.bootstrap.BootstrapHost;
import com.tesora.dve.server.connectionmanager.SSConnectionProxy;
import com.tesora.dve.sql.template.TemplateBuilder;
import com.tesora.dve.sql.util.ProjectDDL;
import com.tesora.dve.sql.util.StorageGroupDDL;
import com.tesora.dve.worker.DBConnectionParameters;

/**
 * Class for tests requiring the DVE engine to be running.
 * 
 */
public class PETest extends PEBaseTest {

	protected static CatalogDAO catalogDAO = null;
	protected static BootstrapHost bootHost = null;
	// kindly leave this public - sometimes it is used for not yet committed tests
	public static Class<?> resourceRoot = PETest.class;
	private static long nettyLeakCount = 0;

	// This is so that any TestNG tests will print out the class name
	// as the test is running (to mimic JUnit behavior)
	@org.testng.annotations.BeforeClass
	@Override
	public void beforeClassTestNG() {
		System.out.println("Running " + this.getClass().getName());
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
        delay("running test class","beforeClass.delay", 0L);

		applicationName = "PETest";

		System.setProperty(ResourceLeakDetector.SYSTEM_ENABLE_LEAK_DETECTION, "true");
		System.setProperty(ResourceLeakDetector.SYSTEM_LEAK_DETECTION_INTERVAL, "1");
		System.setProperty(ResourceLeakDetector.SYSTEM_REPORT_ALL, "true");

		ResourceLeakDetector<?> detector = ResourceLeakDetector.getDetector(ByteBuf.class);
		if (detector != null)
			nettyLeakCount = detector.getLeakCount();

		logger = Logger.getLogger(PETest.class);
	}

    private static void delay(String activity, String property, long defaultDelayMillis) {
        long delayTime = defaultDelayMillis;
        String delay = System.getProperty(property);
        if (delay != null) {
            delayTime = Long.parseLong(delay) * 1000;
        }
        try {
            if (delayTime != defaultDelayMillis)
                System.out.println("Pausing for " + delayTime + " millis before " + activity);
            Thread.sleep(delayTime);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    @Before
	public void beforeEachTest() throws PEException {
        delay("running test case","beforeTest.delay", 0L);
	}

	@AfterClass
	public static void teardownPETest() throws Throwable {
        delay("tearing down the PE","afterClass.delay", 100L);

		if (catalogDAO != null) {
			catalogDAO.close();
			catalogDAO = null;
		}

		List<Throwable> finalThrows = new ArrayList<Throwable>();

		try {
			SSConnectionProxy.checkForLeaks();
		} catch (Throwable e) {
			// Don't throw the exception now - since we want to continue doing
			// cleanup
			finalThrows.add(e);
		}

		if (bootHost != null) {
			try {
                checkForLeakedLocks(Singletons.lookup(LockManager.class));
			} catch (Throwable e) {
				finalThrows.add(e);
			}

			// if (!bootHost.getWorkerManager().allWorkersReturned())
				// finalThrows.add(new Exception("Not all workers returned"));

			BootstrapHost.stopServices();

			bootHost = null;
		}

		ResourceLeakDetector<?> detector = ResourceLeakDetector.getDetector(ByteBuf.class);
		if (detector != null && detector.getLeakCount() > nettyLeakCount)
			finalThrows.add(new Exception("Netty ByteBuf leak detected!"));

		if (finalThrows.size() > 0) {
			if (logger.isDebugEnabled()) {
				for (Throwable th : finalThrows) {
					logger.debug(th);
				}
			}
			throw finalThrows.get(0);
		}
	}

    private static void checkForLeakedLocks(LockManager mgr) throws Exception {
        String lockCheck = mgr.assertNoLocks();
        if (lockCheck == null)
            return;
        throw new Exception(lockCheck);
    }

    public PETest() {
		super();
		SSConnectionProxy.setOperatingContext(this.getClass().getSimpleName());
	}

	public static void populateMetadata(Class<?> testClass, Properties props) throws Exception {
		populateMetadata(testClass, props, "metadata.sql");
	}

	public static void populateMetadata(Class<?> testClass, Properties props, String sqlRes) throws Exception {
		catalogDAO = CatalogDAOFactory.newInstance();
		InputStream is = testClass.getResourceAsStream(sqlRes);
		if (is != null) {
			logger.info("Reading SQL statements from " + sqlRes);
			DBHelper dbh = new DBHelper(props).connect();
			try {
				dbh.executeFromStream(is);
			} finally {
				dbh.disconnect();
			}
			is.close();
		}
	}

	public static void populateTemplate(DBHelper dbh, String name) throws Exception {
		dbh.executeQuery(TemplateBuilder.getClassPathCreate(name));
	}

	public static void populateTemplate(String url, String username, String password, String name) throws Exception {
		DBHelper dbh = new DBHelper(url, username, password).connect();
		try {
			dbh.executeQuery(TemplateBuilder.getClassPathCreate(name));
		} finally {
			dbh.disconnect();
		}
	}

	public static void populateSites(Class<?> testClass, Properties props) throws PEException, SQLException {
		populateSites(testClass, props, "");
	}

	public static void populateSites(Class<?> testClass, Properties props, String prefix) throws PEException,
			SQLException {
		List<PersistentSite> allSites = catalogDAO.findAllPersistentSites();

		for (StorageSite site : allSites) {
			String sqlRes = prefix + site.getName() + "-load.sql";
			InputStream is = testClass.getResourceAsStream(sqlRes);
			if (is != null) {
				logger.info("Reading SQL statements from " + sqlRes);
				DBHelper dbh = new DBHelper(props).connect();
				try {
					dbh.executeFromStream(is);
					dbh.disconnect();

					is.close();
				} catch (IOException e) {
					// ignore
				} finally {
					dbh.disconnect();
				}
			}
		}
	}

	public class ExecuteResult {
		public String stmtId;
		public ExecuteResponse execResp;

		public ExecuteResult(String stmtId, ExecuteResponse resp) {
			this.stmtId = stmtId;
			this.execResp = resp;
		}
	}

	public ExecuteResult executeStatement(SSConnectionProxy conProxy, DBConnectionParameters dbParams, String command)
			throws Exception {
		return executeStatement(conProxy, dbParams, command, "TestDB");
	}

	public ExecuteResult executeStatement(SSConnectionProxy conProxy, DBConnectionParameters dbParams, String command,
			String onDB) throws Exception {
		ConnectRequest conReq = new ConnectRequest(dbParams.getUserid(), dbParams.getPassword());
		ConnectResponse resp = (ConnectResponse) conProxy.executeRequest(conReq);
		assertTrue(resp.isOK());

		CreateStatementRequest csReq = new CreateStatementRequest();
		CreateStatementResponse csResp = (CreateStatementResponse) conProxy.executeRequest(csReq);
		assertTrue(csResp.isOK());
		String stmtId = csResp.getStatementId();

		ExecuteRequest execReq = new ExecuteRequest(stmtId, "use " + onDB);
		ExecuteResponse execResp = (ExecuteResponse) conProxy.executeRequest(execReq);
		assertTrue(execResp.isOK());

		execReq = new ExecuteRequest(stmtId, command);
		execResp = (ExecuteResponse) conProxy.executeRequest(execReq);
		assertTrue(execResp.isOK());

		return new ExecuteResult(stmtId, execResp);
	}

	public int showAllRows(SSConnectionProxy conProxy, DBConnectionParameters dbParams, String command)
			throws Exception {
		return showAllRows(conProxy, dbParams, command, "TestDB");
	}

	public int showAllRows(SSConnectionProxy conProxy, DBConnectionParameters dbParams, String command, String onDB)
			throws Exception {

		ExecuteResult result = executeStatement(conProxy, dbParams, command, onDB);
		String stmtId = result.stmtId;
		return showAllResultRows(conProxy, stmtId);
	}

	public int showAllResultRows(SSConnectionProxy conProxy, String stmtId) throws Exception {

		boolean moreData = false;
		int rowsPrinted = 0;
		String line = "";
		do {
			FetchRequest fetchReq = new FetchRequest(stmtId);
			FetchResponse fetchResp = (FetchResponse) conProxy.executeRequest(fetchReq);
			assertTrue(fetchResp.isOK());
			moreData = !fetchResp.noMoreData();
			if (moreData) {
				line += stmtId + ":";
				ResultChunk chunk = fetchResp.getResultChunk();
				List<ResultRow> rowList = chunk.getRowList();
				for (ResultRow row : rowList) {
					List<ResultColumn> colList = row.getRow();
					for (ResultColumn col : colList) {
						line += "\t" + col.getColumnValue().toString();
					}
					logger.debug(line);
					line = "";
					++rowsPrinted;
				}
			}
		} while (moreData);
		logger.debug("" + rowsPrinted + " rows printed"); // NOPMD by doug on 04/12/12 2:02 PM
		return rowsPrinted;
	}

	public static DBHelper buildHelper() throws Exception {
		Properties catalogProps = TestCatalogHelper.getTestCatalogProps(resourceRoot);
		Properties tempProps = (Properties) catalogProps.clone();
		tempProps.remove(DBHelper.CONN_DBNAME);
		DBHelper dbHelper = new DBHelper(tempProps);
		dbHelper.connect();
		return dbHelper;
	}

	protected static void projectSetup(StorageGroupDDL[] extras, ProjectDDL... proj) throws Exception {
		TestCatalogHelper.createTestCatalog(resourceRoot);

		DBHelper dbh = buildHelper();

		try {
			for (ProjectDDL pdl : proj) {
				for (String s : pdl.getSetupDrops())
					dbh.executeQuery(s);
				if (extras != null) {
					for (StorageGroupDDL sgl : extras) {
						for (String s : sgl.getSetupDrops(pdl.getDatabaseName()))
							dbh.executeQuery(s);
					}
				}
			}
		} finally {
			if (dbh != null)
				dbh.disconnect();
			CatalogDAOFactory.clearCache();
		}
		// TestCatalogHelper.populateMinimalCatalog(TestCatalogHelper.getCatalogDBUrl());
	}

	public static void projectSetup(ProjectDDL... proj) throws Exception {
		PETest.projectSetup(null, proj);
	}

	public static void loadSchemaIntoPE(DBHelper dbHelper, Class<?> testClass, String schemaFile, int numSites,
			String dbName) throws Exception {
		cleanupDatabase(numSites, dbName);
		try {
			dbHelper.connect();
			dbHelper.executeFromStream(PEFileUtils.getResourceStream(testClass, schemaFile));
		} finally {
			dbHelper.disconnect();
		}
	}

	public static void cleanupDatabase(int numSites, String dbName) throws Exception {
		Properties catalogProps = TestCatalogHelper.getTestCatalogProps(PETest.class);
		Properties tempProps = (Properties) catalogProps.clone();
		tempProps.remove(DBHelper.CONN_DBNAME);
		DBHelper myHelper = new DBHelper(tempProps).connect();
		try {
			for (int i = 1; i <= numSites; i++) {
				myHelper.executeQuery("DROP DATABASE IF EXISTS site" + i + "_" + dbName);
			}
		} finally {
			myHelper.disconnect();
		}
	}
}

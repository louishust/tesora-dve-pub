package com.tesora.dve.sql;

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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.tesora.dve.charset.*;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.tesora.dve.common.DBHelper;
import com.tesora.dve.common.catalog.TemplateMode;
import com.tesora.dve.db.mysql.MysqlNativeType;
import com.tesora.dve.db.mysql.MysqlNativeType.MysqlType;
import com.tesora.dve.errmap.InternalErrors;
import com.tesora.dve.errmap.MySQLErrors;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.exceptions.PESQLException;
import com.tesora.dve.resultset.ResultRow;
import com.tesora.dve.server.bootstrap.BootstrapHost;
import com.tesora.dve.singleton.Singletons;
import com.tesora.dve.sql.schema.modifiers.TypeModifier;
import com.tesora.dve.sql.schema.types.BasicType;
import com.tesora.dve.sql.schema.types.Type;
import com.tesora.dve.sql.template.TemplateBuilder;
import com.tesora.dve.sql.template.jaxb.ModelType;
import com.tesora.dve.sql.template.jaxb.TableTemplateType;
import com.tesora.dve.sql.template.jaxb.Template;
import com.tesora.dve.sql.util.ConnectionResource;
import com.tesora.dve.sql.util.PEDDL;
import com.tesora.dve.sql.util.Pair;
import com.tesora.dve.sql.util.ProjectDDL;
import com.tesora.dve.sql.util.ProxyConnectionResource;
import com.tesora.dve.sql.util.StorageGroupDDL;
import com.tesora.dve.standalone.PETest;
import com.tesora.dve.variable.VariableConstants;

public class AlterTest extends SchemaTest {

	private static final StorageGroupDDL sg = new StorageGroupDDL("check",1,"checkg");

	
	private static final ProjectDDL checkDDL =
		new PEDDL("adb",sg,"database");
	private static final ProjectDDL oDDL =
		new PEDDL("odb",sg,"schema");
	
	@BeforeClass
	public static void setup() throws Exception {
		// pe855 also creates some databases, make sure they get cleaned up appropriately in setup
		String[] pe855names = new String[] { "pe855db", "pe855temp1", "pe855temp2", "pe855temp3" }; 
		PETest.projectSetup(checkDDL,oDDL);
		
		DBHelper dbh = buildHelper();
		try {
			for (String sdb : pe855names) {
				for (String s : sg.getSetupDrops(sdb))
					dbh.executeQuery(s);
			}
		} finally {
			dbh.disconnect();
		}
		
		PETest.bootHost = BootstrapHost.startServices(PETest.class);
	}


	protected ProxyConnectionResource conn = null;
	
	private static final String tabDefs[] = new String[] {
		"create table `altest` ( "
		+ "`cola` int not null auto_increment, "
		+ "`module` varchar(64) not null"
		+ ") Engine=InnoDB",
		"create table `baltest` ( "
		+ "`soda` int not null "
		+ ") Engine=InnoDB"
	};
		
	@Before
	public void createTable() throws Throwable {
		conn = new ProxyConnectionResource();
		oDDL.create(conn);
		checkDDL.create(conn);
		for(int i = 0; i < tabDefs.length; i++)
			conn.execute(tabDefs[i]);
	}

	@After
	public void dropTable() throws Throwable {
		if(conn != null)
			conn.disconnect();
		conn = null;
	}
	
	@Test
	public void testRename() throws Throwable {
		// shouldn't be able to rename to existing table name
		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table altest rename to `baltest`");
			}
		}.assertError(SchemaException.class, InternalErrors.internalFormatter,
					"Internal error: Table `baltest` already exists");
		conn.execute("alter table altest rename to `ralter`");
		conn.assertResults("show tables like 'ralter'",br(nr,"ralter"));
		conn.assertResults("show tables like 'altest'",br());
	}
	
	public static String getCreateTable(ConnectionResource conn, String tabname) throws Throwable {
		List<ResultRow> results = conn.fetch("show create table " + tabname).getResults();
		assertEquals("only one table for show create table",1,results.size());
		ResultRow rr = results.get(0);
		assertEquals("two columns for show create table",2,rr.getRow().size());
		String decl = (String)rr.getResultColumn(2).getColumnValue();
//		System.out.println(decl);
		return decl;
	}
	
	private String getCreateTable(String tabname) throws Throwable {
		String cts = getCreateTable(conn,tabname);
		return cts;
	}
	
	@Test
	public void testAddRemoveIndex() throws Throwable {
		conn.execute("alter table altest add index `indone` (`cola`,`module`)");
		String cts = getCreateTable("altest");
		assertTrue("should have index",cts.indexOf("indone") > -1);
		conn.execute("alter table altest drop index `indone`");
		cts = getCreateTable("altest");
		assertEquals("should not have index",-1,cts.indexOf("indone"));
	}
	
	@Test
	public void testAddChangeRemoveColumn() throws Throwable {
		conn.execute("alter table altest add `book` int not null");
		String cts = getCreateTable("altest");
		assertTrue("should have new column",cts.indexOf("book") > -1);
		conn.assertResults("show columns in altest like 'book'",br(nr,"book","int(11)","NO","",null,""));
		conn.execute("alter table altest change `book` `pamphlet` varchar(64) null");
		cts = getCreateTable("altest");
		assertTrue("should have new col def",cts.indexOf("pamphlet") > -1);
		SchemaTest.echo(conn.printResults("show columns in altest like 'pamphlet'"));
		conn.execute("alter table altest alter column `pamphlet` set default 'howdy stranger'");
		cts = getCreateTable("altest");
		assertTrue("should have def value",cts.indexOf("howdy stranger") > -1);
		conn.execute("alter table altest alter column `pamphlet` drop default");
		cts = getCreateTable("altest");
		assertEquals("should not have def value",-1,cts.indexOf("howdy stranger"));
		conn.execute("alter table altest drop `pamphlet`");
		cts = getCreateTable("altest");
		assertEquals("should not have removed column",-1,cts.indexOf("pamphlet"));
		conn.execute("alter table altest add column `book` int not null");
		cts = getCreateTable("altest");
		assertTrue("should have book again",cts.indexOf("book") > -1);
		conn.execute("alter table altest drop column `book`");
		cts = getCreateTable("altest");
		assertEquals("should not have column book",-1,cts.indexOf("book"));
		conn.assertResults("show columns in altest like 'pamphlet'", br());
	}
	
	@Test
	public void testMultiAlter() throws Throwable {
		conn.execute("alter table altest add `book` int not null default 15 comment 'this is a comment', add index `book_index` (`book`)");
		conn.assertResults("show columns in altest like 'book'", 
				br(nr,"book","int(11)","NO","MUL","15",""));
		conn.assertResults("show keys in altest where Key_name like 'book_index'",
				br(nr,"altest",I_ONE,"book_index",I_ONE,"book","A",getIgnore(),null,null,"","BTREE","",""));
		String cts = getCreateTable("altest");
		assertTrue("should have new column", cts.indexOf("book") > -1);
		assertTrue("should have new index", cts.indexOf("book_index") > -1);
	}

	@Test
	public void testAlterSameColName() throws Throwable {
		// test changing column attributes
		conn.assertResults("show columns in altest like 'module'",br(nr,"module","varchar(64)","NO","",null,""));
		conn.execute("alter table altest change `module` `module` VARCHAR(64) NULL DEFAULT NULL");
		conn.assertResults("show columns in altest like 'module'",br(nr,"module","varchar(64)","YES","",null,""));
		
		// test changing the length
		conn.execute("alter table altest change `module` `module` VARCHAR(128)");
		conn.assertResults("show columns in altest like 'module'",br(nr,"module","varchar(128)","YES","",null,""));
	}
	
	@Test
	public void testPE600() throws Throwable {
		conn.execute("create table pe600 (`id` int, `symbol` varchar(50), primary key (`id`), key `symbol` (`symbol`)) random distribute");
		conn.execute("alter table pe600 change `symbol` `symbol` varchar(100) character set latin1 collate latin1_swedish_ci not null");
		conn.assertResults("show columns in pe600 like 'symbol'",br(nr,"symbol","varchar(100)","NO","",null,""));
	}
	
	@Test
	public void testPE617() throws Throwable {
		conn.execute("create table pe617 ("
				+"`uid` int(10) NOT NULL ,"
				+"`country` varchar(255) NOT NULL , "
				+"`state` varchar(50) NOT NULL , `postal_code` varchar(10) NOT NULL ,"
				+"`vid` int(10) unsigned NOT NULL ,"
				+"`is_active` tinyint(1) NOT NULL ,"
				+" PRIMARY KEY (`uid`),"
				+" KEY `country` (`country`), KEY `vid` (`vid`), KEY `is_active` (`is_active`)) ENGINE = MyISAM  CHARSET = utf8  /*#dve  BROADCAST DISTRIBUTE */");
		conn.execute("alter table pe617 "
				+"add column (kts char(1), ktsp char(1), ktsf char(1)), "
				+"add index kts (kts), "
				+"add index ktsp (ktsp), " 
				+"add index ktsf (ktsf)");
		conn.assertResults("show columns in pe617 like 'kt%'",
				br(nr,"kts","char(1)","YES","MUL",null,"",
				   nr,"ktsp","char(1)","YES","MUL",null,"",
				   nr,"ktsf","char(1)","YES","MUL",null,""));
		conn.assertResults("show keys in pe617 where Key_name like 'kt%'",
				br(nr,"pe617",I_ONE,"ktsf",I_ONE,"ktsf","A",getIgnore(),null,null,"YES","BTREE","","",
				   nr,"pe617",I_ONE,"kts",I_ONE,"kts","A",getIgnore(),null,null,"YES","BTREE","","",
				   nr,"pe617",I_ONE,"ktsp",I_ONE,"ktsp","A",getIgnore(),null,null,"YES","BTREE","",""));
	}
	
	@Test
	public void testPE654() throws Throwable {
		conn.execute("create table maone(`id` int, `entity_id` int, `attribute_id` int not null default '15', `store_id` int, primary key (`store_id`)) engine=innodb broadcast distribute");
		String engineFmt = "select engine from information_schema.tables where table_schema = '" + checkDDL.getDatabaseName() + "' and table_name = '%s'";
		String distFmt = "select model_type from information_schema.distributions where database_name = '" + checkDDL.getDatabaseName() + "' and table_name = '%s'";
		conn.assertResults(String.format(engineFmt,"maone"), br(nr,"InnoDB"));
		conn.assertResults(String.format(distFmt,"maone"),br(nr,"Broadcast"));
		conn.execute("alter table maone engine=memory");
		conn.assertResults(String.format(engineFmt,"maone"), br(nr,"MEMORY"));
		conn.assertResults(String.format(distFmt,"maone"),br(nr,"Broadcast"));
		conn.execute("alter table maone drop primary key, add primary key (`id`, `entity_id`)");
		conn.assertResults("show keys in maone",
				br(nr,"maone",I_ZERO,"PRIMARY",I_ONE,"id","A",getIgnore(),null,null,"","BTREE","","",
				   nr,"maone",I_ZERO,"PRIMARY",new Integer(2),"entity_id","A",getIgnore(),null,null,"","BTREE","",""));
		conn.assertResults("describe maone",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"entity_id","int(11)","NO","PRI",null,"",
				   nr,"attribute_id","int(11)","NO","","15","",
				   nr,"store_id","int(11)","NO","",null,""));
		conn.execute("alter table maone modify column `attribute_id` int null default null comment 'Attribute'");
		conn.assertResults("describe maone",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"entity_id","int(11)","NO","PRI",null,"",
				   nr,"attribute_id","int(11)","YES","",null,"",
				   nr,"store_id","int(11)","NO","",null,""));
		conn.execute("create table matwo(`id` int, `entity_id` int, primary key (`id`), foreign key `myfk` (`id`, `entity_id`) references maone (`id`, `entity_id`)) engine=memory broadcast distribute");
		conn.assertResults("select constraint_name, constraint_type from information_schema.table_constraints where table_schema = '" + checkDDL.getDatabaseName() + "' and table_name = 'matwo'",
					br(nr,"PRIMARY","PRIMARY KEY",
					   nr,"matwo_ibfk_1","FOREIGN KEY"));
		conn.execute("alter table matwo drop foreign key `myfk`");
		conn.assertResults("select constraint_name, constraint_type from information_schema.table_constraints where table_schema = '" + checkDDL.getDatabaseName() + "' and table_name = 'matwo'",
				br(nr,"PRIMARY","PRIMARY KEY"));
		conn.execute("alter table matwo add key `added` (`id`)");
//		System.out.println(conn.printResults("show keys in matwo"));
		conn.assertResults("show keys in matwo",
				br(nr,"matwo",I_ZERO,"PRIMARY",I_ONE,"id","A",getIgnore(),null,null,"","BTREE","","",
				   nr,"matwo",I_ONE,"id",I_ONE,"id","A",getIgnore(),null,null,"","BTREE","","",
				   nr,"matwo",I_ONE,"id",new Integer(2),"entity_id","A",getIgnore(),null,null,"YES","BTREE","","",
				   nr,"matwo",I_ONE,"added",I_ONE,"id","A",getIgnore(),null,null,"","BTREE","",""
				   ));
		conn.execute("alter table matwo drop key `added`");
//		System.out.println(conn.printResults("show keys in matwo"));
		conn.assertResults("show keys in matwo",
				br(nr,"matwo",I_ZERO,"PRIMARY",I_ONE,"id","A",getIgnore(),null,null,"","BTREE","","",
				   nr,"matwo",I_ONE,"id",I_ONE,"id","A",getIgnore(),null,null,"","BTREE","","",
				   nr,"matwo",I_ONE,"id",new Integer(2),"entity_id","A",getIgnore(),null,null,"YES","BTREE","",""
				   ));
	}

	@Test
	public void testPE710() throws Throwable {
		conn.execute("create table pe710 (`keyname` varchar(100) NOT NULL, `title` text NOT NULL, `body` longtext NOT NULL, `imgurl` varchar(255) NOT NULL, `linkurl` varchar(255) NOT NULL, `footer` varchar(255) NOT NULL, `countrycode` varchar(2) NOT NULL, `gender` varchar(1) NOT NULL, `add_date` datetime NOT NULL, CONSTRAINT `keyname` UNIQUE KEY `keyname` (`keyname`, `add_date`)) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */");
		conn.assertResults("select column_name, ordinal_position from information_schema.columns where table_name='pe710' order by ordinal_position asc",
				br(nr,"keyname",1,
				   nr,"title",2,
				   nr,"body",3,
				   nr,"imgurl",4,
				   nr,"linkurl",5,
				   nr,"footer",6,
				   nr,"countrycode",7,
				   nr,"gender",8,
				   nr,"add_date",9
				   ));
		conn.execute("ALTER TABLE `pe710` ADD `subtitle` TEXT NULL AFTER `title`");
		conn.assertResults("select column_name, ordinal_position from information_schema.columns where table_name='pe710' order by ordinal_position",
				br(nr,"keyname",1,
				   nr,"title",2,
				   nr,"subtitle",3,
				   nr,"body",4,
				   nr,"imgurl",5,
				   nr,"linkurl",6,
				   nr,"footer",7,
				   nr,"countrycode",8,
				   nr,"gender",9,
				   nr,"add_date",10
				   ));
		conn.execute("ALTER TABLE `pe710` ADD `first_col` INT NOT NULL FIRST");
		conn.assertResults("select column_name, ordinal_position from information_schema.columns where table_name='pe710' order by ordinal_position",
				br(nr,"first_col",1,
				   nr,"keyname",2,
				   nr,"title",3,
				   nr,"subtitle",4,				   
				   nr,"body",5,
				   nr,"imgurl",6,
				   nr,"linkurl",7,
				   nr,"footer",8,
				   nr,"countrycode",9,
				   nr,"gender",10,
				   nr,"add_date",11
				   ));
		try {
			conn.execute("ALTER TABLE `pe710` ADD `multi1` INT NOT NULL, `multi2` INT NOT NULL AFTER `footer`");
			fail("Should throw exception");
		} catch (PEException e) {
			// test worked
		}

	}

	@Test
	public void testPE1335() throws Throwable {
		conn.execute("CREATE TABLE `pe1335` ("
				+"`idd` int(11) NOT NULL,`lm` datetime DEFAULT NULL,"
				+"`t` varchar(100) DEFAULT NULL,`orn` varchar(50) NOT NULL,`itn` varchar(100) NOT NULL,"
				+"`sk` varchar(100) DEFAULT NULL,`qf` int(11) DEFAULT NULL,`qu` int(11) DEFAULT NULL,"
				+"`sos` varchar(50) DEFAULT NULL,`fs` varchar(50) DEFAULT NULL,`mm` varchar(255) DEFAULT NULL,"
				+"`abn` varchar(100) DEFAULT NULL,`cn` varchar(50) DEFAULT NULL,`sd` datetime DEFAULT NULL,"
				+"`st` int(2) DEFAULT 0,KEY `orn` (`orn`)) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve BROADCAST DISTRIBUTE */");
		conn.assertResults("select column_name, ordinal_position from information_schema.columns where table_name='pe1335' order by ordinal_position asc",
				br(nr,"idd",1,
						nr,"lm",2,
						nr,"t",3,
						nr,"orn",4,
						nr,"itn",5,
						nr,"sk",6,
						nr,"qf",7,
						nr,"qu",8,
						nr,"sos",9,
						nr,"fs",10,
						nr,"mm",11,
						nr,"abn",12,
						nr,"cn",13,
						nr,"sd",14,
						nr,"st",15
						));
		conn.execute("ALTER TABLE `pe1335` ADD COLUMN `id` INT AUTO_INCREMENT PRIMARY KEY FIRST");
		conn.assertResults("select column_name, ordinal_position from information_schema.columns where table_name='pe1335' order by ordinal_position",
				br(nr,"id",1,
						nr,"idd",2,
						nr,"lm",3,
						nr,"t",4,
						nr,"orn",5,
						nr,"itn",6,
						nr,"sk",7,
						nr,"qf",8,
						nr,"qu",9,
						nr,"sos",10,
						nr,"fs",11,
						nr,"mm",12,
						nr,"abn",13,
						nr,"cn",14,
						nr,"sd",15,
						nr,"st",16
						));
		conn.assertResults("show keys in `pe1335`",
				br(nr,"pe1335",I_ONE,"orn",I_ONE,"orn","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1335",I_ZERO,"PRIMARY",I_ONE,"id","A",ignore,null,null,"","BTREE","",""));
	}
	
	@Test
	public void testAlterFromDifferentDB() throws Throwable {
		conn.execute("create table nonlocal (`id` int, `fid` int, primary key (id))");
		conn.execute("use " + oDDL.getDatabaseName());
		conn.execute("alter table " + checkDDL.getDatabaseName() + ".nonlocal add column `sid` int");
		conn.execute("use " + checkDDL.getDatabaseName());
		conn.assertResults("select column_name from information_schema.columns where table_name = 'nonlocal'",
				br(nr,"id",nr,"fid",nr,"sid"));
	}
	
	@Test
	public void testPE1276() throws Throwable {
        final NativeCharSetCatalog supportedCharsets = Singletons.require(NativeCharSetCatalog.class);
		final NativeCharSet utf8 = supportedCharsets.findCharSetByName("UTF8");
		final NativeCharSet ascii = supportedCharsets.findCharSetByName("ASCII");
		final NativeCharSet latin1 = supportedCharsets.findCharSetByName("LATIN1");

		executeAlterCharsetCollateTest("pe1276_charset", ascii.getName(), null);
		executeAlterCharsetCollateTest("pe1276_collate", null, Singletons.require(NativeCollationCatalog.class).findDefaultCollationForCharSet(latin1.getName()).getName());
		executeAlterCharsetCollateTest("pe1276_both", latin1.getName(), Singletons.require(NativeCollationCatalog.class).findDefaultCollationForCharSet(latin1.getName()).getName());

		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				executeAlterCharsetCollateTest("pe1276_ex1", utf8.getName(), Singletons.require(NativeCollationCatalog.class).findDefaultCollationForCharSet(latin1.getName()).getName());
			}
		}.assertError(SchemaException.class, MySQLErrors.collationCharsetMismatchFormatter, "latin1_swedish_ci", "utf8");

		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				executeAlterCharsetCollateTest("pe1276_ex2", "big5", null);
			}
		}.assertError(SchemaException.class, MySQLErrors.unknownCharacterSetFormatter, "big5");

		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				executeAlterCharsetCollateTest("pe1276_ex3", null, "latin2_czech_cs");
			}
		}.assertError(SchemaException.class, MySQLErrors.unknownCollationFormatter, "latin2_czech_cs");

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				executeAlterCharsetCollateTest("pe1276_ex4", null, null);
			}
		}.assertException(SchemaException.class, "Can't alter database 'pe1276_ex4'; syntax error");
	}

	private void executeAlterCharsetCollateTest(final String dbName, final String charSetName, final String collationName) throws Throwable {
		final StringBuilder alterStmt = new StringBuilder("alter database ").append(dbName);

		final Pair<String, String> defaults = getDefaultCharSetAndCollationNames();
		final Pair<String, String> expected = buildAndEmitCharSetCollateModifiers(charSetName, collationName, alterStmt);
		
		final String verifySql = "select schema_name, default_character_set_name, default_collation_name "
				+ "from information_schema.schemata where schema_name = '" + dbName + "'";
		conn.execute("create database " + dbName + " default persistent group " + sg.getName() + " using template " + TemplateMode.OPTIONAL);
		try {
			conn.assertResults(verifySql, br(nr, dbName, defaults.getFirst(), defaults.getSecond()));
			conn.execute(alterStmt.toString());
			conn.assertResults(verifySql, br(nr, dbName, expected.getFirst(), expected.getSecond()));
		} finally {
			try {
				conn.execute("drop database " + dbName);
			} catch (final Exception e) {
				// don't worry about this
			}
		}
	}

	@Test
	public void testPE1501() throws Throwable {
		final NativeCharSetCatalog supportedCharsets = Singletons.require(NativeCharSetCatalog.class);
		final NativeCharSet utf8 = supportedCharsets.findCharSetByName("UTF8");
		final NativeCharSet ascii = supportedCharsets.findCharSetByName("ASCII");
		final NativeCharSet latin1 = supportedCharsets.findCharSetByName("LATIN1");

		final String db = checkDDL.getDatabaseName();
		executeAlterCharsetCollateTest(db, "pe1501_charset", ascii.getName(), null);
		executeAlterCharsetCollateTest(db, "pe1501_collate", null, Singletons.require(NativeCollationCatalog.class)
				.findDefaultCollationForCharSet(latin1.getName()).getName());
		executeAlterCharsetCollateTest(db, "pe1501_both", latin1.getName(), Singletons.require(NativeCollationCatalog.class)
				.findDefaultCollationForCharSet(latin1.getName()).getName());
		executeAlterCharsetCollateTest(db, "pe1501_nothing", null, null);

		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				executeAlterCharsetCollateTest(db, "pe1501_ex1", utf8.getName(), Singletons.require(NativeCollationCatalog.class)
						.findDefaultCollationForCharSet(latin1.getName()).getName());
			}
		}.assertError(SchemaException.class, MySQLErrors.collationCharsetMismatchFormatter, "latin1_swedish_ci", "utf8");

		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				executeAlterCharsetCollateTest(db, "pe1501_ex2", "big5", null);
			}
		}.assertError(SchemaException.class, MySQLErrors.unknownCharacterSetFormatter, "big5");

		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				executeAlterCharsetCollateTest(db, "pe1501_ex3", null, "latin2_czech_cs");
			}
		}.assertError(SchemaException.class, MySQLErrors.unknownCollationFormatter, "latin2_czech_cs");
	}

	private void executeAlterCharsetCollateTest(final String dbName, final String tableName, final String charSetName, final String collationName) throws Throwable {
		final StringBuilder alterStmt = new StringBuilder("alter table ").append(tableName);

		final Pair<String, String> defaults = getDefaultCharSetAndCollationNames();
		final Pair<String, String> expected = buildAndEmitCharSetCollateModifiers(charSetName, collationName, alterStmt);
		
		final String verifySql = "select table_schema, table_name, table_collation "
				+ "from information_schema.tables where table_schema = '" + dbName + "' and table_name = '" + tableName + "'";
		conn.execute("create table `" + dbName + "`.`" + tableName + "` (id int not null auto_increment, value text not null)");
		try {
			conn.assertResults(verifySql, br(nr, dbName, tableName, defaults.getSecond()));
			conn.execute(alterStmt.toString());
			conn.assertResults(verifySql, br(nr, dbName, tableName, expected.getSecond()));
		} finally {
			try {
				conn.execute("drop table `" + dbName + "`.`" + tableName + "`");
			} catch (final Exception e) {
				// don't worry about this
			}
		}
	}

	@Test
	public void testPE768Exceptions() throws Throwable {
		conn.execute("create table pe768_ex (a INT NOT NULL, b INT NOT NULL, c INT NOT NULL, PRIMARY KEY(a))");
		conn.execute("insert into pe768_ex values (1, 2, 3), (4, 5, 6), (7, 8, 9)");

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table pe768_ex modify d INT NOT NULL first");
			}
		}.assertException(SchemaException.class, "Unknown column 'd' in 'pe768_ex'");

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table pe768_ex modify c INT NOT NULL after c");
			}
		}.assertException(PEException.class, "Unknown column 'c' in 'pe768_ex'");

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table pe768_ex change d e INT NOT NULL");
			}
		}.assertException(SchemaException.class, "Unknown column 'd' in 'pe768_ex'");

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table pe768_ex change c d INT NOT NULL after c");
			}
		}.assertException(PEException.class, "Unknown column 'c' in 'pe768_ex'");

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table pe768_ex change a b INT NOT NULL after a");
			}
		}.assertException(PEException.class, "Unknown column 'a' in 'pe768_ex'");
	}

	@Test
	public void testPE1480Exceptions() throws Throwable {
		conn.execute("create table pe1480_ex (a INT NOT NULL, b INT NOT NULL, c INT NOT NULL, PRIMARY KEY(a))");
		conn.execute("insert into pe1480_ex values (1, 2, 3), (4, 5, 6), (7, 8, 9)");

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table pe1480_ex change c d INT, add e INT after d, change e f INT first");
			}
		}.assertException(SchemaException.class, "Unknown column 'e' in 'pe1480_ex'");

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table pe1480_ex change c d INT, change d e INT");
			}
		}.assertException(SchemaException.class, "Unknown column 'd' in 'pe1480_ex'");
	}

	@Test
	public void testPE1506() throws Throwable {
		conn.execute("CREATE TABLE `Account` (  `id` bigint(20) NOT NULL AUTO_INCREMENT,  `accountManager` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,  `deClientId` bigint(20) NOT NULL,  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,  `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,  `clientGroup_id` bigint(20) DEFAULT NULL,  PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */");
		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("alter table Account add constraint UK_2imnf6c1l1o06tdcq7mw468vn unique (name, clientGroup_id)");
			}
		}.assertException(PESQLException.class, "(1071: 42000) Specified key was too long; max key length is 767 bytes", true);
	}

	@Test
	public void testTemplateModes() throws Throwable {
		try {
			conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.OPTIONAL));
			conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.REQUIRED));
			conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.STRICT));

			new ExpectedSqlErrorTester() {
				@Override
				public void test() throws Throwable {
					conn.execute("alter dve set " + VariableConstants.TEMPLATE_MODE_NAME + " = 'non_existing_mode'");
				}
			}.assertError(SchemaException.class, MySQLErrors.wrongValueForVariable, VariableConstants.TEMPLATE_MODE_NAME, "non_existing_mode");

		} finally {
			conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.REQUIRED));
		}
	}

	@Test
	public void testPE855() throws Throwable {
		final String dbName = "pe855db";
		final String groupName = sg.getName();

		final TemplateBuilder template1 = new TemplateBuilder("pe855temp1");
		template1.withRequirement(template1.toCreateRangeStatement("r3", groupName, Collections.singleton(buildTypeFromNative(MysqlType.INT))));
		template1.withTable("t1", ModelType.BROADCAST.value());
		template1.withTable("t2", ModelType.RANDOM.value());
		template1.withRangeTable("t3", "r3", "id");

		final TemplateBuilder template2 = new TemplateBuilder("pe855temp2");
		template2.withRequirement(template1.toCreateRangeStatement("r3", groupName, Collections.singleton(buildTypeFromNative(MysqlType.INT))));
		template2.withTable("t1", ModelType.RANDOM.value());
		template2.withRangeTable("t2", "r3", "id");
		template2.withTable("t3", ModelType.BROADCAST.value());
		template2.withTable("non_existing_table", ModelType.RANDOM.value());

		final TemplateBuilder template3 = new TemplateBuilder("pe855temp3", "pe855temp3");

		final TemplateBuilder template4 = new TemplateBuilder("pe855temp4");
		template4.withTable("t4", ModelType.BROADCAST.value());
		template4.withRangeTable("t5", "r3", "id");

		conn.execute(template1.toCreateStatement());
		conn.execute(template2.toCreateStatement());
		conn.execute(template3.toCreateStatement());
		conn.execute(template4.toCreateStatement());

		final String nonExistingTemplateName = "non_existing_template_name";

		/*
		 * Test creates.
		 */

		// This should work as the name matches the template.
		conn.execute("DROP DATABASE IF EXISTS " + template3.getName());
		try {
			conn.execute("CREATE DATABASE " + template3.getName() + " DEFAULT PERSISTENT GROUP " + groupName);
			assertTemplate(template3.getName(), template3.toTemplate(), TemplateMode.getCurrentDefault(), Collections.<String> emptySet());
		} finally {
			dropOnCleanup(conn, template3.getName());
		}

		// This should fail - template exists but has no match and the default
		// mode requires a template.
		conn.execute("DROP DATABASE IF EXISTS " + template1.getName());
		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("CREATE DATABASE " + template1.getName() + " DEFAULT PERSISTENT GROUP " + groupName);
			}
		}.assertException(SchemaException.class, "Template required, but not matched", true);

		// This should fail - no template to match the name and the default
		// mode requires a template.
		conn.execute("DROP DATABASE IF EXISTS " + nonExistingTemplateName);
		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("CREATE DATABASE " + nonExistingTemplateName + " DEFAULT PERSISTENT GROUP " + groupName);
			}
		}.assertException(SchemaException.class, "Template required, but not matched", true);

		try {
			conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.OPTIONAL));

			// No template to match the name. But should pass in the
			// OPTIONAL mode.
			conn.execute("DROP DATABASE IF EXISTS " + nonExistingTemplateName);
			try {
				conn.execute("CREATE DATABASE " + nonExistingTemplateName + " DEFAULT PERSISTENT GROUP " + groupName);
				assertTemplate(nonExistingTemplateName, null, TemplateMode.OPTIONAL, Collections.<String> emptySet());
			} finally {
				dropOnCleanup(conn, nonExistingTemplateName);
			}

			// Although, still in the OPTIONAL mode, we should fail here
			// as a non-existing template was explicitly specified.
			conn.execute("DROP DATABASE IF EXISTS " + nonExistingTemplateName);
			new ExpectedExceptionTester() {
				@Override
				public void test() throws Throwable {
					conn.execute("CREATE DATABASE " + nonExistingTemplateName + " DEFAULT PERSISTENT GROUP " + groupName + " USING TEMPLATE "
							+ nonExistingTemplateName);
				}
			}.assertException(SchemaException.class, "No such template: 'non_existing_template_name'", true);

			conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.STRICT));

			// This should fail - template exists but has no match.
			conn.execute("DROP DATABASE IF EXISTS " + template1.getName());
			new ExpectedExceptionTester() {
				@Override
				public void test() throws Throwable {
					conn.execute("CREATE DATABASE " + template1.getName() + " DEFAULT PERSISTENT GROUP " + groupName);
				}
			}.assertException(SchemaException.class, "Template required, but not matched", true);

		} finally {
			conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.REQUIRED));
		}

		/*
		 * Test alters.
		 */

		conn.execute("DROP DATABASE IF EXISTS " + dbName);
		try {
			conn.execute("CREATE DATABASE " + dbName + " DEFAULT PERSISTENT GROUP " + groupName + " USING TEMPLATE " + template1.getName());
			conn.execute("USE " + dbName);

			conn.execute("CREATE TABLE t1 (id INT NOT NULL, data TEXT)");
			conn.execute("CREATE TABLE t2 (id INT NOT NULL, data TEXT)");
			conn.execute("CREATE TABLE t3 (id INT NOT NULL, data TEXT)");
			assertTemplate(dbName, template1.toTemplate(), TemplateMode.getCurrentDefault(), Collections.<String> emptySet());

			// OK, we are in a non-STRICT mode, make it Random.
			conn.execute("CREATE TABLE t4 (id INT NOT NULL, data TEXT)");
			assertTableDistributionModel(dbName, "t4", ModelType.RANDOM, Collections.<String> emptyList());

			conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE " + template2.getName() + " STRICT");
			assertTemplate(dbName, template2.toTemplate(), TemplateMode.STRICT, Collections.singleton("non_existing_table"));

			// Fail, we are in the STRICT mode.
			new ExpectedExceptionTester() {
				@Override
				public void test() throws Throwable {
					conn.execute("CREATE TABLE t5 (id INT NOT NULL, data TEXT)");
				}
			}.assertException(SchemaException.class, "com.tesora.dve.sql.SchemaException: No matching template found for table t5", true);

			// Template mode changes, table distributions don't.
			conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE OPTIONAL");
			assertTemplate(dbName, null, TemplateMode.OPTIONAL, Collections.<String> emptySet());
			assertDatabaseDistribution(dbName, template2.toTemplate(), Collections.singleton("non_existing_table"));
			assertTableDistributionModel(dbName, "t4", ModelType.RANDOM, Collections.<String> emptyList());

			// OK, we are in a non-STRICT mode, make it Random.
			conn.execute("CREATE TABLE t5 (id INT NOT NULL, data TEXT)");
			assertTableDistributionModel(dbName, "t5", ModelType.RANDOM, Collections.<String> emptyList());

			// OK, ALTER distribution of the two added tables. The other tables
			// should keep their distribution models from template #2.
			// No mode => use default.
			conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE " + template4.getName());
			assertTemplate(dbName, template4.toTemplate(), TemplateMode.getCurrentDefault(), Collections.<String> emptySet());
			assertDatabaseDistribution(dbName, template2.toTemplate(), Collections.singleton("non_existing_table"));

			final TemplateBuilder template5 = new TemplateBuilder("pe855temp5", dbName);
			template5.withTable("t1", ModelType.BROADCAST.value());
			template5.withTable("t2", ModelType.BROADCAST.value());
			template5.withTable("t3", ModelType.BROADCAST.value());
			template5.withTable("t4", ModelType.BROADCAST.value());
			template5.withTable("t5", ModelType.BROADCAST.value());
			conn.execute(template5.toCreateStatement());

			// OK, the template should get matched.
			conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE OPTIONAL");
			assertTemplate(dbName, template5.toTemplate(), TemplateMode.OPTIONAL, Collections.<String> emptySet());

			// Fail - such template does not exist.
			// No mode => use default.
			new ExpectedExceptionTester() {
				@Override
				public void test() throws Throwable {
					conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE " + nonExistingTemplateName);
				}
			}.assertException(SchemaException.class, "No such template: 'non_existing_template_name'", true);

			// Although, in the OPTIONAL mode, we should still fail
			// as a non-existing template was explicitly specified.
			try {
				conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.OPTIONAL));
				new ExpectedExceptionTester() {
					@Override
					public void test() throws Throwable {
						conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE " + nonExistingTemplateName);
					}
				}.assertException(SchemaException.class, "No such template: 'non_existing_template_name'", true);
			} finally {
				conn.execute(SchemaTest.buildAlterTemplateModeStmt(TemplateMode.REQUIRED));
			}

			/*
			 * Sanity checks.
			 */

			// Redundant template specification in the OPTIONAL mode.
			new ExpectedExceptionTester() {
				@Override
				public void test() throws Throwable {
					conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE " + template2.getName() + " OPTIONAL");
				}
			}.assertException(SchemaException.class, "Redundant template specification '" + template2.getName()
					+ "' for template_mode 'OPTIONAL'; syntax error", true);

			// Redundant mode specification in the OPTIONAL mode.
			new ExpectedExceptionTester() {
				@Override
				public void test() throws Throwable {
					conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE OPTIONAL REQUIRED");
				}
			}.assertException(SchemaException.class, "Redundant mode specification 'REQUIRED'; syntax error", true);

			// Redundant mode specification.
			new ExpectedExceptionTester() {
				@Override
				public void test() throws Throwable {
					conn.execute("ALTER DATABASE " + dbName + " USING TEMPLATE REQUIRED STRICT");
				}
			}.assertException(SchemaException.class, "Redundant mode specification 'STRICT'; syntax error", true);

		} finally {
			dropOnCleanup(conn, dbName);
		}
	}

	private static void dropOnCleanup(final ConnectionResource connection, final String dbName) throws Throwable {
		try {
			connection.execute("DROP DATABASE " + dbName);
		} catch (final Exception e) {
			// Ignore on cleanup.
		}
	}

	private void assertTemplate(final String dbName, final Template template, final TemplateMode mode, final Set<String> ignoredTables) throws Throwable {
		conn.assertResults("SELECT template, template_mode FROM INFORMATION_SCHEMA.SCHEMATA WHERE schema_name = '" + dbName + "'",
				br(nr, (template != null) ? template.getName() : null, mode.toString()));
		if (template != null) {
			assertDatabaseDistribution(dbName, template, ignoredTables);
		}
	}

	private void assertDatabaseDistribution(final String dbName, final Template template, final Set<String> ignoredTables) throws Throwable {
		for (final TableTemplateType item : template.getTabletemplate()) {
			final String tableName = item.getMatch();
			if (!ignoredTables.contains(tableName)) {
				final ModelType tableModel = item.getModel();
				final List<String> dvColumnNames = item.getColumn();
				assertTableDistributionModel(dbName, tableName, tableModel, dvColumnNames);
			}
		}
	}

	private void assertTableDistributionModel(final String dbName, final String tableName, final ModelType tableModel, final List<String> dvColumnNames)
			throws Throwable {
		conn.assertResults("SELECT column_name, model_type FROM INFORMATION_SCHEMA.DISTRIBUTIONS"
				+ " WHERE ((database_name = '" + dbName + "') AND (table_name = '" + tableName + "'))",
				br(nr, (!dvColumnNames.isEmpty()) ? StringUtils.join(dvColumnNames, ',') : null, tableModel.value()));
	}

	private static Type buildTypeFromNative(final MysqlType type) {
		return BasicType.buildType(new MysqlNativeType(type), 0, Collections.<TypeModifier> emptyList()).normalize();
	}

	@Test
	public void testPE1404_PE1406() throws Throwable {
		conn.execute("CREATE TABLE `pe1404` ( `id` int NOT NULL,  `data` int DEFAULT 1, PRIMARY KEY (`id`)) ENGINE=MyISAM /*#dve  BROADCAST DISTRIBUTE */");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `id` int(11) NOT NULL,\n  `data` int(11) DEFAULT '1',\n  PRIMARY KEY (`id`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","","1",""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","",""));

		conn.execute("INSERT INTO `pe1404` (`id`) VALUES (1)");
		conn.assertResults("SELECT `id`, `data` FROM `pe1404` ORDER BY `id`",
				br(nr,1,1));
		
		// ALTER [COLUMN] col_name SET DEFAULT literal
		conn.execute("ALTER TABLE `pe1404` ALTER COLUMN `data` SET DEFAULT 99");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `id` int(11) NOT NULL,\n  `data` int(11) DEFAULT '99',\n  PRIMARY KEY (`id`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","","99",""));

		conn.execute("INSERT INTO `pe1404` (`id`) VALUES (2)");
		conn.assertResults("SELECT `id`, `data` FROM `pe1404` ORDER BY `id`",
				br(nr,1,1,
				   nr,2,99));
		
		// ADD [COLUMN] col_name column_definition
		conn.execute("ALTER TABLE `pe1404` ADD COLUMN `newcolumn` VARCHAR(255) NOT NULL");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `id` int(11) NOT NULL,\n  `data` int(11) DEFAULT '99',\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","","99","",
				   nr,"newcolumn","varchar(255)","NO","",null,""));

		// ADD [COLUMN] col_name column_definition FIRST
		conn.execute("ALTER TABLE `pe1404` ADD COLUMN `firstcolumn` int FIRST");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `firstcolumn` int(11) DEFAULT NULL,\n  `id` int(11) NOT NULL,\n  `data` int(11) DEFAULT '99',\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"firstcolumn","int(11)","YES","",null,"",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","","99","",
				   nr,"newcolumn","varchar(255)","NO","",null,""));
		
		// ADD [COLUMN] col_name column_definition AFTER col_name
		conn.execute("ALTER TABLE `pe1404` ADD COLUMN `afterdata` varchar(10) NOT NULL AFTER `data`");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `firstcolumn` int(11) DEFAULT NULL,\n  `id` int(11) NOT NULL,\n  `data` int(11) DEFAULT '99',\n  `afterdata` varchar(10) NOT NULL,\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"firstcolumn","int(11)","YES","",null,"",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","","99","",
				   nr,"afterdata","varchar(10)","NO","",null,"",
				   nr,"newcolumn","varchar(255)","NO","",null,""));

		// ADD {INDEX|KEY} [index_name] [index_type] (index_col_name,...) [index_option] ...
		conn.execute("ALTER TABLE `pe1404` ADD INDEX `index2` (`afterdata`)");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `firstcolumn` int(11) DEFAULT NULL,\n  `id` int(11) NOT NULL,\n  `data` int(11) DEFAULT '99',\n  `afterdata` varchar(10) NOT NULL,\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`afterdata`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"firstcolumn","int(11)","YES","",null,"",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","","99","",
				   nr,"afterdata","varchar(10)","NO","MUL",null,"",
				   nr,"newcolumn","varchar(255)","NO","",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404",1,"index2",1,"afterdata","A",ignore,null,null,"","BTREE","",""
				   ));
		
		// ADD FULLTEXT [INDEX|KEY] [index_name] (index_col_name,...) [index_option] ...
		conn.execute("ALTER TABLE `pe1404` ADD FULLTEXT INDEX `fulltextindex` (`newcolumn`)");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `firstcolumn` int(11) DEFAULT NULL,\n  `id` int(11) NOT NULL,\n  `data` int(11) DEFAULT '99',\n  `afterdata` varchar(10) NOT NULL,\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`afterdata`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"firstcolumn","int(11)","YES","",null,"",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","","99","",
				   nr,"afterdata","varchar(10)","NO","MUL",null,"",
				   nr,"newcolumn","varchar(255)","NO","MUL",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"index2",1,"afterdata","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","",""
				   
				   ));

		// ALTER [COLUMN] col_name DROP DEFAULT
		conn.execute("ALTER TABLE `pe1404` ALTER COLUMN `data` DROP DEFAULT");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `firstcolumn` int(11) DEFAULT NULL,\n  `id` int(11) NOT NULL,\n  `data` int(11),\n  `afterdata` varchar(10) NOT NULL,\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`afterdata`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"firstcolumn","int(11)","YES","",null,"",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata","varchar(10)","NO","MUL",null,"",
				   nr,"newcolumn","varchar(255)","NO","MUL",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404",1,"index2",1,"afterdata","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","",""
				   ));

		conn.execute("INSERT INTO `pe1404` (`id`) VALUES (3)");
		conn.assertResults("SELECT `id`, `data` FROM `pe1404` ORDER BY `id`",
				br(nr,1,1,
				   nr,2,99,
				   nr,3,null));

		// CHANGE [COLUMN] old_col_name new_col_name column_definition
		conn.execute("ALTER TABLE `pe1404` CHANGE COLUMN `firstcolumn` `oldfirstcolumn` int NOT NULL DEFAULT 5");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `oldfirstcolumn` int(11) NOT NULL DEFAULT '5',\n  `id` int(11) NOT NULL,\n  `data` int(11),\n  `afterdata` varchar(10) NOT NULL,\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`afterdata`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"oldfirstcolumn","int(11)","NO","","5","",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata","varchar(10)","NO","MUL",null,"",
				   nr,"newcolumn","varchar(255)","NO","MUL",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"index2",1,"afterdata","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","",""
				   ));
		
		// CHANGE [COLUMN] old_col_name new_col_name column_definition FIRST
		conn.execute("ALTER TABLE `pe1404` CHANGE COLUMN `afterdata` `newfirstcolumn` int NOT NULL FIRST");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newfirstcolumn` int(11) NOT NULL,\n  `oldfirstcolumn` int(11) NOT NULL DEFAULT '5',\n  `id` int(11) NOT NULL,\n  `data` int(11),\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`newfirstcolumn`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newfirstcolumn","int(11)","NO","",null,"",
				   nr,"oldfirstcolumn","int(11)","NO","","5","",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"newcolumn","varchar(255)","NO","MUL",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","",""
				   ));
		
		// CHANGE [COLUMN] old_col_name new_col_name column_definition AFTER col_name
		conn.execute("ALTER TABLE `pe1404` CHANGE COLUMN `oldfirstcolumn` `afterdata2` varchar(255) AFTER `data`");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newfirstcolumn` int(11) NOT NULL,\n  `id` int(11) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`newfirstcolumn`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newfirstcolumn","int(11)","NO","MUL",null,"",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,"",
				   nr,"newcolumn","varchar(255)","NO","MUL",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","",""
				   
				   ));
		
		// MODIFY [COLUMN] col_name column_definition
		conn.execute("ALTER TABLE `pe1404` MODIFY COLUMN `newfirstcolumn` varchar(255) DEFAULT 50");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newfirstcolumn` varchar(255) DEFAULT '50',\n  `id` int(11) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  `newcolumn` varchar(255) NOT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`newfirstcolumn`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newfirstcolumn","varchar(255)","YES","","50","",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,"",
				   nr,"newcolumn","varchar(255)","NO","MUL",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"YES","BTREE","","",
						nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","",""
				   ));

		// MODIFY [COLUMN] col_name column_definition FIRST
		conn.execute("ALTER TABLE `pe1404` MODIFY COLUMN `newcolumn` VARCHAR(256) FIRST");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newcolumn` varchar(256) DEFAULT NULL,\n  `newfirstcolumn` varchar(255) DEFAULT '50',\n  `id` int(11) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`newfirstcolumn`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newcolumn","varchar(256)","YES","",null,"",
				   nr,"newfirstcolumn","varchar(255)","YES","MUL","50","",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"YES","BTREE","","",
						nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"YES","FULLTEXT","",""
				   ));

		// MODIFY [COLUMN] col_name column_definition AFTER col_name
		conn.execute("ALTER TABLE `pe1404` MODIFY COLUMN `newcolumn` VARCHAR(300) NOT NULL AFTER `id`");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newfirstcolumn` varchar(255) DEFAULT '50',\n  `id` int(11) NOT NULL,\n  `newcolumn` varchar(300) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`newfirstcolumn`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newfirstcolumn","varchar(255)","YES","MUL","50","",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"newcolumn","varchar(300)","NO","",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"YES","BTREE","","",
						nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","",""
				   ));

		// DROP PRIMARY KEY
		conn.execute("ALTER TABLE `pe1404` DROP PRIMARY KEY");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newfirstcolumn` varchar(255) DEFAULT '50',\n  `id` int(11) NOT NULL,\n  `newcolumn` varchar(300) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  KEY `index2` (`newfirstcolumn`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newfirstcolumn","varchar(255)","YES","MUL","50","",
				   nr,"id","int(11)","NO","",null,"",
				   nr,"newcolumn","varchar(300)","NO","MUL",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"YES","BTREE","","",
						nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","",""
				   ));

		// ADD [CONSTRAINT [symbol]] PRIMARY KEY [index_type] (index_col_name,...) [index_option] ...
		conn.execute("ALTER TABLE `pe1404` ADD PRIMARY KEY (`id`)");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newfirstcolumn` varchar(255) DEFAULT '50',\n  `id` int(11) NOT NULL,\n  `newcolumn` varchar(300) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`newfirstcolumn`),\n  FULLTEXT KEY `fulltextindex` (`newcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newfirstcolumn","varchar(255)","YES","MUL","50","",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"newcolumn","varchar(300)","NO","MUL",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,""));
		//System.out.println(conn.printResults("show index from pe1404"));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"YES","BTREE","","",
						nr,"pe1404",1,"fulltextindex",1,"newcolumn",null,ignore,null,null,"","FULLTEXT","","",
						nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","",""
				   ));

		// DROP {INDEX|KEY} index_name
		conn.execute("ALTER TABLE `pe1404` DROP INDEX `fulltextindex`");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newfirstcolumn` varchar(255) DEFAULT '50',\n  `id` int(11) NOT NULL,\n  `newcolumn` varchar(300) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  PRIMARY KEY (`id`),\n  KEY `index2` (`newfirstcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newfirstcolumn","varchar(255)","YES","MUL","50","",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"newcolumn","varchar(300)","NO","",null,"",
				   nr,"data","int(11)","YES","",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"YES","BTREE","","",
						nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","",""
				   ));

		// ADD [CONSTRAINT [symbol]] UNIQUE [INDEX|KEY] [index_name] [index_type] (index_col_name,...) [index_option] ...
		conn.execute("ALTER TABLE `pe1404` ADD UNIQUE INDEX `index1` (`data`)");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `newfirstcolumn` varchar(255) DEFAULT '50',\n  `id` int(11) NOT NULL,\n  `newcolumn` varchar(300) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`data`),\n  KEY `index2` (`newfirstcolumn`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"newfirstcolumn","varchar(255)","YES","MUL","50","",
				   nr,"id","int(11)","NO","PRI",null,"",
				   nr,"newcolumn","varchar(300)","NO","",null,"",
				   nr,"data","int(11)","YES","UNI",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,""));

		conn.assertResults("SHOW INDEX FROM `pe1404`",
				br(nr,"pe1404",1,"index2",1,"newfirstcolumn","A",ignore,null,null,"YES","BTREE","","",
					nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
					nr,"pe1404",0,"index1",1,"data","A",ignore,null,null,"YES","BTREE","",""
				   ));

		// DROP [COLUMN] col_name
		conn.execute("ALTER TABLE `pe1404` DROP COLUMN `newfirstcolumn`");
		conn.assertResults("SHOW CREATE TABLE `pe1404`",
				br(nr,"pe1404","CREATE TABLE `pe1404` (\n  `id` int(11) NOT NULL,\n  `newcolumn` varchar(300) NOT NULL,\n  `data` int(11),\n  `afterdata2` varchar(255) DEFAULT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`data`)\n) ENGINE=MyISAM DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"newcolumn","varchar(300)","NO","",null,"",
				   nr,"data","int(11)","YES","UNI",null,"",
				   nr,"afterdata2","varchar(255)","YES","",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404`", 
				br(nr,"pe1404",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
					nr,"pe1404",0,"index1",1,"data","A",ignore,null,null,"YES","BTREE","",""
				   ));
		
		// check the FK
		conn.execute("CREATE TABLE `pe1404_parent` ( `id` int NOT NULL, `alt_id` int NOT NULL, PRIMARY KEY (`id`), UNIQUE INDEX `index1` (alt_id)) /*#dve  BROADCAST DISTRIBUTE */");
		conn.assertResults("SHOW CREATE TABLE `pe1404_parent`",
				br(nr,"pe1404_parent","CREATE TABLE `pe1404_parent` (\n  `id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_parent`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_parent`", 
				br(nr,"pe1404_parent",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
					nr,"pe1404_parent",0,"index1",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));

		conn.execute("CREATE TABLE `pe1404_middle` ( `id` int NOT NULL,  `parent_id` int NOT NULL, `alt_id` int NOT NULL, PRIMARY KEY (`id`), UNIQUE INDEX `index1` (parent_id), UNIQUE INDEX `index2` (alt_id), FOREIGN KEY `middle_to_parent` (`parent_id`) REFERENCES `pe1404_parent` (`alt_id`)) /*#dve  BROADCAST DISTRIBUTE */");
		conn.assertResults("SHOW CREATE TABLE `pe1404_middle`",
				br(nr,"pe1404_middle","CREATE TABLE `pe1404_middle` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`),\n  UNIQUE KEY `index2` (`alt_id`),\n  CONSTRAINT `pe1404_middle_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `pe1404_parent` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_middle`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_middle`", 
				br(nr,"pe1404_middle",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404_middle",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_middle",0,"index2",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));

		conn.execute("CREATE TABLE `pe1404_child` ( `id` int NOT NULL,  `parent_id` int NOT NULL, `alt_id` int NOT NULL, PRIMARY KEY (`id`), UNIQUE INDEX `index1` (parent_id), UNIQUE INDEX `index2` (alt_id)) /*#dve  BROADCAST DISTRIBUTE */");
		conn.assertResults("SHOW CREATE TABLE `pe1404_child`",
				br(nr,"pe1404_child","CREATE TABLE `pe1404_child` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`),\n  UNIQUE KEY `index2` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_child`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_child`", 
				br(nr,"pe1404_child",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
					nr,"pe1404_child",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_child",0,"index2",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));
		
		// ADD [CONSTRAINT [symbol]] FOREIGN KEY [index_name] (index_col_name,...) reference_definition
		conn.execute("ALTER TABLE `pe1404_child` ADD FOREIGN KEY `child_to_middle` (`parent_id`) REFERENCES `pe1404_middle` (`alt_id`)");
		conn.assertResults("SHOW CREATE TABLE `pe1404_child`",
				br(nr,"pe1404_child","CREATE TABLE `pe1404_child` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`),\n  UNIQUE KEY `index2` (`alt_id`),\n  CONSTRAINT `pe1404_child_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `pe1404_middle` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_child`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_child`", 
				br(nr,"pe1404_child",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404_child",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_child",0,"index2",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));
		
		// DROP FOREIGN KEY fk_symbol (foreign key constraint has to be dropped by constraint name and not the index name)
		conn.execute("ALTER TABLE `pe1404_child` DROP FOREIGN KEY `pe1404_child_ibfk_1`");
		conn.assertResults("SHOW CREATE TABLE `pe1404_child`",
				br(nr,"pe1404_child","CREATE TABLE `pe1404_child` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`),\n  UNIQUE KEY `index2` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_child`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_child`", 
				br(nr,"pe1404_child",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404_child",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_child",0,"index2",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));

		// add the FK again
		conn.execute("ALTER TABLE `pe1404_child` ADD FOREIGN KEY `child_to_middle` (`parent_id`) REFERENCES `pe1404_middle` (`alt_id`)");
		conn.assertResults("SHOW CREATE TABLE `pe1404_child`",
				br(nr,"pe1404_child","CREATE TABLE `pe1404_child` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`),\n  UNIQUE KEY `index2` (`alt_id`),\n  CONSTRAINT `pe1404_child_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `pe1404_middle` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_child`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_child`", 
				br(nr,"pe1404_child",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404_child",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_child",0,"index2",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));
		
		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("ALTER TABLE `pe1404_middle` DROP COLUMN `parent_id`");
			}
		}.assertException(SchemaException.class, "Cannot drop column 'parent_id' because it is part of foreign key 'middle_to_parent'", true);
		conn.assertResults("SHOW CREATE TABLE `pe1404_middle`",
				br(nr,"pe1404_middle","CREATE TABLE `pe1404_middle` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`),\n  UNIQUE KEY `index2` (`alt_id`),\n  CONSTRAINT `pe1404_middle_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `pe1404_parent` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_middle`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_middle`", 
				br(nr,"pe1404_middle",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404_middle",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_middle",0,"index2",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));

		new ExpectedExceptionTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("ALTER TABLE `pe1404_middle` DROP COLUMN `alt_id`");
			}
		}.assertException(SchemaException.class, "Cannot drop column 'alt_id' because it is part of foreign key 'child_to_middle'", true);
		conn.assertResults("SHOW CREATE TABLE `pe1404_middle`",
				br(nr,"pe1404_middle","CREATE TABLE `pe1404_middle` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`),\n  UNIQUE KEY `index2` (`alt_id`),\n  CONSTRAINT `pe1404_middle_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `pe1404_parent` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_middle`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_middle`", 
				br(nr,"pe1404_middle",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404_middle",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_middle",0,"index2",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));
		conn.assertResults("SHOW CREATE TABLE `pe1404_child`",
				br(nr,"pe1404_child","CREATE TABLE `pe1404_child` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`),\n  UNIQUE KEY `index2` (`alt_id`),\n  CONSTRAINT `pe1404_child_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `pe1404_middle` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_child`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_child`", 
				br(nr,"pe1404_child",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404_child",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_child",0,"index2",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));

		// drop a column of multipart key
		conn.execute("ALTER TABLE `pe1404_child` DROP FOREIGN KEY `pe1404_child_ibfk_1`");
		conn.execute("ALTER TABLE `pe1404_child` DROP INDEX `index1`");
		conn.execute("ALTER TABLE `pe1404_child` DROP INDEX `index2`");
		conn.execute("ALTER TABLE `pe1404_child` ADD UNIQUE INDEX `index1` (`parent_id`, `alt_id`)");
		conn.assertResults("SHOW CREATE TABLE `pe1404_child`",
				br(nr,"pe1404_child","CREATE TABLE `pe1404_child` (\n  `id` int(11) NOT NULL,\n  `parent_id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`parent_id`,`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_child`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"parent_id","int(11)","NO","UNI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_child`", 
				br(nr,"pe1404_child",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
						nr,"pe1404_child",0,"index1",1,"parent_id","A",ignore,null,null,"","BTREE","","",
				   nr,"pe1404_child",0,"index1",2,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));

		conn.execute("ALTER TABLE `pe1404_child` DROP COLUMN `parent_id`");
		conn.assertResults("SHOW CREATE TABLE `pe1404_child`",
				br(nr,"pe1404_child","CREATE TABLE `pe1404_child` (\n  `id` int(11) NOT NULL,\n  `alt_id` int(11) NOT NULL,\n  PRIMARY KEY (`id`),\n  UNIQUE KEY `index1` (`alt_id`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 /*#dve  BROADCAST DISTRIBUTE */"));
		conn.assertResults("DESCRIBE `pe1404_child`",
				br(nr,"id","int(11)","NO","PRI",null,"",
				   nr,"alt_id","int(11)","NO","UNI",null,""));
		conn.assertResults("SHOW INDEX FROM `pe1404_child`", 
				br(nr,"pe1404_child",0,"PRIMARY",1,"id","A",ignore,null,null,"","BTREE","","",
					nr,"pe1404_child",0,"index1",1,"alt_id","A",ignore,null,null,"","BTREE","",""
				   ));
	}

	@Test
	public void testPE1632() throws Throwable {
		conn.execute("DROP TABLE IF EXISTS pe1632;");
		conn.execute("SET SQL_MODE='TRADITIONAL'");

		// a field comment 1025 chars long
		new ExpectedSqlErrorTester() {
			@Override
			public void test() throws Throwable {
				conn.execute("CREATE TABLE t1 (c1 VARCHAR(10) NOT NULL COMMENT 'c1 comment', c2 INTEGER,"
						+ "c3 INTEGER COMMENT '0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123',"
						+ "c4 INTEGER, c5 INTEGER, c6 INTEGER, c7 INTEGER, INDEX i1 (c1) COMMENT 'i1 comment',INDEX i2(c2)) COMMENT='abc'");
			}
		}.assertError(SchemaException.class, MySQLErrors.tooLongTableFieldCommentFormatter, "c3", 1024L);
	}
	
	@Test
	public void testPE1671() throws Throwable {
		conn.execute("drop table if exists pe1671");
		conn.execute("create table pe1671(url_rewrite_id int unsigned not null auto_increment, request_path varchar(255) not null,  "
				+"target_path varchar(255) not null, is_system smallint unsigned not null, guid varchar(32) not null, "
				+"identifier varchar(255) not null, inc int unsigned not null, value_id int unsigned not null, "
				+"primary key (`url_rewrite_id`)) broadcast distribute");
		conn.execute("alter table pe1671 add column store_id smallint unsigned not null");
		conn.execute("alter table pe1671 add column entity_type smallint unsigned not null");
		conn.execute("alter table pe1671 add unique `snowflake` (request_path, store_id, entity_type)");
		conn.execute("alter table pe1671 drop index `snowflake`, add unique `snowflake` (request_path, store_id, entity_type)");
		conn.execute("alter table pe1671 drop index `snowflake`, add unique `snowflake` (request_path, store_id, entity_type)");
		conn.execute("alter table pe1671 drop index `snowflake`, add unique `snowflake` (request_path, store_id, entity_type)");
	}
	
}

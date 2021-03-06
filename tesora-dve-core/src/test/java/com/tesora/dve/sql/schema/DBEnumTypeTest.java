package com.tesora.dve.sql.schema;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tesora.dve.db.DBNative;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.tesora.dve.common.catalog.UserColumn;
import com.tesora.dve.db.NativeType;
import com.tesora.dve.db.NativeTypeCatalog;
import com.tesora.dve.db.mysql.MysqlNativeType.MysqlType;
import com.tesora.dve.server.connectionmanager.TestHost;
import com.tesora.dve.server.global.HostService;
import com.tesora.dve.singleton.Singletons;
import com.tesora.dve.sql.schema.modifiers.TypeModifier;
import com.tesora.dve.sql.schema.types.BasicType;
import com.tesora.dve.sql.schema.types.DBEnumType;
import com.tesora.dve.sql.schema.types.Type;
import com.tesora.dve.standalone.PETest;

public class DBEnumTypeTest extends PETest {

	@BeforeClass
	public static void setup() throws Exception {
		TestHost.startServicesTransient(PETest.class);
	}

	@AfterClass
	public static void teardown() throws Exception {
		TestHost.stopServices();
	}

	@Test
	public void testMakeAndBuild() {
		String[][][] data = getData();
		try {
			int dataRow = 0;
			for (String[][] rec : data) {
				dataRow ++;
				String msg = " for make for data row " + dataRow;
				String[] values = rec[0];
				String[] strMods = rec[1];
				String expBackTypeStr = rec[2][0];
				String sizingStr = rec[2][1];

				List<TypeModifier> mods = new ArrayList<TypeModifier>();
				boolean unsigned = false;
				if (strMods != null) {
					for (String strMod : strMods) {
						if ("UNSIGNED".equalsIgnoreCase(strMod))
							unsigned = true;
					}
				}
				NativeTypeCatalog typeCatalog = Singletons.require(DBNative.class).getTypeCatalog();
				NativeType expBackType = BasicType.lookupNativeType(expBackTypeStr,typeCatalog);
				Integer expSta = Integer.valueOf(sizingStr);
				String typeName = MysqlType.ENUM.toString();
				StringBuilder universe = new StringBuilder();
				boolean first = true;
				for (String value : values) {
					if (!first)
						universe.append(",");
					universe.append(value);
					first = false;
				}
				String nativeTypeName = String.format("%s(%s)",typeName,universe);
//				nativeTypeName.append(")");
				// test make
				Type newType = DBEnumType.makeFromStrings(false, Arrays.asList(values), mods,typeCatalog);
				assertEquals("Should get the right type" + msg, nativeTypeName.toString(), newType.getTypeName());
				assertEquals("Should get the right sizing" + msg, false, newType.declUsesSizing());
				assertEquals("Should get the right sizing" + msg, expSta.intValue(), newType.getSize());
				assertEquals("Should get the right backing type" + msg, expBackType, newType.getBaseType());
				// test build
				UserColumn uc = new UserColumn();
				uc.setTypeName(typeName);
				uc.setESUniverse(universe.toString());
				uc.setUnsigned(unsigned);
				newType = DBEnumType.buildType(uc, typeCatalog);
				msg = " for build for data row " + dataRow;
				assertEquals("Should get the right type" + msg, nativeTypeName.toString(), newType.getTypeName());
				assertEquals("Should get the right sizing" + msg, false, newType.declUsesSizing());
				assertEquals("Should get the right sizing" + msg, expSta.intValue(), newType.getSize());
				assertEquals("Should get the right backing type" + msg, expBackType, newType.getBaseType());
			}
		} catch (Exception e) {
			failWithStackTrace(e);
		}

	}
	
	private String[][][] getData() {
		List<String> lottaValues = new ArrayList<String>();
		for (int count = 0; count < 300	; count++) {
			StringBuilder sb = new StringBuilder("'").append(count).append("'");
			lottaValues.add(sb.toString());
		}
		// format is {enum values}, {modifiers}, {expected backing, sizing}
		String[][][] data = {
				{ { "'A'", "'B'", "'C'", "'D'" },
				{ "unsigned" },
				{ MysqlType.TINYINT.toString(), "1" } },
				{ { "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'",
						"'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB'",
						"'CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC'",
						"'DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD'",
						"'EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE'",
						"'FFFFFFFFFFFFFFFFFFFFFFFFFFFFFF'",
						"'GGGGGGGGGGGGGGGGGGGGGGGGGGGGGG'",
						"'HHHHHHHHHHHHHHHHHHHHHHHHHHHHHH'",
						"'IIIIIIIIIIIIIIIIIIIIIIIIIIIIII'",
						"'JJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ'" },
				{},
				{ MysqlType.TINYINT.toString(), "1" } },
				{ lottaValues.toArray(new String[0]),
				{},
				{ MysqlType.SMALLINT.toString(), "2" } },
		};
		return data;
	}

}

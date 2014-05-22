package com.tesora.dve.sql.transform;

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

import org.junit.Ignore;
import org.junit.Test;

import com.tesora.dve.distribution.BroadcastDistributionModel;
import com.tesora.dve.distribution.StaticDistributionModel;
import com.tesora.dve.resultset.ColumnInfo;
import com.tesora.dve.resultset.ProjectionInfo;
import com.tesora.dve.sql.transexec.TransientExecutionEngine;
import com.tesora.dve.sql.schema.PEPersistentGroup;
import com.tesora.dve.sql.schema.SchemaContext;
import com.tesora.dve.sql.statement.dml.SelectStatement;
import com.tesora.dve.sql.transform.execution.ExecutionPlan;
import com.tesora.dve.sql.transform.execution.ExecutionType;
import com.tesora.dve.sql.util.TestName;

public class JoinTransformTest extends TransformTest {

	public JoinTransformTest() {
		super("JoinTransformTest");
	}
	
	private static final String[] leftySchema = new String[] {
		"create table `titles` (`id` int unsigned not null, `name` varchar(50) not null) static distribute on (`id`)",
		"create table `states` (`id` int unsigned not null, `name` varchar(50) not null, `tag` varchar(50)) static distribute on (`id`)",
		"create table `laws` (`id` int unsigned not null, `state_id` int unsigned not null, `title_id` int unsigned not null, " 
			+ "`status` varchar(16) not null default 'unpublished', `version` int unsigned not null, `law` varchar(100)) "
			+ "static distribute on (`id`)",
		"create table `counties` (`id` int unsigned not null, `name` varchar(50) not null, `state_id` int unsigned not null) " +
			"static distribute on (`id`)",
		"create table `courts` (`id` int unsigned not null, `county_id` int unsigned not null, `address` varchar(50) not null) " +
			"static distribute on (`id`)"
	};
	
	@Test
	public void simpleTestA() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,"select l.* from laws l, states s where l.state_id = s.id and s.tag = 'Idaho'",SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT s.`id` AS si0_2 FROM `states` AS s WHERE s.`tag` = 'Idaho'",
									group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_2" }),
								new ProjectingExpectedStep(
								"SELECT DISTINCT temp1.si0_2 AS t3s0 FROM temp1",
									TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(
								"SELECT l.`id` AS li0_14,l.`state_id` AS ls1_15,l.`title_id` AS lt2_16,l.`status` AS ls3_17,l.`version` AS lv4_18,l.`law` AS ll5_19 FROM `laws` AS l, temp2 WHERE l.`state_id` = temp2.t3s0",
									group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_15" })
							)
						),
						new ProjectingExpectedStep(
						"SELECT temp3.li0_14 AS t5l0_15,temp3.ls1_15 AS t5l1_16,temp3.lt2_16 AS t5l2_17,temp3.ls3_17 AS t5l3_18,temp3.lv4_18 AS t5l4_19,temp3.ll5_19 AS t5l5_20 FROM temp3, temp1 WHERE temp3.ls1_15 = temp1.si0_2",
							null)
					));		
	}
	
	@Test
	public void simpleTestB() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,"select l.law, s.name, t.name from laws l, states s, titles t where l.state_id = s.id and s.tag = 'Idaho' and l.title_id = t.id",SelectStatement.class,
				bes(
						bpes(
							bes(
								bpes(
									bes(
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT s.`name` AS sn1_3,s.`id` AS si0_4 FROM `states` AS s WHERE s.`tag` = 'Idaho'",
											group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_4" }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT DISTINCT temp1.si0_4 AS t4s1 FROM temp1",
											TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT l.`law` AS ll5_8,l.`state_id` AS ls1_9,l.`title_id` AS lt2_10 FROM `laws` AS l, temp2 WHERE l.`state_id` = temp2.t4s1",
											group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_9" })
									)
								),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT temp3.ll5_8 AS t6l0,temp3.lt2_10 AS t6l2,temp1.sn1_3 AS t7s0 FROM temp3, temp1 WHERE temp3.ls1_9 = temp1.si0_4",
									TransientExecutionEngine.LARGE,"temp4",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t6l2" }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT DISTINCT temp4.t6l2 AS t8t1 FROM temp4",
									TransientExecutionEngine.LARGE,"temp5",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT t.`name` AS tn1_6,t.`id` AS ti0_7 FROM `titles` AS t, temp5 WHERE t.`id` = temp5.t8t1",
									group,"temp6",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ti0_7" })
							)
						),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp4.t6l0 AS t10t0_11,temp4.t7s0 AS t10t2_12,temp6.tn1_6 AS t11t0_13 FROM temp4, temp6 WHERE temp4.t6l2 = temp6.ti0_7",
							null)
					));

	}
	
	@Test
	public void complexTestA() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,
				"select l.law, ch.address, c.name " +
				" from laws l, states s, counties c, courts ch " +
				" where s.name = 'Idaho' and l.state_id = s.id and ch.county_id = c.id and c.state_id = l.state_id and ch.address like '%Main%'",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								bpes(
									bes(
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT s.`id` AS si0_2 FROM `states` AS s WHERE s.`name` = 'Idaho'",
											group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_2" }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT DISTINCT temp1.si0_2 AS t5s0 FROM temp1",
											TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT l.`law` AS ll5_6,l.`state_id` AS ls1_7 FROM `laws` AS l, temp2 WHERE l.`state_id` = temp2.t5s0",
											group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_7" })
									)
								),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT temp3.ll5_6 AS t7l0,temp3.ls1_7 AS t7l1 FROM temp3, temp1 WHERE temp3.ls1_7 = temp1.si0_2",
									TransientExecutionEngine.LARGE,"temp7",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t7l1" })
							),
							bes(
								bpes(
									bes(
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT ch.`address` AS cha2_3,ch.`county_id` AS chc1_4 FROM `courts` AS ch WHERE ch.`address` like '%Main%'",
											group,"temp4",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"chc1_4" }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT DISTINCT temp4.chc1_4 AS t9c1 FROM temp4",
											TransientExecutionEngine.LARGE,"temp5",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT c.`name` AS cn1_8,c.`id` AS ci0_9,c.`state_id` AS cs2_10 FROM `counties` AS c, temp5 WHERE c.`id` = temp5.t9c1",
											group,"temp6",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ci0_9" })
									)
								),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT temp6.cn1_8 AS t11c0,temp6.cs2_10 AS t11c2,temp4.cha2_3 AS t12c0 FROM temp6, temp4 WHERE temp4.chc1_4 = temp6.ci0_9",
									TransientExecutionEngine.LARGE,"temp8",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t11c2" })
							)
						),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp7.t7l0 AS t13t0_11,temp8.t12c0 AS t14t2_12,temp8.t11c0 AS t14t0_13 FROM temp7, temp8 WHERE temp8.t11c2 = temp7.t7l1",
							null)
					));
	}
	
	@Test
	public void testSystest1() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db, "select l.* from laws l, states s where s.id = 37 and l.state_id = s.id order by s.name",SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT l.`id` AS li0_7,l.`state_id` AS ls1_8,l.`title_id` AS lt2_9,l.`status` AS ls3_10,l.`version` AS lv4_11,l.`law` AS ll5_12 FROM `laws` AS l WHERE l.`state_id` = 37",
									group,"temp1",TransientExecutionEngine.MEDIUM,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_8" })
							),
							bes(
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT s.`name` AS sn1_3,s.`id` AS si0_4 FROM `states` AS s WHERE s.`id` = 37",
									group,"temp2",TransientExecutionEngine.MEDIUM,StaticDistributionModel.MODEL_NAME,new String[] {"si0_4" })
							)
						),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp1.li0_7 AS t3l0_17,temp1.ls1_8 AS t3l1_18,temp1.lt2_9 AS t3l2_19,temp1.ls3_10 AS t3l3_20,temp1.lv4_11 AS t3l4_21,temp1.ll5_12 AS t3l5_22,temp2.sn1_3 AS t4s0_23 FROM temp1, temp2 WHERE temp1.ls1_8 = temp2.si0_4",
							TransientExecutionEngine.MEDIUM,"temp3",TransientExecutionEngine.AGGREGATION,StaticDistributionModel.MODEL_NAME,new String[] { }),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp3.t3l0_17 AS t5t0,temp3.t3l1_18 AS t5t1,temp3.t3l2_19 AS t5t2,temp3.t3l3_20 AS t5t3,temp3.t3l4_21 AS t5t4,temp3.t3l5_22 AS t5t5 FROM temp3 ORDER BY temp3.t4s0_23 ASC",
							null)
					));
				
	}
	
	@Test
	public void testExplicitSimpleA() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,"select l.* from laws l inner join states s on l.state_id = s.id where s.tag = 'Idaho'",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT s.`id` AS si0_2 FROM `states` AS s WHERE s.`tag` = 'Idaho'",
									group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_2" }),
								new ProjectingExpectedStep(
								"SELECT DISTINCT temp1.si0_2 AS t3s0 FROM temp1",
									TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(
								"SELECT l.`id` AS li0_14,l.`state_id` AS ls1_15,l.`title_id` AS lt2_16,l.`status` AS ls3_17,l.`version` AS lv4_18,l.`law` AS ll5_19 FROM `laws` AS l, temp2 WHERE l.`state_id` = temp2.t3s0",
									group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_15" })
							)
						),
						new ProjectingExpectedStep(
						"SELECT temp3.li0_14 AS t5l0_15,temp3.ls1_15 AS t5l1_16,temp3.lt2_16 AS t5l2_17,temp3.ls3_17 AS t5l3_18,temp3.lv4_18 AS t5l4_19,temp3.ll5_19 AS t5l5_20 FROM temp3 INNER JOIN temp1 ON temp3.ls1_15 = temp1.si0_2",
							null)
					));
	}
	
	@Test
	public void testExplicitSimpleB() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		// select l.law, s.name, t.name from laws l, states s, titles t where l.state_id = s.id and s.tag = 'Idaho' and l.title_id = t.id
		stmtTest(db,
				"select l.law, s.name, t.name from laws l inner join titles t on l.title_id = t.id inner join states s on l.state_id = s.id where s.tag = 'Idaho'",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								bpes(
									bes(
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT s.`name` AS sn1_3,s.`id` AS si0_4 FROM `states` AS s WHERE s.`tag` = 'Idaho'",
											group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_4" }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT DISTINCT temp1.si0_4 AS t4s1 FROM temp1",
											TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT l.`law` AS ll5_8,l.`title_id` AS lt2_9,l.`state_id` AS ls1_10 FROM `laws` AS l, temp2 WHERE l.`state_id` = temp2.t4s1",
											group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_10" })
									)
								),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT temp3.ll5_8 AS t6l0,temp3.lt2_9 AS t6l1,temp1.sn1_3 AS t7s0 FROM temp3 INNER JOIN temp1 ON temp3.ls1_10 = temp1.si0_4",
									TransientExecutionEngine.LARGE,"temp4",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t6l1" }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT DISTINCT temp4.t6l1 AS t8t1 FROM temp4",
									TransientExecutionEngine.LARGE,"temp5",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT t.`name` AS tn1_6,t.`id` AS ti0_7 FROM `titles` AS t, temp5 WHERE temp5.t8t1 = t.`id`",
									group,"temp6",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ti0_7" })
							)
						),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp4.t6l0 AS t10t0_11,temp4.t7s0 AS t10t2_12,temp6.tn1_6 AS t11t0_13 FROM temp4 INNER JOIN temp6 ON temp4.t6l1 = temp6.ti0_7",
							null)
					)
				);
	}
	
	@Test
	public void testLeftOuterSimpleA() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,
				"select l.law, s.name, t.name from laws l left join titles t on l.title_id = t.id left join states s on l.state_id = s.id where s.tag = 'Idaho'",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								bpes(
									bes(
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT s.`name` AS sn1_3,s.`id` AS si0_4 FROM `states` AS s WHERE s.`tag` = 'Idaho'",
											group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_4" }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT DISTINCT temp1.si0_4 AS t4s1 FROM temp1",
											TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT l.`law` AS ll5_8,l.`title_id` AS lt2_9,l.`state_id` AS ls1_10 FROM `laws` AS l, temp2 WHERE l.`state_id` = temp2.t4s1",
											group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_10" })
									)
								),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT temp3.ll5_8 AS t6l0,temp3.lt2_9 AS t6l1,temp1.sn1_3 AS t7s0 FROM temp3 INNER JOIN temp1 ON temp3.ls1_10 = temp1.si0_4",
									TransientExecutionEngine.LARGE,"temp4",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t6l1" }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT DISTINCT temp4.t6l1 AS t8t1 FROM temp4",
									TransientExecutionEngine.LARGE,"temp5",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT t.`name` AS tn1_6,t.`id` AS ti0_7 FROM `titles` AS t, temp5 WHERE temp5.t8t1 = t.`id`",
									group,"temp6",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ti0_7" })
							)
						),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp4.t6l0 AS t10t0_11,temp4.t7s0 AS t10t2_12,temp6.tn1_6 AS t11t0_13 FROM temp4 LEFT OUTER JOIN temp6 ON temp4.t6l1 = temp6.ti0_7",
							null)
					));
	}
	
	@Test
	public void testLeftOuterSimpleB() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,
				"select l.law, s.name, t.name from laws l left join titles t on l.title_id = t.id inner join states s on l.state_id = s.id where s.tag = 'Idaho'",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								bpes(
									bes(
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT s.`name` AS sn1_3,s.`id` AS si0_4 FROM `states` AS s WHERE s.`tag` = 'Idaho'",
											group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_4" }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT DISTINCT temp1.si0_4 AS t4s1 FROM temp1",
											TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT l.`law` AS ll5_8,l.`title_id` AS lt2_9,l.`state_id` AS ls1_10 FROM `laws` AS l, temp2 WHERE l.`state_id` = temp2.t4s1",
											group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_10" })
									)
								),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT temp3.ll5_8 AS t6l0,temp3.lt2_9 AS t6l1,temp1.sn1_3 AS t7s0 FROM temp3 INNER JOIN temp1 ON temp3.ls1_10 = temp1.si0_4",
									TransientExecutionEngine.LARGE,"temp4",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t6l1" }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT DISTINCT temp4.t6l1 AS t8t1 FROM temp4",
									TransientExecutionEngine.LARGE,"temp5",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT t.`name` AS tn1_6,t.`id` AS ti0_7 FROM `titles` AS t, temp5 WHERE temp5.t8t1 = t.`id`",
									group,"temp6",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ti0_7" })
							)
						),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp4.t6l0 AS t10t0_11,temp4.t7s0 AS t10t2_12,temp6.tn1_6 AS t11t0_13 FROM temp4 LEFT OUTER JOIN temp6 ON temp4.t6l1 = temp6.ti0_7",
							null)
					)
					);
	}

	@Test
	public void testLeftOuterSimpleC() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,
				"select l.law, s.name, t.name from laws l inner join titles t on l.title_id = t.id left join states s on l.state_id = s.id where s.tag = 'Idaho'",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								bpes(
									bes(
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT s.`name` AS sn1_3,s.`id` AS si0_4 FROM `states` AS s WHERE s.`tag` = 'Idaho'",
											group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_4" }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT DISTINCT temp1.si0_4 AS t4s1 FROM temp1",
											TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
										new ProjectingExpectedStep(ExecutionType.SELECT,
										"SELECT l.`law` AS ll5_8,l.`title_id` AS lt2_9,l.`state_id` AS ls1_10 FROM `laws` AS l, temp2 WHERE l.`state_id` = temp2.t4s1",
											group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_10" })
									)
								),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT temp3.ll5_8 AS t6l0,temp3.lt2_9 AS t6l1,temp1.sn1_3 AS t7s0 FROM temp3 INNER JOIN temp1 ON temp3.ls1_10 = temp1.si0_4",
									TransientExecutionEngine.LARGE,"temp4",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t6l1" }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT DISTINCT temp4.t6l1 AS t8t1 FROM temp4",
									TransientExecutionEngine.LARGE,"temp5",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT t.`name` AS tn1_6,t.`id` AS ti0_7 FROM `titles` AS t, temp5 WHERE temp5.t8t1 = t.`id`",
									group,"temp6",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ti0_7" })
							)
						),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp4.t6l0 AS t10t0_11,temp4.t7s0 AS t10t2_12,temp6.tn1_6 AS t11t0_13 FROM temp4 INNER JOIN temp6 ON temp4.t6l1 = temp6.ti0_7",
							null)
					));
	}

	@Test
	public void testInnerA() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,
				"select l.* from laws l inner join states s on s.id = l.state_id where (l.version > 1) and (s.tag = 'idaho')  and (l.status = 'published') and (s.name = 'Idaho')",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT l.`id` AS li0_7,l.`state_id` AS ls1_8,l.`title_id` AS lt2_9,l.`status` AS ls3_10,l.`version` AS lv4_11,l.`law` AS ll5_12 FROM `laws` AS l WHERE  (l.`version` > 1)  AND  (l.`status` = 'published')",
									group,"temp1",TransientExecutionEngine.MEDIUM,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_8" })
							),
							bes(
								new ProjectingExpectedStep(
								"SELECT s.`id` AS si0_2 FROM `states` AS s WHERE  (s.`tag` = 'idaho')  AND  (s.`name` = 'Idaho')",
									group,"temp2",TransientExecutionEngine.MEDIUM,StaticDistributionModel.MODEL_NAME,new String[] {"si0_2" })
							)
						),
						new ProjectingExpectedStep(
						"SELECT temp1.li0_7 AS t3l0_15,temp1.ls1_8 AS t3l1_16,temp1.lt2_9 AS t3l2_17,temp1.ls3_10 AS t3l3_18,temp1.lv4_11 AS t3l4_19,temp1.ll5_12 AS t3l5_20 FROM temp1 INNER JOIN temp2 ON temp2.si0_2 = temp1.ls1_8",
							null)
					));
	}
	
	@Test
	public void testLeftOuterA() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		// note that we don't need to propagate the l.version test all the way to the end since it is on the base table
		stmtTest(db,
				"select l.*, s.*, l.status from laws l left outer join states s on s.id = l.state_id where l.version > 1",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT l.`id` AS li0_7,l.`state_id` AS ls1_8,l.`title_id` AS lt2_9,l.`status` AS ls3_10,l.`version` AS lv4_11,l.`law` AS ll5_12 FROM `laws` AS l WHERE l.`version` > 1",
									group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ls1_8" }),
								new ProjectingExpectedStep(
								"SELECT DISTINCT temp1.ls1_8 AS t3l1 FROM temp1",
									TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(
								"SELECT s.`id` AS si0_8,s.`name` AS sn1_9,s.`tag` AS st2_10 FROM `states` AS s, temp2 WHERE s.`id` = temp2.t3l1",
									group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"si0_8" })
							)
						),
						new ProjectingExpectedStep(
						"SELECT temp1.li0_7 AS t5l0_19,temp1.ls1_8 AS t5l1_20,temp1.lt2_9 AS t5l2_21,temp1.ls3_10 AS t5l3_22,temp1.lv4_11 AS t5l4_23,temp1.ll5_12 AS t5l5_24,temp3.si0_8 AS t6s0_25,temp3.sn1_9 AS t6s1_26,temp3.st2_10 AS t6s2_27,temp1.ls3_10 AS t5l3_28 FROM temp1 LEFT OUTER JOIN temp3 ON temp3.si0_8 = temp1.ls1_8",
							null)
					));
	}
	
	@Test
	public void testSchemaSystemTestA() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,"select l.* from laws l, titles t where t.name like 'Court%' and l.title_id = t.id order by l.state_id asc",
				SelectStatement.class, 
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT t.`id` AS ti0_2 FROM `titles` AS t WHERE t.`name` like 'Court%'",
									group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ti0_2" }),
								new ProjectingExpectedStep(
								"SELECT DISTINCT temp1.ti0_2 AS t3t0 FROM temp1",
									TransientExecutionEngine.LARGE,"temp2",group,BroadcastDistributionModel.MODEL_NAME,new String[] { }),
								new ProjectingExpectedStep(
								"SELECT l.`id` AS li0_14,l.`state_id` AS ls1_15,l.`title_id` AS lt2_16,l.`status` AS ls3_17,l.`version` AS lv4_18,l.`law` AS ll5_19 FROM `laws` AS l, temp2 WHERE l.`title_id` = temp2.t3t0",
									group,"temp3",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"lt2_16" })
							)
						),
						new ProjectingExpectedStep(
						"SELECT temp3.li0_14 AS t5l0_15,temp3.ls1_15 AS t5l1_16,temp3.lt2_16 AS t5l2_17,temp3.ls3_17 AS t5l3_18,temp3.lv4_18 AS t5l4_19,temp3.ll5_19 AS t5l5_20 FROM temp3, temp1 WHERE temp3.lt2_16 = temp1.ti0_2",
							TransientExecutionEngine.LARGE,"temp4",TransientExecutionEngine.AGGREGATION,StaticDistributionModel.MODEL_NAME,new String[] { }),
						new ProjectingExpectedStep(
						"SELECT temp4.t5l0_15 AS t7t0,temp4.t5l1_16 AS t7t1,temp4.t5l2_17 AS t7t2,temp4.t5l3_18 AS t7t3,temp4.t5l4_19 AS t7t4,temp4.t5l5_20 AS t7t5 FROM temp4 ORDER BY t7t1 ASC",
							null)
					));
	}
	@Test
	public void testCartesianA() throws Throwable {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);		
		stmtTest(db,
				"select * from titles, states",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT `titles`.`id` AS t1i0_3,`titles`.`name` AS t1n1_4 FROM `titles`",
									group,"temp2",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t1i0_3" })
							),
							bes(
								new ProjectingExpectedStep(
								"SELECT `states`.`id` AS s2i0_4,`states`.`name` AS s2n1_5,`states`.`tag` AS s2t2_6 FROM `states`",
									group,"temp1",TransientExecutionEngine.LARGE,BroadcastDistributionModel.MODEL_NAME,new String[] { })
							)
						),
						new ProjectingExpectedStep(
						"SELECT temp2.t1i0_3 AS t4t0_11,temp2.t1n1_4 AS t4t1_12,temp1.s2i0_4 AS t3s0_13,temp1.s2n1_5 AS t3s1_14,temp1.s2t2_6 AS t3s2_15 FROM temp2, temp1",
							null)
					));
	}
	
	@Test
	public void testCartesianB() throws Throwable {
		SchemaContext db = buildSchema(TestName.MULTI,
				"create table ltab (`id` int, `junk` varchar(24), primary key (`id`) ) static distribute on (`id`)",
				"create table rtab (`id` int, `crap` varchar(24), primary key (`id`) ) range distribute on (`id`) using openrange");
		// PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,
				"select * from ltab l, rtab r where l.id = 15",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT l.`id` AS li0_3,l.`junk` AS lj1_4 FROM `ltab` AS l WHERE l.`id` = 15",
									group,"temp1",group,BroadcastDistributionModel.MODEL_NAME,new String[] { })
							)
						),
						new ProjectingExpectedStep(
						"SELECT temp1.li0_3 AS t3l0_9,temp1.lj1_4 AS t3l1_10,r.`id` AS ri0_11,r.`crap` AS rc1_12 FROM temp1, `rtab` AS r",
							null)
					));
	}
	
	@Test
	public void testCartesianC() throws Throwable {
		SchemaContext db = buildSchema(TestName.MULTI,leftySchema);
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);		
		stmtTest(db,
				"select count(*) from titles, states",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT `titles`.`id` AS t1i0_2 FROM `titles`",
									group,"temp2",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"t1i0_2" })
							),
							bes(
								new ProjectingExpectedStep(
								"SELECT `states`.`id` AS s2i0_2 FROM `states`",
									group,"temp1",TransientExecutionEngine.LARGE,BroadcastDistributionModel.MODEL_NAME,new String[] { })
							)
						),
						new ProjectingExpectedStep(
						"SELECT count( * )  AS func FROM temp2, temp1",
							TransientExecutionEngine.LARGE,"temp3",TransientExecutionEngine.AGGREGATION,StaticDistributionModel.MODEL_NAME,new String[] { }),
						new ProjectingExpectedStep(
						"SELECT CONVERT( sum( temp3.func ) ,SIGNED )  AS func_3 FROM temp3",
							null)
					)
		);
	}
	
	@Test
	public void testCartesianD() throws Throwable {
		SchemaContext db = buildSchema(TestName.MULTI,
				"create table LA (id int, fid int, primary key (id))",
				"create table RA (id int, fid int, primary key (id))"
				);
		PEPersistentGroup group = getGroup(db);
		stmtTest(db,
				"select la.id, ra.fid from LA la, RA ra where la.id <> ra.id",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT la.`id` AS lai0_2 FROM `LA` AS la",
									group,"temp2",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] { }, new String[][] { })
							),
							bes(
								new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT ra.`fid` AS raf1_3,ra.`id` AS rai0_4 FROM `RA` AS ra",
									group,"temp1",TransientExecutionEngine.LARGE,BroadcastDistributionModel.MODEL_NAME,new String[] { }, new String[][] { })
							)
						),
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp2.lai0_2 AS t4l0_7,temp1.raf1_3 AS t3r0_8 FROM temp2, temp1 WHERE temp2.lai0_2 <> temp1.rai0_4",
							null)
					));
	}

	@Ignore
	@Test
	public void testCartesianE() throws Throwable {
		SchemaContext db = buildSchema(TestName.MULTI,
				"create table LA (id int, fid int, primary key (id))",
				"create table RA (id int, fid int, primary key (id))",
				"create table SW (id int, fid int, sid int, primary key(id))"
				);
		//PEPersistentGroup group = getGroup(db);
		
		stmtTest(db,
				"select la.id, ra.fid, sw.sid from LA la inner join RA ra on la.id = ra.id, SW sw where sw.id <> ra.fid",
				SelectStatement.class,
				null);
	}

	@Ignore
	@Test
	public void testCartesianF() throws Throwable {
		SchemaContext db = buildSchema(TestName.MULTI,
				"create table LA (id int, fid int, primary key (id))",
				"create table RA (id int, fid int, primary key (id))",
				"create table LB (id int, fid int, sid int, primary key(id))",
				"create table RB (id int, fid int, sid int, primary key(id))"
				);
		//PEPersistentGroup group = getGroup(db);
		
		stmtTest(db,
				"select la.id, ra.fid, lb.sid, rb.sid from LA la inner join RA ra on la.id = ra.id, LB lb, RB rb where lb.fid = rb.fid",
				SelectStatement.class,
				null);
	}

	
	
	@Ignore
	@Test
	public void testPlanNesting() throws Throwable {
		System.out.println("press return to continue");
		System.in.read();
		ArrayList<String> tabs = new ArrayList<String>();
		int ntabs = 1000;
		ArrayList<String> tabnames = new ArrayList<String>();
		for(int i = 0; i < ntabs; i++) {
			String tabname = "tpn" + i;
			tabs.add("create table " + tabname + " (`id` int, `whatever` varchar(32), primary key (`id`))");
			tabnames.add(tabname);
		}	
		// tabnames has tpn0, tpn1, tpn2
		// build select * from tpn0 t0 inner join tpn1 t1 on t0.id=t1.id inner join tpn2 t2 on t1.id=t2.id
		StringBuilder stmt = new StringBuilder();
		stmt.append("select * from tpn0 atpn0");
		for(int i = 1; i < tabnames.size(); i++) {
			String ptn = tabnames.get(i - 1);
			String ctn = tabnames.get(i);
			stmt.append(" inner join " + ctn + " a" + ctn + " on a" + ptn + ".id=a" + ctn + ".id");
		}
		SchemaContext db = buildSchema(TestName.MULTI, tabs.toArray(new String[0]));
		ExecutionPlan ep = stmtTest(db,
				stmt.toString(),
				SelectStatement.class,
				null);
		System.out.println("Finished planning");
		// exercise the plan, see if we can get it to overflow
		ep.getlastInsertId(db.getValueManager(),db);
		ep.getUpdateCount(db);
	}
	
	@Test
	public void testPE449() throws Exception {
		SchemaContext db = buildSchema(TestName.MULTI,
				"create table foo (`id` int, `pid` int, `junk` varchar(32), primary key (`id`))");
		ExecutionPlan ep = stmtTest(db,"select * from foo",SelectStatement.class,
				bes(
						new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT `foo`.`id` AS f1i0_5,`foo`.`pid` AS f1p1_6,`foo`.`junk` AS f1j2_7 FROM `foo`",
							null)
					));
		ProjectionInfo pi = ep.getProjectionInfo();
		for(int i = 1; i <= pi.getWidth(); i++) {
			ColumnInfo ci = pi.getColumnInfo(i);
			assertEquals("mydb",ci.getDatabaseName());
			assertEquals("foo",ci.getTableName());
		}
	}

	@Test
	public void testPE658A() throws Throwable {
		SchemaContext db = buildSchema(TestName.MULTI,
				"create table ltab (`id` int, `junk` varchar(32), primary key (`id`)) random distribute",
				"create table rtab (`id` int, `junk` varchar(32), primary key (`id`)) random distribute");
		PEPersistentGroup group = db.getCurrentDatabase().getDefaultStorage(db);
		stmtTest(db,
				"select l.junk, r.junk from ltab l inner join rtab r where l.id = r.id",
				SelectStatement.class,
				bes(
						bpes(
							bes(
								new ProjectingExpectedStep(
								"SELECT l.`junk` AS lj1_3,l.`id` AS li0_4 FROM `ltab` AS l",
									group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"li0_4" })
							),
							bes(
								new ProjectingExpectedStep(
								"SELECT r.`junk` AS rj1_3,r.`id` AS ri0_4 FROM `rtab` AS r",
									group,"temp2",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"ri0_4" })
							)
						),
						new ProjectingExpectedStep(
						"SELECT temp1.lj1_3 AS t3l0_9,temp2.rj1_3 AS t4r0_10 FROM temp1 INNER JOIN temp2 WHERE temp1.li0_4 = temp2.ri0_4",
							null)
					));
	}

	@Test
	public void testPE1369() throws Throwable {
		SchemaContext db = buildSchema(TestName.MULTI,
				"create table bar1(col1 int)",
				"create table bar2(col1 int)"
				);
		PEPersistentGroup group = getGroup(db);
		String dbName = db.getCurrentDatabase().getName().get();
		String sql = "select " + dbName + ".bar1.col1 from " + dbName + ".bar2, " + dbName + ".bar1 where " + dbName + ".bar1.col1=" + dbName + ".bar2.col1";
		stmtTest(db,sql,SelectStatement.class,
				bes(
					bpes(
						bes(
							new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT `bar2`.`col1` AS b1c0_2 FROM `bar2`",
								group,"temp1",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"b1c0_2" })
						),
						bes(
							new ProjectingExpectedStep(ExecutionType.SELECT,
								"SELECT `bar1`.`col1` AS b2c0_2 FROM `bar1`",
								group,"temp2",TransientExecutionEngine.LARGE,StaticDistributionModel.MODEL_NAME,new String[] {"b2c0_2" })
						)
					),
					new ProjectingExpectedStep(ExecutionType.SELECT,
						"SELECT temp2.b2c0_2 AS t4b0_5 FROM temp1, temp2 WHERE temp2.b2c0_2 = temp1.b1c0_2",
						null)
				));
	}
}

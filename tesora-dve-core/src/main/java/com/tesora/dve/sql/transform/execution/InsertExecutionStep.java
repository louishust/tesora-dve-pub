package com.tesora.dve.sql.transform.execution;

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



import java.util.List;

import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.queryplan.QueryStepInsertByKeyOperation;
import com.tesora.dve.queryplan.QueryStepOperation;
import com.tesora.dve.resultset.ProjectionInfo;
import com.tesora.dve.server.messaging.SQLCommand;
import com.tesora.dve.sql.schema.ConnectionValues;
import com.tesora.dve.sql.schema.Database;
import com.tesora.dve.sql.schema.DistributionKey;
import com.tesora.dve.sql.schema.PEStorageGroup;
import com.tesora.dve.sql.schema.PETable;
import com.tesora.dve.sql.schema.SchemaContext;
import com.tesora.dve.sql.schema.ValueManager;
import com.tesora.dve.sql.statement.dml.DMLStatement;

public final class InsertExecutionStep extends DirectExecutionStep {

	// number of tuples
	protected Long updateCount;

	private InsertExecutionStep(SchemaContext pc, Database<?> db, PEStorageGroup tsg, DMLStatement sql, PETable tab, DistributionKey kv, 
			Long uc) throws PEException {
		super(db, tsg, ExecutionType.INSERT, tab.getDistributionVector(pc), kv,
				sql.getGenericSQL(pc, false, true), null);
		updateCount = uc;
	}

	public static InsertExecutionStep build(SchemaContext pc, Database<?> db, PEStorageGroup tsg,
			DMLStatement sql, PETable tab, DistributionKey kv, Long uc) throws PEException {
		// tenant rewrite not needed due to special insert planning
		return new InsertExecutionStep(pc, db, tsg, sql, tab, kv, uc);
	}
	
	@Override
	public Long getlastInsertId(ValueManager vm, SchemaContext sc, ConnectionValues cv) {
		return cv.getLastInsertId();
	}
	
	@Override
	public Long getUpdateCount(SchemaContext sc, ConnectionValues cv) {
		return updateCount;
	}

	@Override
	public void schedule(ExecutionPlanOptions opts, List<QueryStepOperation> qsteps, ProjectionInfo projection, SchemaContext sc,
			ConnectionValuesMap cvm, ExecutionPlan containing)
			throws PEException {
		ConnectionValues cv = cvm.getValues(containing);
		SQLCommand sqlCommand = getCommand(sc,cv).withReferenceTime(getReferenceTimestamp(cv));
		QueryStepInsertByKeyOperation qso = 
				new QueryStepInsertByKeyOperation(getStorageGroup(sc,cv),getPersistentDatabase(), 
						getKeyValue(sc,cv), 
						sqlCommand);
		qso.setStatistics(getStepStatistics(sc));
		qsteps.add(qso);
	}
}

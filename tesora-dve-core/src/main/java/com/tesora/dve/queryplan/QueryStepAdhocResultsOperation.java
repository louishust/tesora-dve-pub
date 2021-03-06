package com.tesora.dve.queryplan;

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

import com.tesora.dve.db.DBResultConsumer;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.resultset.IntermediateResultSet;
import com.tesora.dve.worker.WorkerGroup;

public class QueryStepAdhocResultsOperation extends QueryStepOperation {

	private IntermediateResultSet results;
	
	public QueryStepAdhocResultsOperation(IntermediateResultSet irs) throws PEException {
		super(nullStorageGroup);
		results = irs;
	}
	
	@Override
	public void executeSelf(ExecutionState estate, WorkerGroup wg, DBResultConsumer resultConsumer)
			throws Throwable {
		resultConsumer.inject(results.getMetadata(), results.getRows());
	}
	
	@Override
	public boolean requiresTransactionSelf() {
		return false;
	}

	@Override
	public boolean requiresWorkers() {
		// could be executed before the database is set
		return false;
	}

}

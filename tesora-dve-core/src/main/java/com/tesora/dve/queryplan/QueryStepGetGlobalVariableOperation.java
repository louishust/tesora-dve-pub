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

import java.sql.Types;

import com.tesora.dve.db.DBResultConsumer;
import com.tesora.dve.resultset.ColumnSet;
import com.tesora.dve.resultset.ResultRow;
import com.tesora.dve.server.connectionmanager.SSConnection;
import com.tesora.dve.variables.VariableHandler;
import com.tesora.dve.worker.WorkerGroup;

public class QueryStepGetGlobalVariableOperation extends QueryStepOperation {
	
	VariableHandler handler;
	private String alias;


	public QueryStepGetGlobalVariableOperation(VariableHandler handler, String alias) {
		super();
		this.handler = handler;
		this.alias = alias;
	}
	
	public QueryStepGetGlobalVariableOperation(VariableHandler handler) {
		this(handler, handler.getName());
	}


	@Override
	public void execute(SSConnection ssCon, WorkerGroup wg, DBResultConsumer resultConsumer)
			throws Throwable {
        resultConsumer.inject(ColumnSet.singleColumn(alias, Types.VARCHAR),
				ResultRow.singleRow(
						handler.toRow(handler.getGlobalValue(ssCon))));
	}

}

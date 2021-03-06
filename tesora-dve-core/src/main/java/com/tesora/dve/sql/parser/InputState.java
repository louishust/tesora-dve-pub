package com.tesora.dve.sql.parser;

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

import org.antlr.runtime.ANTLRStringStream;

import com.tesora.dve.sql.statement.dml.InsertIntoValuesStatement;

public interface InputState {

	public ANTLRStringStream buildNewStream();
	
	public String describe();
	
	// for holding the offset within the stmt
	public void setCurrentPosition(int pos);
	public int getCurrentPosition();
	
	// for holding the skeleton
	public void setInsertSkeleton(InsertIntoValuesStatement is);
	public InsertIntoValuesStatement getInsertSkeleton();
	
	public String getCommand();
	
	public long getThreshold();

	public boolean isInsert();
}

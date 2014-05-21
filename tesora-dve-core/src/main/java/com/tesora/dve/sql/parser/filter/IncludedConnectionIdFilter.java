// OS_STATUS: public
package com.tesora.dve.sql.parser.filter;

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

import java.util.Set;

public class IncludedConnectionIdFilter implements LogFileFilter {
	Set<Integer> connIds;
	
	public IncludedConnectionIdFilter(Set<Integer> connIds) {
		this.connIds = connIds;
	}

	@Override
	public boolean filterLine(int connectionId, String statement) {
		return !connIds.contains(connectionId);
	}

}

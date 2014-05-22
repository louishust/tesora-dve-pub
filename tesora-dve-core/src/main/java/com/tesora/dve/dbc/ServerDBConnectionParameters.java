package com.tesora.dve.dbc;

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

import java.util.HashSet;
import java.util.Set;

public class ServerDBConnectionParameters {

	private String cacheName = null;
	private Set<String> replSlaveIgnoreTblList = new HashSet<String>();

	public String getCacheName() {
		return cacheName;
	}

	public void setCacheName(String cacheName) {
		this.cacheName = cacheName;
	}
	
	public Set<String> getReplSlaveIgnoreTblList() {
		return replSlaveIgnoreTblList;
	}

	public void setReplSlaveIgnoreTblList(Set<String> replSlaveIgnoreTblList) {
		this.replSlaveIgnoreTblList = replSlaveIgnoreTblList;
	}
	
}

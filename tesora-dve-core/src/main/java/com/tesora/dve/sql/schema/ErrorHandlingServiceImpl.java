package com.tesora.dve.sql.schema;

/*
 * #%L
 * Tesora Inc.
 * Database Virtualization Engine
 * %%
 * Copyright (C) 2011 - 2015 Tesora Inc.
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

import com.tesora.dve.sql.ErrorHandlingService;
import com.tesora.dve.variables.KnownVariables;

/**
 *
 */
public class ErrorHandlingServiceImpl implements ErrorHandlingService {
    public boolean useVerboseErrorHandling() {
        // ugh, what a freaking hack, but I don't want to add a callback everywhere
        SchemaContext current = SchemaContext.threadContext.get();
        return (current != null && current.getCatalog().isPersistent() &&
                KnownVariables.ERROR_MIGRATOR.getGlobalValue(null));
    }
}

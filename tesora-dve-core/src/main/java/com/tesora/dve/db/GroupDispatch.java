package com.tesora.dve.db;

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

import com.tesora.dve.concurrent.CompletionHandle;
import com.tesora.dve.db.mysql.MysqlCommandResultsProcessor;
import com.tesora.dve.db.mysql.MysqlMessage;
import com.tesora.dve.server.messaging.SQLCommand;

/**
 *
 */
public interface GroupDispatch {

    public class Bundle {
        public static final Bundle NO_COMM = new Bundle();
        public final MysqlMessage outboundMessage;
        public final MysqlCommandResultsProcessor resultsProcessor;

        protected Bundle(){
            this.outboundMessage = null;
            this.resultsProcessor = null;
        }

        public Bundle(MysqlMessage outboundMessage, MysqlCommandResultsProcessor resultsProcessor) {
            this.outboundMessage = outboundMessage;
            this.resultsProcessor = resultsProcessor;
        }

        public void writeAndFlush(CommandChannel channel){
            if (outboundMessage != null)
                channel.writeAndFlush(outboundMessage,resultsProcessor);
        }

    }

    void setSenderCount(int senderCount);
    Bundle getDispatchBundle(CommandChannel connection, SQLCommand sql, CompletionHandle<Boolean> promise);
}

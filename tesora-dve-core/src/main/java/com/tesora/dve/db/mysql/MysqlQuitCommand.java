package com.tesora.dve.db.mysql;

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

import com.tesora.dve.db.mysql.libmy.MyMessage;
import com.tesora.dve.db.mysql.libmy.MyOKResponse;
import com.tesora.dve.exceptions.PECommunicationsException;
import io.netty.channel.ChannelHandlerContext;

import com.tesora.dve.exceptions.PECodingException;
import com.tesora.dve.exceptions.PEException;

public class MysqlQuitCommand extends MysqlCommand {

    boolean closed = false;

    public MysqlQuitCommand() {
        super();
    }

    @Override
    public void packetStall(ChannelHandlerContext ctx) {
    }

    @Override
	public boolean processPacket(ChannelHandlerContext ctx, MyMessage message) throws PEException {
        if (message instanceof MyOKResponse)
            closed = true;
        return true;
	}

	@Override
	public void failure(Exception e) {
        if (e instanceof PECommunicationsException){
            PECommunicationsException comm = (PECommunicationsException)e;
            if (comm.getMessage().startsWith("Connection closed")){
                //a connection close can return an OK, or close the socket, so this isn't an error.
                closed = true;
                return;
            }
        }
		throw new PECodingException(this.getClass().getSimpleName() + " encountered unhandled exception", e);
	}


    @Override
    public void active(ChannelHandlerContext ctx) {
        //NOOP.
    }

}

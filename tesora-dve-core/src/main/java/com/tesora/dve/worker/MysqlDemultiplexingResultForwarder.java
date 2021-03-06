package com.tesora.dve.worker;

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

import com.tesora.dve.db.mysql.libmy.*;
import com.tesora.dve.exceptions.PEException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.LoggingHandler;

import org.apache.log4j.Logger;

import com.tesora.dve.resultset.ColumnInfo;
import com.tesora.dve.server.connectionmanager.SSConnection;

public abstract class MysqlDemultiplexingResultForwarder extends MysqlParallelResultConsumer {
	
	private static final Logger logger = Logger.getLogger(MysqlDemultiplexingResultForwarder.class);

	static public class ByteLogger extends LoggingHandler {

        public void printBuffer(String message, MyMessage recv) {
//			System.out.println(formatByteBuf(message, buf));
        }
	}

	protected static ByteLogger byteLogger = new ByteLogger();
	
	protected final ChannelHandlerContext outboundCtx;
	long tmr;

	private long resultsLimit = Long.MAX_VALUE;
	long rowCount = 0;

	public MysqlDemultiplexingResultForwarder(ChannelHandlerContext outboundCtx) {
		this.outboundCtx = outboundCtx;
	}


    @Override
    public void consumeEmptyResultSet(MyOKResponse ok) {
        byteLogger.printBuffer("consumeEmptyResultSet", ok);
    }


	@Override
	public void consumeError(MyErrorResponse errorResp) {
		byteLogger.printBuffer("consumeError", errorResp);
		outboundCtx.writeAndFlush(errorResp);
		if (logger.isDebugEnabled()) {
			logger.debug("Error forwarded to user: " + errorResp);
		}
	}


    @Override
    public void consumeFieldCount(MyColumnCount colCount) {
        byteLogger.printBuffer("consumeFieldCount", colCount);
        tmr = System.currentTimeMillis();
        sendMessage(colCount);
    }

    public void sendMessage(MyMessage msg) {
        sendMessage(msg,false);
    }

    public void sendMessage(MyMessage msg, boolean flush) {
        byteLogger.printBuffer("writtenMessage", msg);
        if (flush)
            outboundCtx.writeAndFlush(msg);
        else
            outboundCtx.write(msg);
    }

    @Override
    public void consumeField(int field_, MyFieldPktResponse columnDef, ColumnInfo columnInfo) {
		if (columnInfo == null) {
			byteLogger.printBuffer("consumeField", columnDef);
			sendMessage(columnDef);
		} else {
            columnDef.setColumn(columnInfo.getAlias());
			columnDef.setOrig_column(columnInfo.getName());
			sendMessage(columnDef);
		}
	}

	@Override
	public void consumeFieldEOF(MyMessage someMessage) {
		byteLogger.printBuffer("consumeFieldEOF", someMessage);
		sendMessage(someMessage,/* flush */ false);
	}


	@Override
	public void consumeRowEOF() {
//		byteLogger.printBuffer("consumeRowEOF", wholePacket);
//		writePacket(wholePacket);
//		System.out.println("Time1: " + 1.0 * (System.currentTimeMillis() - tmr)/1000);
//		outboundCtx.flush().addListener(new ChannelFutureListener() {
//			@Override
//			public void operationComplete(ChannelFuture future) throws Exception {
//				System.out.println("Time2: " + 1.0 * (System.currentTimeMillis() - tmr)/1000);
//			}
//		});
	}

    public void consumeRowText(MyTextResultRow textRow){
        handleUnknownRow(textRow);
    }

    public void consumeRowBinary(MyBinaryResultRow binRow){
        handleUnknownRow(binRow);
    }

    @Override
    public void rowFlush() throws PEException {
        outboundCtx.flush();
    }

    public void handleUnknownRow(MyMessage row) {
        if (rowCount >= resultsLimit)
            return;

        rowCount++;
        outboundCtx.write(row);
    }

	@Override
	public void setResultsLimit(long resultsLimit) {
		this.resultsLimit = resultsLimit;
	}


	public long getResultsLimit() {
		return resultsLimit;
	}


	public void sendSuccess(SSConnection ssConn) {
		if (hasResults())
			sendEOFPacket(ssConn);
		else 
			sendOKPacket(ssConn);
	}
	
	/**
	 * @param ssConn
	 */
	private void sendEOFPacket(SSConnection ssConn) {
		MyEOFPktResponse eofPacket1 = new MyEOFPktResponse();
		eofPacket1.setStatusFlags(statusFlags);
		eofPacket1.setWarningCount(warnings);
        outboundCtx.writeAndFlush(eofPacket1);
	}


	private void sendOKPacket(SSConnection ssConn) {
		MyOKResponse okPacket1 = new MyOKResponse();
		okPacket1.setAffectedRows(numRowsAffected);
		okPacket1.setServerStatus(statusFlags);

        //See PE-1568.  Protocol format looks like last insert ID is the *first* ID in the autoinc sequence, IE: [insertID...(insertID+affectedRows-1)]
        //autoinc behavior is undefined if they go negative, and zero is reserved for 'generate', so if we compute less than 1, revert to previous behavior (probably a zero)
        long lastInsertedId = ssConn.getLastInsertedId();
        long computedStartID = lastInsertedId <=numRowsAffected ? lastInsertedId : (lastInsertedId - numRowsAffected + 1L);
		okPacket1.setInsertId( computedStartID );
		okPacket1.setWarningCount(ssConn.getMessageManager().getNumberOfMessages());
		okPacket1.setMessage(infoString);
		outboundCtx.writeAndFlush(okPacket1);
	}


	public void sendError(Exception e) {
		MyMessage respMsg = new MyErrorResponse(e);
		outboundCtx.writeAndFlush(respMsg);
	}
	
	public void sendError(MyErrorResponse constructed) {
		outboundCtx.write(constructed);
		outboundCtx.flush();		
	}

}

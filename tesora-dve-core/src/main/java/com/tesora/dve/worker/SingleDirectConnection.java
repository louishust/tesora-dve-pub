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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.tesora.dve.concurrent.CompletionHandle;
import com.tesora.dve.db.mysql.SetVariableSQLBuilder;
import com.tesora.dve.db.mysql.SharedEventLoopHolder;
import io.netty.channel.EventLoopGroup;
import org.apache.log4j.Logger;

import com.tesora.dve.common.catalog.StorageSite;
import com.tesora.dve.db.DBConnection;
import com.tesora.dve.exceptions.PESQLException;

public class SingleDirectConnection implements WorkerConnection {
	static Logger logger = Logger.getLogger(SingleDirectConnection.class);

    static final EventLoopGroup DEFAULT_EVENTLOOP = SharedEventLoopHolder.getLoop();

    EventLoopGroup preferredEventLoop = DEFAULT_EVENTLOOP;

    final UserAuthentication userAuthentication;
	final StorageSite site;
	final AdditionalConnectionInfo additionalConnInfo;

	AtomicReference<DirectConnectionCache.CachedConnection> datasourceInfo = new AtomicReference<>();
	
	WorkerStatement wSingleStatement = null;



	public SingleDirectConnection(final UserAuthentication auth, final AdditionalConnectionInfo additionalConnInfo, final StorageSite site, EventLoopGroup preferredEventLoop) {
		this.userAuthentication = auth;
		this.site = site;
		this.additionalConnInfo = additionalConnInfo;
        if (preferredEventLoop == null)
            this.preferredEventLoop = DEFAULT_EVENTLOOP;
        else
            this.preferredEventLoop = preferredEventLoop;
	}

	@Override
	public WorkerStatement getStatement(Worker w) throws PESQLException {
		if (wSingleStatement==null) {
			w.setPreviousDatabaseWithCurrent();
				
			wSingleStatement = getNewStatement(w);
		}
		return wSingleStatement;
	}

	/**
	 * @param w
	 * @throws PESQLException
	 */
	protected void onCommunicationsFailure(Worker w) throws PESQLException {
		// do nothing?
	}

	protected SingleDirectStatement getNewStatement(Worker w) throws PESQLException {
		return new SingleDirectStatement(w, getConnection());
	}


    protected DBConnection getConnection() throws PESQLException {
        DirectConnectionCache.CachedConnection cacheEntry = datasourceInfo.get();
        while (cacheEntry == null){
            cacheEntry = DirectConnectionCache.checkoutDatasource(preferredEventLoop,userAuthentication, additionalConnInfo, site);

            if (datasourceInfo.compareAndSet(null, cacheEntry))
                break;

            DirectConnectionCache.returnDatasource(cacheEntry);
            cacheEntry = datasourceInfo.get();
        }
        return cacheEntry;
    }
	
	@Override
	public synchronized void close(boolean isStateValid) throws PESQLException {
        releaseConnection(isStateValid);
	}

    private void releaseConnection(boolean isStateValid) throws PESQLException {
        closeActiveStatements();

        DirectConnectionCache.CachedConnection currentConnection = datasourceInfo.getAndSet(null);

        if (currentConnection != null){
            if (isStateValid) {
                DirectConnectionCache.returnDatasource(currentConnection);
            } else {
                DirectConnectionCache.discardDatasource(currentConnection);
            }
        }
    }


    @Override
	public void closeActiveStatements() throws PESQLException {
		if (wSingleStatement != null) {
			wSingleStatement.close();
			wSingleStatement = null;
		}
	}

	@Override
	public void setCatalog(String databaseName) throws PESQLException {
		getConnection().setCatalog(databaseName, null);
	}

    public void updateSessionVariables(Map<String,String> desiredVariables, SetVariableSQLBuilder setBuilder, CompletionHandle<Boolean> promise) {
        DBConnection connection = null;
        try {
            connection = getConnection();
            connection.updateSessionVariables(desiredVariables, setBuilder, promise);
        } catch (PESQLException e) {
            promise.failure(e);
        }
    }



	@Override
	public void rollbackXA(DevXid xid, CompletionHandle<Boolean> promise) {
		try {
			getConnection().rollback(xid, promise);
		} catch (Exception e) {
			PESQLException problem = new PESQLException("Cannot rollback XA Transaction " + xid, e);
            problem.fillInStackTrace();
            promise.failure(problem);
		}
	}

	@Override
	public void commitXA(DevXid xid, boolean onePhase, CompletionHandle<Boolean> promise) {
        try {
            getConnection().commit(xid, onePhase, promise);
        } catch (PESQLException e) {
            promise.failure(e);
        }
    }

	@Override
	public void prepareXA(DevXid xid, CompletionHandle<Boolean> promise) {
        try {
            getConnection().prepare(xid, promise);
        } catch (PESQLException sqlError){
            promise.failure(sqlError);
        }
	}

	@Override
	public void endXA(DevXid xid) throws PESQLException {
		try {
			getConnection().end(xid,null);
		} catch (Exception e) {
			throw new PESQLException("Cannot end XA Transaction " + xid, e);
		}
	}

	@Override
	public void startXA(DevXid xid) throws PESQLException {
		try {
			getConnection().start(xid, null);
		} catch (Exception e) {
			throw new PESQLException("Cannot start XA Transaction " + xid, e);
		}
	}


	@Override
	public boolean isModified() throws PESQLException {
		return getConnection().hasPendingUpdate();
	}

    @Override
	public boolean hasActiveTransaction() throws PESQLException {
		return getConnection().hasActiveTransaction();
	}

	@Override
	public int getConnectionId() throws PESQLException {
		return getConnection().getConnectionId();
	}

    @Override
    public void bindToClientThread(EventLoopGroup eventLoop) throws PESQLException {
        if (preferredEventLoop == eventLoop)
            return;

        if (eventLoop == null)
            this.preferredEventLoop = DEFAULT_EVENTLOOP;
        else
            this.preferredEventLoop = eventLoop;

        //return this connection to the pool.  we'll get a new one tied to our preferred event loop from the cache on next request.
        releaseConnection(true);
    }
}
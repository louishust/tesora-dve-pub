package com.tesora.dve.sql.expression;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import com.tesora.dve.sql.SchemaException;
import com.tesora.dve.sql.node.expression.Alias;
import com.tesora.dve.sql.node.expression.ExpressionAlias;
import com.tesora.dve.sql.node.expression.ExpressionNode;
import com.tesora.dve.sql.node.expression.FunctionCall;
import com.tesora.dve.sql.node.expression.NameInstance;
import com.tesora.dve.sql.node.expression.TableInstance;
import com.tesora.dve.sql.node.expression.VariableInstance;
import com.tesora.dve.sql.parser.SourceLocation;
import com.tesora.dve.sql.schema.LockInfo;
import com.tesora.dve.sql.schema.Name;
import com.tesora.dve.sql.schema.PEColumn;
import com.tesora.dve.sql.schema.PETable;
import com.tesora.dve.sql.schema.Schema;
import com.tesora.dve.sql.schema.SchemaContext;
import com.tesora.dve.sql.schema.SubqueryTable;
import com.tesora.dve.sql.schema.Table;
import com.tesora.dve.sql.schema.UnqualifiedName;
import com.tesora.dve.sql.statement.dml.ProjectingStatement;
import com.tesora.dve.sql.util.ListSet;
import com.tesora.dve.sql.util.ResizableArray;

public class ScopeStack implements Scope {

	private Stack<ScopeEntry> scopes;
	private ResizableArray<ScopeEntry> popped;
	private int counter;
	
	public ScopeStack() {
		scopes = new Stack<ScopeEntry>();
		popped = new ResizableArray<ScopeEntry>();
		counter = -1;
	}
	
	public void pushScope() {
		pushScope(new ScopeEntry(true,++counter));
	}
	
	private void pushScope(ScopeEntry s) {
		Scope parent = null;
		if (!scopes.isEmpty())
			parent = scopes.peek();
		if (parent != null)
			parent.getNested().add(s);
		scopes.push(s);
	}
	
	public void pushUnresolvingScope() {
		pushScope(new ScopeEntry(false,++counter));
	}
	
	@Override
	public void resolveProjection(SchemaContext sc) {
		getScope().resolveProjection(sc);
	}
	
	@Override
	public void storeProjection(List<ExpressionNode> exprs) {
		getScope().storeProjection(exprs);
	}
	
	@Override
	public void setPhase(ScopeParsePhase spp) {
		getScope().setPhase(spp);
	}
	
	@Override
	public ScopeParsePhase getPhase() {
		return getScope().getPhase();
	}
		
	public void popScope() {
		ScopeEntry se = scopes.pop();
		if (se.getUnresolved() != null && se.getUnresolved().size() > 0 && !isEmpty()) {
			for (NameInstance ni : se.getUnresolved()) {
				getScope().buildColumnInstance(null, ni.getName());
			}
			getScope().getUnresolvedChildren().addAll(se.getUnresolvedChildren());
			getScope().getUnresolvedChildren().addAll(se.getUnresolved());
		}
		popped.set(se.getID(),se);
	}
	
	public int getLastPoppedScopeID() {
		if (popped.size() == 0) return -1;
		return popped.get(popped.size() - 1).getID();
	}
	
	public void pushScopeID(int id) {
		pushScope(popped.get(id));
	}
	
	private Scope getScope() {
		return scopes.peek();
	}
	
	public boolean isEmpty() {
		return scopes.isEmpty();
	}
	
	public Scope getParentScope() {
		if (scopes.size() > 1)
			return scopes.get(scopes.size() - 2);
		return null;
	}
	
	public List<Scope> getCurrentStack() {
		ArrayList<Scope> out = new ArrayList<Scope>();
		for(ScopeEntry se : scopes)
			out.add(se);
		return out;
	}

	@Override
	public List<Scope> getNested() {
		return getScope().getNested();
	}

	@Override
	public ListSet<ProjectingStatement> getNestedQueries() {
		return getScope().getNestedQueries();
	}

	@Override
	public ListSet<VariableInstance> getVariables() {
		return getScope().getVariables();
	}

	@Override
	public TableInstance buildTableInstance(final Name inTableName,
			final UnqualifiedName alias, 
			final Schema<?> inSchema, 
			final SchemaContext sc, 
			final LockInfo info) {
		return lookup(new LookupFunction<TableInstance>() {

			@Override
			public TableInstance lookup(ScopeEntry e) throws SchemaException {
				return e.buildTableInstance(inTableName, alias, inSchema, sc, info);
			}
			
		});
	}

	@Override
	public TableInstance buildTableInstance(final Name inTableName,
			final UnqualifiedName alias, final SchemaContext sc, final LockInfo info) {
		return lookup(new LookupFunction<TableInstance>() {

			@Override
			public TableInstance lookup(ScopeEntry entry) throws SchemaException {
				return entry.buildTableInstance(inTableName, alias, sc, info);
			}
			
		});
	}

	private <ReturnType> ReturnType lookup(LookupFunction<ReturnType> f) {
		RuntimeException any = null;
		for(int i = scopes.size() - 1; i > -1; i--) {
			try {
				return f.lookup(scopes.get(i));
			} catch (SchemaException e) {
				if (any == null) any = e;
			}
		}
		throw any;
	}

	interface LookupFunction<ReturnType> {
		
		ReturnType lookup(ScopeEntry e) throws SchemaException;
		
	}
	
	@Override
	public ExpressionNode buildColumnInstance(final SchemaContext sc, final Name given) {
		return lookup(new LookupFunction<ExpressionNode>() {

			@Override
			public ExpressionNode lookup(ScopeEntry e) throws SchemaException {
				return e.buildColumnInstance(sc, given);
			}
			
		});
	}

	@Override
	public void insertColumn(UnqualifiedName alias, ExpressionAlias e) {
		getScope().insertColumn(alias, e);
	}

	@Override
	public ExpressionNode buildExpressionAlias(final ExpressionNode e,
			final Alias alias, final SourceLocation sloc) {
		return lookup(new LookupFunction<ExpressionNode>() {

			@Override
			public ExpressionNode lookup(ScopeEntry entry) throws SchemaException {
				return entry.buildExpressionAlias(e, alias, sloc);
			}
			
		});
	}

	@Override
	public Set<String> getAllAliases() {
		return getScope().getAllAliases();
	}

	@Override
	public ListSet<TableKey> getLocalTables() {
		return getScope().getLocalTables();
	}

	@Override
	public ListSet<TableKey> getAllVisibleTables() {
		return getScope().getAllVisibleTables();
	}

	@Override
	public ListSet<FunctionCall> getFunctions() {
		return getScope().getFunctions();
	}

	@Override
	public void registerFunction(FunctionCall fc) {
		getScope().registerFunction(fc);
	}

	@Override
	public PEColumn registerColumn(PEColumn c) {
		return getScope().registerColumn(c);
	}

	@Override
	public void registerAlterColumns(SchemaContext sc, PETable tab) {
		getScope().registerAlterColumns(sc,tab);
	}

	@Override
	public PEColumn lookupInProcessColumn(Name n) {
		return getScope().lookupInProcessColumn(n);
	}

	@Override
	public Table<?> getAlteredTable() {
		return getScope().getAlteredTable();
	}

	@Override
	public void pushVirtualTable(SubqueryTable sqt, UnqualifiedName alias, SchemaContext sc) {
		getScope().pushVirtualTable(sqt, alias, sc);
	}

	@Override
	public void insertTable(TableInstance ti) {
		getScope().insertTable(ti);
	}

	@Override
	public TableInstance lookupTableInstance(SchemaContext sc, Name given, boolean required) {
		return getScope().lookupTableInstance(sc, given, required);
	}	
	
	@Override
	public ListSet<NameInstance> getUnresolvedChildren() {
		return getScope().getUnresolvedChildren();
	}

}

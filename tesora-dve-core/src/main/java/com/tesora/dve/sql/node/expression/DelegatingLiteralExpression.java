package com.tesora.dve.sql.node.expression;

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

import com.tesora.dve.sql.node.LanguageNode;
import com.tesora.dve.sql.parser.SourceLocation;
import com.tesora.dve.sql.schema.ConnectionValues;
import com.tesora.dve.sql.schema.UnqualifiedName;
import com.tesora.dve.sql.schema.cache.IDelegatingLiteralExpression;
import com.tesora.dve.sql.schema.cache.ILiteralExpression;
import com.tesora.dve.sql.transform.CopyContext;

public class DelegatingLiteralExpression extends LiteralExpression implements IDelegatingLiteralExpression {

	protected int position;
	protected ValueSource source;
	protected Boolean hasValue = null;
	
	public  DelegatingLiteralExpression(int tt, SourceLocation sloc, ValueSource vs, int position, UnqualifiedName charsetHint) {
		this(tt,sloc,vs,position,charsetHint,false);
	}

	protected DelegatingLiteralExpression(int tt, SourceLocation sloc, ValueSource vs, int position, UnqualifiedName charsetHint, 
			boolean nullSrcOk) {
		super(tt,sloc,charsetHint);
		this.position = position;
		source = vs;
		if (source == null && !nullSrcOk) 
			throw new IllegalStateException("should be created with source");
		if (sloc != null && sloc.getPositionInLine() == -1) throw new IllegalStateException("Invalid source position");		
	}
	
	protected DelegatingLiteralExpression(DelegatingLiteralExpression dle) {
		super(dle);
		position = dle.position;
		source = dle.source;
	}
	
	@Override
	public Object getValue(ConnectionValues cv) {
		if (source != null) return source.getLiteral(this);
		Object v = cv.getLiteral(this);
		if (hasValue == null)
			hasValue = v != null;
		else if (v == null && hasValue.booleanValue())
			throw new IllegalStateException("mismatch");
		return v;
	}
	
	@Override
	protected LanguageNode copySelf(CopyContext cc) {
		return new DelegatingLiteralExpression(this);
	}
	
	@Override
	public int getPosition() {
		return position;
	}
	
	public void setPosition(int p, boolean clearSource) {
		position = p;
		if (clearSource)
			source = null;
	}

	@Override
	public ILiteralExpression getCacheExpression() {
		if (source == null)
			return new CachedDelegatingLiteralExpression(getValueType(), position, getCharsetHint());
		return this;
	}
	
	@Override
	protected boolean schemaSelfEqual(LanguageNode other) {
		if (!super.schemaSelfEqual(other))
			return false;
		DelegatingLiteralExpression odle = (DelegatingLiteralExpression) other;
		return position == odle.position;
	}

	@Override
	protected int selfHashCode() {
		return addSchemaHash(super.selfHashCode(),position);
	}

}

package com.tesora.dve.sql.schema.types;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.tesora.dve.common.PEStringUtils;
import com.tesora.dve.common.catalog.UserColumn;
import com.tesora.dve.db.NativeType;
import com.tesora.dve.db.NativeTypeCatalog;
import com.tesora.dve.db.mysql.MysqlNativeType.MysqlType;
import com.tesora.dve.sql.node.expression.LiteralExpression;
import com.tesora.dve.sql.schema.UnqualifiedName;
import com.tesora.dve.sql.schema.modifiers.TypeModifier;
import com.tesora.dve.sql.util.Functional;

public final class DBEnumType extends TextType {

	List<LiteralExpression> values;
	private final boolean set;
	
	public static Type buildType(UserColumn uc, NativeTypeCatalog types) {
		boolean isSet = "set".equalsIgnoreCase(uc.getTypeName());
		List<String> values = null;
		if (uc.getESUniverse() == null)
			values = Collections.EMPTY_LIST;
		else
			values = Arrays.asList(uc.getESUniverse().split(","));
		return makeFromStrings(isSet, values, BasicType.buildModifiers(uc),types);
	}
	
	public static DBEnumType makeFromStrings(boolean isSet, List<String> values, List<TypeModifier> mods, NativeTypeCatalog typeCatalog) {
		final List<LiteralExpression> valueExpressions = new ArrayList<LiteralExpression>(values.size());
		for (final String value : values) {
			final String noQuotesValue = PEStringUtils.dequote(value);
			if (PEStringUtils.isHexNumber(noQuotesValue)) {
				valueExpressions.add(LiteralExpression.makeHexStringLiteral(noQuotesValue));
			} else {
				valueExpressions.add(LiteralExpression.makeStringLiteral(noQuotesValue));
			}
		}
		return make(isSet, valueExpressions, mods, typeCatalog);
	}

	public static DBEnumType make(boolean isSet, List<LiteralExpression> values, List<TypeModifier> mods, NativeTypeCatalog typeCatalog) {
		NativeType backingType = null;
		int size = 0;
		if (values.size() < 256) {
			size = 1;
			backingType = BasicType.lookupNativeType(MysqlType.TINYINT.toString(), typeCatalog);
		} else {
			size = 2;
			backingType = BasicType.lookupNativeType(MysqlType.SMALLINT.toString(), typeCatalog);
		}
		FlagsAndModifiers fam = buildFlagsAndModifiers(mods);
		return new DBEnumType(isSet, values, backingType, fam.flags, size, fam.charset, fam.collation);
	}

	private DBEnumType(boolean isSet, List<LiteralExpression> values, NativeType bt, short flags, int size,
			UnqualifiedName charSet, UnqualifiedName collation) {
		super(bt,flags,size,charSet,collation);
		this.values = values;
		this.set = isSet;
	}
	
	@Override
	public String getTypeName() {
		return getEnumerationTypeName() + "(" + Functional.joinToString(values, ",") + ")";
	}

	@Override
	public void persistTypeName(UserColumn uc) {
		uc.setTypeName(set ? "set" : "enum");
		uc.setESUniverse(Functional.joinToString(values,","));
	}

	
	public String getEnumerationTypeName() {
		final MysqlType type = (set) ? MysqlType.SET : MysqlType.ENUM;
		return type.toString();
	}
	
	@Override
	public boolean declUsesSizing() {
		return false;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((values == null) ? 0 : values.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		DBEnumType other = (DBEnumType) obj;
		if (values == null) {
			if (other.values != null)
				return false;
		} else if (!values.equals(other.values)) {
			return false;
		}
		return true;
	}
	
	@Override
	public boolean isAcceptableRangeType() {
		// never
		return false;
	}

	/**
	 * @param order 1-based index into the enum.
	 */
	public LiteralExpression getValueAt(final int order) {
		return values.get(order - 1);
	}
	
}

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

import java.nio.charset.Charset;
import java.sql.ParameterMetaData;
import java.sql.SQLException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.tesora.dve.charset.NativeCharSetCatalog;
import com.tesora.dve.charset.NativeCollationCatalog;
import com.tesora.dve.common.DBType;
import com.tesora.dve.common.catalog.User;
import com.tesora.dve.common.catalog.UserColumn;
import com.tesora.dve.db.DBNative;
import com.tesora.dve.db.Emitter;
import com.tesora.dve.db.NativeType;
import com.tesora.dve.db.mysql.MysqlNativeType.MysqlType;
import com.tesora.dve.db.mysql.portal.protocol.MSPAuthenticateV10MessageMessage;
import com.tesora.dve.errmap.AvailableErrors;
import com.tesora.dve.errmap.ErrorInfo;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.resultset.ColumnMetadata;
import com.tesora.dve.server.connectionmanager.SSConnection;
import com.tesora.dve.server.global.HostService;
import com.tesora.dve.server.messaging.SQLCommand;
import com.tesora.dve.singleton.Singletons;
import com.tesora.dve.sql.SchemaException;
import com.tesora.dve.sql.schema.ForeignKeyAction;
import com.tesora.dve.sql.schema.types.Type;
import com.tesora.dve.variables.VariableStoreSource;

public class MysqlNative extends DBNative {
    
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(MysqlNative.class);

	private final NativeCharSetCatalog supportedCharsets;
	private final NativeCollationCatalog supportedCollations;


	public MysqlNative(NativeCharSetCatalog charSetCat, NativeCollationCatalog nativeColCat, DBType dbType) throws PEException {
		this.supportedCharsets = charSetCat;
		this.supportedCollations = nativeColCat;
		super.setDbType(dbType);
		setIdentifierQuoteChar(MysqlNativeConstants.MYSQL_IDENTIFIER_QUOTE_CHAR);
		setLiteralQuoteChar(MysqlNativeConstants.MYSQL_LITERAL_QUOTE_CHAR);
		MysqlNativeTypeCatalog types = new MysqlNativeTypeCatalog();
		setTypeCatalog(types);
		setEmitter(new MysqlEmitter());
		setResultHandler(new MysqlNativeResultHandler());
		setCharsetEncoding(MysqlNativeConstants.MYSQL_CHARSET_ENCODING);
	}

	@Override
	public NativeCharSetCatalog getSupportedCharSets() {
		return supportedCharsets;
	}

	@Override
	public NativeCollationCatalog getSupportedCollations() {
		return supportedCollations;
	}

	public MysqlNativeType getNativeTypeFromMyFieldType(MyFieldType mft, int flags, long maxLen) throws PEException {
		final MysqlNativeTypeCatalog typeCatalog = (MysqlNativeTypeCatalog) Singletons.require(DBNative.class).getTypeCatalog();
		return typeCatalog.findType(mft, flags, maxLen, true);
	}

	@Override 
	public String getColumnDefForQuery(UserColumn uc) throws PEException {
		StringBuilder columnDef = new StringBuilder();
		columnDef.append(getNameForQuery(uc))
			.append(" ")
			.append(getDataTypeForQuery(uc,true));

		return columnDef.toString();
	}
	
	private String getDataTypeForQuery(UserColumn uc, boolean extras) throws PEException {
		MysqlNativeType typeInfo = (MysqlNativeType) findType(uc.getTypeName());

		String typeName = typeInfo.getTypeName();
		// DAS - HACK - we need to remove the "ALT" from the 3 BLOB types
		if ( typeName.startsWith("alt") ) { 
			typeName = typeName.substring(3);	// remove the "ALT"
		}

		StringBuilder sb = new StringBuilder(typeName);
		
		if (MysqlType.ENUM.equals(typeInfo.getMysqlType())) {
			// with enum, use the full type, including values
			sb = new StringBuilder(uc.getTypeName());
		} else if (!typeInfo.getSupportsPrecision()) {
			// type requires neither precision nor scale, so we're done
		} else if (typeInfo.getSupportsPrecision() && !typeInfo.getSupportsScale()) {
			// type has precision
			String precision = Long.toString(uc.getSize());
			if (uc.getSize() == 0) {
				// the uc has a size of 0 which means the user probably specified it like integer instead of integer(n)
				// grab the default precision from the type info instead
				precision = Long.toString(typeInfo.getPrecision());
			}
			sb.append('(').append(precision).append(')');
		} else {
			// type has precision and scale
			MysqlType mysqlType = typeInfo.getMysqlType();
			// don't output precision and scale if precision is 0
			if (uc.getPrecision() > 0) {
				// handle special case for MYSQL - DOUBLE with no (p,s) comes back as (22,31) which is invalid
				if ((MysqlType.DOUBLE.equals(mysqlType) || MysqlType.DOUBLE_PRECISION.equals(mysqlType)
								|| MysqlType.FLOAT.equals(mysqlType))
						&& uc.getScale() == 31) {
					// do nothing
				} else {
					sb.append('(').append(uc.getPrecision()).append(',').append(uc.getScale()).append(')');
				}
			} else {
				// special case for decimal and numeric if 0 is specified
				// for precision
//				if ((MysqlType.DECIMAL.equals(mysqlType) || MysqlType.NUMERIC.equals(mysqlType))
				if ((MysqlType.DECIMAL.equals(mysqlType))
						&& uc.getScale() == 0) {
					if (uc.getSize() > 0) {
						// can specify the size without scale - make sure it isn't larger than max for create table (see PE-1232)
						sb.append('(').append(uc.getSize()).append(",0)");
					} else {
						// figure out the default size
						sb.append('(').append(typeInfo.getPrecision()).append(",0)");
					}
				}
			}
		}

		if (uc.getESUniverse() != null)
			sb.append("(").append(uc.getESUniverse()).append(")");
		
		if (extras && typeInfo.isStringType()) {
			if (uc.getCharset() != null && !StringUtils.equalsIgnoreCase(MysqlNativeConstants.DB_CHAR_SET, uc.getCharset())) {
				sb.append(" CHARACTER SET ").append(uc.getCharset());
			}
			if (uc.getCollation() != null) {
				sb.append(" COLLATE ").append(uc.getCollation());
			}
		}
		
		// add any type modifiers to the end
		if (uc.isUnsigned())
			sb.append(" unsigned");
		if (uc.isZerofill())
			sb.append(" zerofill");

		return sb.toString();
		
	}
	
	@Override
	public String getDataTypeForQuery(UserColumn uc) throws PEException {
		return getDataTypeForQuery(uc,false);
	}

	/*
	@Override
	public ColumnMetadata getResultSetColumnInfo(ResultSetMetaData rsmd, ProjectionInfo projection, int colIdx)
			throws SQLException {
		// TODO this doesn't handle default values
		String nativeTypeName = NativeType.fixName(rsmd.getColumnTypeName(colIdx));

		ColumnMetadata cm = new ColumnMetadata();
		if (projection != null) {
			ColumnInfo ci = projection.getColumnInfo(colIdx);
			cm.setName(ci.getName());
			cm.setAliasName(ci.getAlias());
			cm.setDbName(ci.getDatabaseName());
			cm.setTableName(ci.getTableName());
			if (ci.isSet(ColumnAttribute.AUTO_INCREMENT))
				cm.setAutoGenerated(Boolean.TRUE);
			if (ci.isSet(ColumnAttribute.NULLABLE))
				cm.setNullable(Boolean.TRUE);
			if (ci.isSet(ColumnAttribute.KEY_PART)) {
				if (ci.isSet(ColumnAttribute.PRIMARY_KEY_PART))
					cm.primaryKey();
				else if (ci.isSet(ColumnAttribute.UNIQUE_PART))
					cm.uniqueKey();
				else
					cm.nonUniqueKey();
			}
		} else {
			cm.setName(rsmd.getColumnName(colIdx));
			cm.setAliasName(rsmd.getColumnLabel(colIdx));
		}
		cm.setDataType(rsmd.getColumnType(colIdx));

		// for NativeTypeName we need to handle UNSIGNED. The
		// getColumnTypeName() method returns
		// UNSIGNED as part of the type name instead of a modifier to the type
		cm.setNativeTypeName(nativeTypeName);

		if (!rsmd.isSigned(colIdx) && nativeTypeName.contains(" " + MysqlNativeType.MODIFIER_UNSIGNED)) {
			cm.setNativeTypeName(nativeTypeName.replace(" " + MysqlNativeType.MODIFIER_UNSIGNED, ""));
			cm.setNativeTypeModifiers(MysqlNativeType.MODIFIER_UNSIGNED);
		}


		if (rsmd.getColumnType(colIdx) == Types.LONGVARCHAR) {
			// Mysql JDBC seems to return native type "VARCHAR" when the type is "TEXT"
			// or we could read the manual: 
			// http://dev.mysql.com/doc/refman/5.5/en/connector-j-reference-type-conversions.html
			int prec = rsmd.getPrecision(colIdx);
			if (prec > 16777215)
				cm.setNativeTypeName(MysqlType.LONGTEXT.toString());
			else if (prec > 65535)
				cm.setNativeTypeName(MysqlType.MEDIUMTEXT.toString());
			else if (prec > 255)
				cm.setNativeTypeName(MysqlType.TEXT.toString());
			else
				cm.setNativeTypeName(MysqlType.TINYTEXT.toString());
			// Precision and size will be set here even though they are the same as
			// all other types. We may need to alter these based on Charset at some point
			cm.setPrecision(rsmd.getPrecision(colIdx));
			cm.setSize(rsmd.getColumnDisplaySize(colIdx));
		} else {
			cm.setPrecision(rsmd.getPrecision(colIdx));
			cm.setSize(rsmd.getColumnDisplaySize(colIdx));
		}

		cm.setScale(rsmd.getScale(colIdx));

		return cm;
	}
	*/

	@Override
	public ColumnMetadata getParameterColumnInfo(ParameterMetaData pmd, int colIdx) throws SQLException {
		ColumnMetadata out = new ColumnMetadata();
		out.setDataType(pmd.getParameterType(colIdx));
		out.setPrecision(pmd.getPrecision(colIdx));
		out.setScale(pmd.getScale(colIdx));
		if (ParameterMetaData.parameterNullable == pmd.isNullable(colIdx))
			out.setNullable(Boolean.TRUE);
		out.setTypeName(NativeType.fixName(pmd.getParameterTypeName(colIdx)));
		return out;
	}
	
	@Override
	public UserColumn updateUserColumn(UserColumn iuc, Type schemaType) {
		UserColumn uc = (iuc == null ? new UserColumn() : iuc);
		schemaType.persistTypeName(uc);
		uc.setDataType(schemaType.getDataType());
		if (schemaType.hasSize()) {
			uc.setSize(schemaType.getSize());
			if (schemaType.hasPrecisionAndScale()) {
				uc.setPrecision(schemaType.getPrecision());
				uc.setScale(schemaType.getScale());
			} else if (uc.getId() != 0) {
				uc.setPrecision(0);
				uc.setScale(0);
			}
		} else {
			uc.setSize(0);
			uc.setPrecision(0);
			uc.setScale(0);			
		}
		schemaType.addColumnTypeModifiers(uc);

		return uc;
	}

	@Override
	public Emitter getEmitter() {
		return new MysqlEmitter();
	}

	@Override
	public SQLCommand getDropDatabaseStmt(final VariableStoreSource vs, String databaseName) {
		return new SQLCommand(vs, buildDropDatabaseStmt(databaseName));
	}

	@Override
	public SQLCommand getCreateDatabaseStmt(final VariableStoreSource vs, String databaseName, boolean ine, String defaultCharSet, String defaultCollation) {
		return new SQLCommand(vs, buildCreateDatabaseStmt(databaseName, ine, defaultCharSet, defaultCollation));
	}

	@Override
	public SQLCommand getAlterDatabaseStmt(final VariableStoreSource vs, String databaseName, String defaultCharSet, String defaultCollation) {
		return new SQLCommand(vs, buildAlterDatabaseStmt(databaseName, defaultCharSet, defaultCollation));
	}

	@Override
	public SQLCommand getCreateUserCommand(final VariableStoreSource vs, User user) {
		return new SQLCommand(vs, buildCreateUserCommand(user));
	}

	@Override
	public SQLCommand getGrantPriviledgesCommand(final VariableStoreSource vs, String userDeclaration, String databaseName) {
		return new SQLCommand(vs, buildGrantPriviledgesCommand(userDeclaration, databaseName));
	}

	@Override
	public SQLCommand getDropDatabaseStmt(final Charset connectionCharset, String databaseName) {
		return new SQLCommand(connectionCharset, buildDropDatabaseStmt(databaseName));
	}

	@Override
	public SQLCommand getCreateDatabaseStmt(final Charset connectionCharset, String databaseName, boolean ine, String defaultCharSet, String defaultCollation) {
		return new SQLCommand(connectionCharset, buildCreateDatabaseStmt(databaseName, ine, defaultCharSet, defaultCollation));
	}

	@Override
	public SQLCommand getAlterDatabaseStmt(final Charset connectionCharset, String databaseName, String defaultCharSet, String defaultCollation) {
		return new SQLCommand(connectionCharset, buildAlterDatabaseStmt(databaseName, defaultCharSet, defaultCollation));
	}

	@Override
	public SQLCommand getCreateUserCommand(final Charset connectionCharset, User user) {
		return new SQLCommand(connectionCharset, buildCreateUserCommand(user));
	}

	@Override
	public SQLCommand getGrantPriviledgesCommand(final Charset connectionCharset, String userDeclaration, String databaseName) {
		return new SQLCommand(connectionCharset, buildGrantPriviledgesCommand(userDeclaration, databaseName));
	}

	private String buildDropDatabaseStmt(String databaseName) {
		return "DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName);
	}

	private String buildCreateDatabaseStmt(String databaseName, boolean ine, String defaultCharSet, String defaultCollation) {
		final StringBuilder command = new StringBuilder("CREATE DATABASE ");
		if (ine) {
			command.append("IF NOT EXISTS ");
		}
		command.append(quoteIdentifier(databaseName));
		command.append(" DEFAULT CHARACTER SET = ").append(defaultCharSet);
		command.append(" DEFAULT COLLATE = ").append(defaultCollation);

		return command.toString();
	}

	private String buildAlterDatabaseStmt(String databaseName, String defaultCharSet, String defaultCollation) {
		final StringBuilder command = new StringBuilder("ALTER DATABASE ");
		command.append(quoteIdentifier(databaseName));
		command.append(" DEFAULT CHARACTER SET = ").append(defaultCharSet);
		command.append(" DEFAULT COLLATE = ").append(defaultCollation);

		return command.toString();
	}

	private String buildCreateUserCommand(User user) {
		// switch to doing a grant all - lets create be idempotent
		return buildGrantPriviledgesCommand(getUserDeclaration(user, true), "*");
	}

	private String buildGrantPriviledgesCommand(String userDeclaration, String databaseName) {
		return "GRANT ALL PRIVILEGES ON " + databaseName + ".* TO " + userDeclaration;
	}

	@Override
	public String getUserDeclaration(User user, boolean pword) {
		StringBuffer buf = new StringBuffer();
		buf.append("'").append(user.getName()).append("'@'").append(user.getAccessSpec()).append("'"); // NOPMD by doug on 30/11/12 3:23 PM
		if (pword && user.getPlaintextPassword() != null)
			buf.append(" IDENTIFIED BY '").append(user.getPlaintextPassword()).append("'"); // NOPMD by doug on 30/11/12 3:23 PM
		return buf.toString();
	}

    @Override
	public String getEmptyCatalogName() {
		return "mysql";
	}

	@Override
	public String getPasswordForAuth(User user, SSConnection ssConn) throws Exception {
		return MSPAuthenticateV10MessageMessage.computeSecurePasswordString(user.getPlaintextPassword(), ssConn.getHandshake().getSalt());
	}

	@Override
	public String getSessionVariableConfigName() {
		return "com/tesora/dve/db/mysql/mysqlSessionConfig.xml";
	}

	@Override
	public String getStatusVariableConfigName() {
		return "com/tesora/dve/db/mysql/mysqlStatusConfig.xml";
	}

	@Override
	public String getSetSessionVariableStatement(String assignmentClause) {
		return "SET SESSION " + assignmentClause;
	}

	@Override
	public String getSetAutocommitStatement(String value) {
		return "SET autocommit = " + value;
	}

	@Override
	public void assertValidCharacterSet(String value) throws PEException {
		if (!getSupportedCharSets().isCompatibleCharacterSet(value)) {
			throw new SchemaException(new ErrorInfo(AvailableErrors.UNKNOWN_CHARACTER_SET, value));
		}
	}

    @Override
    public void assertValidCollation(String value) throws PEException {
		if (!getSupportedCollations().isCompatibleCollation(value)) {
			throw new SchemaException(new ErrorInfo(AvailableErrors.UNKNOWN_COLLATION, value));
		}
    }

	@Override
	public String getDefaultServerCharacterSet() {
		return "utf8";
	}

	@Override
	public String getDefaultServerCollation() {
		return "utf8_general_ci";
	}

	@Override
	public String getDefaultServerBinaryCollation() {
		return "utf8_bin";
	}
	
	@Override
	public boolean beginImpliesCommit() {
		return true;
	}

	@Override
	public boolean ddlImpliesCommit() {
		return true;
	}

	@Override
	public boolean exceptionAbortsTxn(PEException e) {
//		return e.hasCause(MySQLTransactionRollbackException.class);
		return false;
	}

	@Override
	public int convertTransactionIsolationLevel(String in) throws PEException {
		if (in == null)
			throw new PEException("Missing isolation level");
		MySQLTransactionIsolation isol = MySQLTransactionIsolation.find(in);
		if (isol == null)
			throw new PEException("Unknown isolation level '" + in + "'");
		return isol.getJdbcConstant();
	}

	// use the mysql names - the ones you'd see in the show variables listing
	@Override
	public String convertTransactionIsolationLevel(int level) throws PEException {
		MySQLTransactionIsolation isol = MySQLTransactionIsolation.find(level);
		if (isol == null)
			throw new PEException("Unknown transaction isolation level value: " + level);
		return isol.getExternalName();
	}

	@Override
	public String getTableRenameStatement(String existingTableName, String newTableName) {
		return "ALTER TABLE " + quoteIdentifier(existingTableName) + " RENAME " + quoteIdentifier(newTableName);
	}

	@Override
	public int getMaxAliasNameLen() {
		return 64;
	}

	@Override
	public void convertColumnMetadataToUserColumn(ColumnMetadata cm, UserColumn uc)
			throws PEException {
		super.convertColumnMetadataToUserColumn(cm, uc);
		NativeType nt = this.findType(uc.getTypeName());
		if (nt.isNumericType()) {
			if (uc.getPrecision() > nt.getMaxPrecision()) {
				uc.setPrecision((int) nt.getMaxPrecision());
			}
			if (uc.getSize() > nt.getMaxPrecision()) {
				uc.setSize((int) nt.getMaxPrecision());
			}
		} else {
			// A non-numeric type with scale/precision may be returned.
			// @see coallesce(VARCHAR, VARCHAR)
			// Clear the properties.
			uc.setPrecision(0);
			uc.setScale(0);
		}
	}

	@Override
	public int getMaxNumColsInIndex() {
		return 16;
	}

	@Override
	public ForeignKeyAction getDefaultOnDeleteAction() {
		return ForeignKeyAction.RESTRICT;
	}

	@Override
	public ForeignKeyAction getDefaultOnUpdateAction() {
		return ForeignKeyAction.RESTRICT;
	}

	@Override
	public long getMaxTableCommentLength() {
		return 2048;
	}

	@Override
	public long getMaxTableFieldCommentLength() {
		return 1024;
	}
}

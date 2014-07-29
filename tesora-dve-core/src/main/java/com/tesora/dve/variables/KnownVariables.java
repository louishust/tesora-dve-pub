package com.tesora.dve.variables;

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


import java.util.EnumSet;

import org.apache.commons.lang.StringUtils;

import com.tesora.dve.charset.NativeCharSet;
import com.tesora.dve.charset.mysql.MysqlNativeCharSet;
import com.tesora.dve.charset.mysql.MysqlNativeCharSetCatalog;
import com.tesora.dve.clock.TimingServiceConfiguration;
import com.tesora.dve.common.PEConstants;
import com.tesora.dve.common.PEThreadContext;
import com.tesora.dve.common.catalog.AutoIncrementTracker;
import com.tesora.dve.common.catalog.CatalogDAO;
import com.tesora.dve.common.catalog.CatalogDAO.CatalogDAOFactory;
import com.tesora.dve.common.catalog.CatalogEntity;
import com.tesora.dve.common.catalog.Project;
import com.tesora.dve.common.catalog.TemplateMode;
import com.tesora.dve.db.mysql.MySQLTransactionIsolation;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.groupmanager.GroupManager;
import com.tesora.dve.server.connectionmanager.ConnectionSemaphore;
import com.tesora.dve.server.connectionmanager.SSConnection;
import com.tesora.dve.server.connectionmanager.SlowQueryLogger;
import com.tesora.dve.server.global.BootstrapHostService;
import com.tesora.dve.server.global.HostService;
import com.tesora.dve.server.global.MySqlPortalService;
import com.tesora.dve.singleton.Singletons;
import com.tesora.dve.siteprovider.onpremise.OnPremiseSiteProvider;
import com.tesora.dve.sql.parser.InvokeParser;
import com.tesora.dve.sql.schema.SQLMode;
import com.tesora.dve.sql.schema.VariableScopeKind;
import com.tesora.dve.sql.schema.cache.CacheSegment;
import com.tesora.dve.sql.schema.cache.SchemaSourceFactory;
import com.tesora.dve.sql.schema.modifiers.EngineTableModifier.EngineTag;
import com.tesora.dve.variable.VariableConstants;

public class KnownVariables implements VariableConstants {

	public static final ValueMetadata<Long> integralConverter = new IntegralValueConverter();
	public static final ValueMetadata<Boolean> booleanConverter = new BooleanValueConverter();
	public static final ValueMetadata<Double> floatingPointConverter = new FloatingPointValueConverter();
	public static final ValueMetadata<String> stringConverter = new StringValueConverter();
	public static final ValueMetadata<String> literalConverter = new LiteralValueConverter();

	public static final EnumSet<VariableScopeKind> globalScope = EnumSet.of(VariableScopeKind.GLOBAL);
	public static final EnumSet<VariableScopeKind> sessionScope = EnumSet.of(VariableScopeKind.SESSION);
	public static final EnumSet<VariableScopeKind> bothScope = EnumSet.of(VariableScopeKind.GLOBAL,VariableScopeKind.SESSION);
	
	public static final EnumSet<VariableOption> dveOnly = EnumSet.of(VariableOption.DVE_ONLY);
	public static final EnumSet<VariableOption> noopt = EnumSet.noneOf(VariableOption.class);
	public static final EnumSet<VariableOption> nullable = EnumSet.of(VariableOption.NULLABLE);
	public static final EnumSet<VariableOption> readonly = EnumSet.of(VariableOption.READONLY);
	
	@SuppressWarnings("rawtypes")
	static final ValueMetadata[] defaultConverters = new ValueMetadata[] {
		integralConverter,
		booleanConverter,
		floatingPointConverter,
		stringConverter,
		literalConverter
	};

	
	public static final VariableHandler<Long> LARGE_INSERT_CUTOFF =
			new VariableHandler<Long>(LARGE_INSERT_THRESHOLD_NAME,
					integralConverter,
					globalScope,
					InvokeParser.defaultLargeInsertThreshold,
					dveOnly);
	public static final VariableHandler<Long> CACHED_PLAN_LITERALS_MAX =
			new VariableHandler<Long>(MAX_CACHED_LITERALS_NAME,
					integralConverter,
					globalScope,
					100L,
					dveOnly);
	public static final VariableHandler<Boolean> SLOW_QUERY_LOG =
			new VariableHandler<Boolean>(SLOW_QUERY_LOG_NAME,
					booleanConverter,
					globalScope,
					Boolean.FALSE,
					noopt) {

		@Override
		public void onGlobalValueChange(Boolean newValue) throws PEException {
			SlowQueryLogger.enableSlowQueryLogger(newValue);
		}
	};
	public static final VariableHandler<Long> LONG_PLAN_STEP_TIME =
			new VariableHandler<Long>(LONG_PLAN_STEP_TIME_NAME,
					integralConverter,
					globalScope,
					3L,
					dveOnly);
	public static final VariableHandler<Double> LONG_QUERY_TIME =
			new VariableHandler<Double>(LONG_QUERY_TIME_NAME,
					floatingPointConverter,
					bothScope,
					new Double(10.0),
					noopt);
	public static final VariableHandler<Boolean> EMULATE_MYSQL_LIMIT =
			new VariableHandler<Boolean>(LIMIT_ORDERBY_EMULATION_NAME,
					booleanConverter,
					globalScope,
					Boolean.FALSE,
					dveOnly);
	public static final VariableHandler<Boolean> BALANCE_PERSISTENT_GROUPS =
			new VariableHandler<Boolean>(BALANCE_PERSISTENT_GROUPS_NAME,
					booleanConverter,
					globalScope,
					Boolean.FALSE,
					dveOnly);
	public static final VariableHandler<String> BALANCE_PERSISTENT_GROUPS_PREFIX =
			new VariableHandler<String>(BALANCE_PERSISTENT_GROUPS_PREFIX_NAME,
					stringConverter,
					globalScope,
					"",
					dveOnly);
	public static final VariableHandler<String> DYNAMIC_POLICY =
			new VariableHandler<String>(DYNAMIC_POLICY_NAME,
					new StringValueConverter() {
				
						@Override
						public String convertToInternal(String varName, String in) throws PEException {
							String raw = stringConverter.convertToInternal(varName, in);
							CatalogDAO c = CatalogDAOFactory.newInstance();
							try {
								c.findDynamicPolicy(raw, true);
							} finally {
								c.close();
							}
							return raw;
						}

					},
					bothScope,
					OnPremiseSiteProvider.DEFAULT_POLICY_NAME,
					dveOnly) {
		
		// set it in both places for now
		@Override
		protected void persistValue(final CatalogDAO c, final String newValue) throws PEException {
			super.persistValue(c, newValue);
			final Project p = c.findByKey(Project.class, Singletons.require(HostService.class).getProject().getId());
			try {
				c.new EntityUpdater() {
					@Override
					public CatalogEntity update() throws Throwable {
						p.setDefaultPolicy(c.findDynamicPolicy(newValue));
						return p;
					}
				}.execute();
			} catch (Throwable e) {
				throw new PEException("Unable to set project variable persistent_group",e);
			}
		}

		
	};
	public static final VariableHandler<Boolean> STEPWISE_STATISTICS =
			new VariableHandler<Boolean>(STEPWISE_STATISTICS_NAME,
					booleanConverter,
					globalScope,
					Boolean.FALSE,
					dveOnly);
	public static final VariableHandler<Boolean> COST_BASED_PLANNING =
			new VariableHandler<Boolean>(CARDINALITY_COSTING_NAME,
					booleanConverter,
					globalScope,
					Boolean.TRUE,
					dveOnly);
	public static final VariableHandler<TemplateMode> TEMPLATE_MODE =
			new VariableHandler<TemplateMode>(TEMPLATE_MODE_NAME,
					new EnumValueConverter<TemplateMode>(TemplateMode.values()),
					globalScope,
					TemplateMode.REQUIRED,
					dveOnly);
	public static final VariableHandler<Boolean> SHOW_METADATA_EXTENSIONS =
			new VariableHandler<Boolean>(SHOW_METADATA_EXTENSIONS_NAME,
					booleanConverter,
					sessionScope,
					Boolean.FALSE,
					dveOnly);
	public static final VariableHandler<Boolean> OMIT_DIST_COMMENTS =
			new VariableHandler<Boolean>(OMIT_DIST_COMMENTS_NAME,
					booleanConverter,
					sessionScope,
					Boolean.FALSE,
					dveOnly);
	public static final VariableHandler<Long> SELECT_LIMIT =
			new VariableHandler<Long>(SQL_SELECT_LIMIT_NAME,
					new IntegralValueConverter() {
				@Override
				public Long convertToInternal(String varName, String in) throws PEException {
					if ("DEFAULT".equalsIgnoreCase(in))
						return null;
					return super.convertToInternal(varName, in);
				}
				@Override
				public String convertToExternal(Long in) {
					if (in == null) return "DEFAULT";
					return Long.toString(in);
				}

			},
			sessionScope,
			null,
			EnumSet.of(VariableOption.NULLABLE,VariableOption.NO_SESSION_ASSIGNMENT));
	public static final VariableHandler<Boolean> FOREIGN_KEY_CHECKS =
			new VariableHandler<Boolean>(FOREIGN_KEY_CHECKS_NAME,
					booleanConverter,
					bothScope,
					Boolean.TRUE,
					noopt);
	public static final VariableHandler<EngineTag> STORAGE_ENGINE =
			new VariableHandler<EngineTag>(STORAGE_ENGINE_NAME,
					new EnumValueConverter<EngineTag>(EngineTag.values()),
					bothScope,
					EngineTag.INNODB,
					noopt);
	public static final VariableHandler<Long> REPL_INSERT_ID =
			new VariableHandler<Long>(REPL_SLAVE_INSERT_ID_NAME,
					integralConverter,
					sessionScope,
					null,
					EnumSet.of(VariableOption.DVE_ONLY, VariableOption.NULLABLE));
	public static final VariableHandler<Long> REPL_TIMESTAMP =
			new VariableHandler<Long>(REPL_SLAVE_TIMESTAMP_NAME,
					integralConverter,
					sessionScope,
					null,
					EnumSet.of(VariableOption.DVE_ONLY, VariableOption.NULLABLE));
	public static final VariableHandler<SQLMode> SQL_MODE =
			new VariableHandler<SQLMode>("sql_mode",
					new ValueMetadata<SQLMode>() {

						@Override
						public SQLMode convertToInternal(String varName, String in)
								throws PEException {
							String raw = stringConverter.convertToInternal(varName, in);
							return new SQLMode(raw);
						}

						@Override
						public String convertToExternal(SQLMode in) {
							return String.format("'%s'",in.toString());
						}

						@Override
						public String getTypeName() {
							return "varchar";
						}

						@Override
						public String toRow(SQLMode in) {
							return in.toString();
						}
				
					},
					bothScope,
					new SQLMode("NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION"),
					noopt) {
		
		@Override
		public void onSessionValueChange(VariableStoreSource conn, SQLMode newValue) throws PEException {
			super.onSessionValueChange(conn,newValue);
		}
		
		@Override
		public void setSessionValue(VariableStoreSource conn, String value) throws PEException {
			// make sure the global values are included
			SQLMode globalValues = getGlobalValue(conn);
			String raw = stringConverter.convertToInternal(getName(), value);
			if ("default".equalsIgnoreCase(raw)) {
				// set the session values to the global values
				super.setSessionValue(conn,globalValues.toString());
			} else {
				// combine
				SQLMode myValues = new SQLMode(raw);
				SQLMode combined = globalValues.add(myValues);
				super.setSessionValue(conn,combined.toString());
			}
		}
	};
	public static final VariableHandler<String> COLLATION_CONNECTION =
			new VariableHandler<String>(COLLATION_CONNECTION_NAME,
					new LiteralValueConverter() {
				
						@Override
						public String convertToInternal(String varName, String in) throws PEException {
							Singletons.require(HostService.class).getDBNative().assertValidCollation(in);
							return in;
						}
				
					},
					bothScope,
					"utf8_general_ci",
					noopt);
	
	public static final VariableHandler<NativeCharSet> CHARACTER_SET_CLIENT =
			new VariableHandler<NativeCharSet>(CHARACTER_SET_CLIENT_NAME,
					new ValueMetadata<NativeCharSet>() {

						@Override
						public NativeCharSet convertToInternal(String varName,
								String in) throws PEException {
							try {
								return MysqlNativeCharSetCatalog.DEFAULT_CATALOG.findCharSetByName(in, true);
							} catch (PEException e) {
								throw new PEException(String.format("Unsupported charset for '%s': %s",varName,in));
							}
						}

						@Override
						public String convertToExternal(NativeCharSet in) {
							return in.getName();
						}

						@Override
						public String toRow(NativeCharSet in) {
							return in.getName();
						}

						@Override
						public String getTypeName() {
							return "varchar";
						}
				
					},
					bothScope,
					MysqlNativeCharSet.UTF8,
					noopt) {
		
		public void onSessionValueChange(VariableStoreSource conn, NativeCharSet in) throws PEException {
			if (conn instanceof SSConnection) {
				SSConnection ssconn = (SSConnection) conn;
				ssconn.setClientCharSet(in);
			}
			super.onSessionValueChange(conn,in);
		}
		
	};
	public static final VariableHandler<Long> CACHE_LIMIT =
			new VariableHandler<Long>("cache_limit",
					integralConverter,
					globalScope,
					10000L,
					dveOnly) {

		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
			SchemaSourceFactory.setCacheSegmentLimit(CacheSegment.lookupSegment(getName()),newValue.intValue());
		}
	};
	public static final VariableHandler<Long> SCOPE_CACHE_LIMIT =
			new VariableHandler<Long>("scope_cache_limit",
					integralConverter,
					globalScope,
					10000L,
					dveOnly) {
		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
			SchemaSourceFactory.setCacheSegmentLimit(CacheSegment.lookupSegment(getName()),newValue.intValue());
		}
		
	};
	public static final VariableHandler<Long> TENANT_CACHE_LIMIT =
			new VariableHandler<Long>("tenant_cache_limit",
					integralConverter,
					globalScope,
					500L,
					dveOnly) {
		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
			SchemaSourceFactory.setCacheSegmentLimit(CacheSegment.lookupSegment(getName()),newValue.intValue());
		}
		
	};
	public static final VariableHandler<Long> TABLE_CACHE_LIMIT =
			new VariableHandler<Long>("table_cache_limit",
					integralConverter,
					globalScope,
					400L,
					dveOnly) {
		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
			SchemaSourceFactory.setCacheSegmentLimit(CacheSegment.lookupSegment(getName()),newValue.intValue());
		}
		
	};
	public static final VariableHandler<Long> PLAN_CACHE_LIMIT =
			new VariableHandler<Long>("plan_cache_limit",
					integralConverter,
					globalScope,
					400L,
					dveOnly) {
		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
			SchemaSourceFactory.setCacheSegmentLimit(CacheSegment.lookupSegment(getName()),newValue.intValue());
		}
		
	};
	public static final VariableHandler<Long> RAW_PLAN_CACHE_LIMIT =
			new VariableHandler<Long>("raw_plan_cache_limit",
					integralConverter,
					globalScope,
					0L,
					dveOnly) {
		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
			SchemaSourceFactory.setCacheSegmentLimit(CacheSegment.lookupSegment(getName()),newValue.intValue());
		}
		
	};
	public static final VariableHandler<Long> TEMPLATE_CACHE_LIMIT =
			new VariableHandler<Long>("template_cache_limit",
					integralConverter,
					globalScope,
					100L,
					dveOnly) {
		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
			SchemaSourceFactory.setCacheSegmentLimit(CacheSegment.lookupSegment(getName()),newValue.intValue());
		}
		
	};
	public static final VariableHandler<Long> MAX_PREPARED_STMT_COUNT =
			new VariableHandler<Long>("max_prepared_stmt_count",
					integralConverter,
					globalScope,
					16382L,
					noopt) {
		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
			SchemaSourceFactory.setCacheSegmentLimit(CacheSegment.lookupSegment(getName()),newValue.intValue());
		}		
	};
	public static final VariableHandler<Long> REDIST_MAX_COLUMNS =
			new VariableHandler<Long>("redist_max_columns",
					integralConverter,
					bothScope,
					1000L,
					dveOnly);
	public static final VariableHandler<Long> REDIST_MAX_SIZE =
			new VariableHandler<Long>("redist_max_size",
					integralConverter,
					bothScope,
					16000000L,
					dveOnly);
	public static final VariableHandler<Long> WAIT_TIMEOUT =
			new VariableHandler<Long>("wait_timeout",
					integralConverter,
					bothScope,
					28800L,
					noopt);
	public static final VariableHandler<Boolean> AUTOCOMMIT =
			new VariableHandler<Boolean>("autocommit",
					booleanConverter,
					bothScope,
					Boolean.TRUE,
					noopt) {
		
		@Override
		public void onSessionValueChange(VariableStoreSource store, Boolean newValue) throws PEException {
			((SSConnection)store).setAutoCommitMode(newValue.booleanValue());
		}
		
		@Override
		public String getSessionAssignmentClause(String value) {
			// does not seem correct
			return "autocommit = 1";
		}
	};
	public static final VariableHandler<String> TIME_ZONE =
			new VariableHandler<String>("time_zone",
					stringConverter,
					bothScope,
					"+00:00",
					noopt);
	public static final VariableHandler<String> PERSISTENT_GROUP =
			new VariableHandler<String>("persistent_group",
					stringConverter,
					bothScope,
					PEConstants.DEFAULT_GROUP_NAME,
					dveOnly) {
		
		@Override
		public void setSessionValue(VariableStoreSource conn, String newValue) throws PEException {
			if (conn instanceof SSConnection) {
				SSConnection scon = (SSConnection) conn;
				scon.getCatalogDAO().findPersistentGroup(newValue, /* check existence */ true);
			}
			super.setSessionValue(conn,newValue);
		}
		
		// set it in both places for now
		@Override
		protected void persistValue(final CatalogDAO c, final String newValue) throws PEException {
			super.persistValue(c, newValue);
			final Project p = c.findByKey(Project.class, Singletons.require(HostService.class).getProject().getId());
			try {
				c.new EntityUpdater() {
					@Override
					public CatalogEntity update() throws Throwable {
						p.setDefaultStorageGroup(c.findPersistentGroup(newValue));
						return p;
					}
				}.execute();
			} catch (Throwable e) {
				throw new PEException("Unable to set project variable persistent_group",e);
			}
		}
		
	};
	public static final VariableHandler<String> GROUP_SERVICE =
			new VariableHandler<String>("group_service",
					new StringValueConverter() {
						@Override
						public String convertToInternal(String varName, String in) throws PEException {
							String raw = super.convertToInternal(varName,in);
							if (!GroupManager.isValidServiceType(raw))
								throw new PEException("Value '" + raw + "' is not valid for variable 'group_service'" );
							return raw;
						}
					},
					globalScope,
					"Localhost",
					dveOnly);
	public static final VariableHandler<Long> MAX_CONCURRENT =
			new VariableHandler<Long>("max_concurrent",
					new BoundedIntegralConverter(1L,5000L),
					globalScope,
					100L,
					dveOnly) {

		@Override
		public void onGlobalValueChange(Long newValue) throws PEException {
	        MySqlPortalService portal = Singletons.lookup(MySqlPortalService.class);
	        if(portal != null)
				portal.setMaxConcurrent(newValue.intValue());
		}
		
	};
	public static final VariableHandler<String> VERSION_COMMENT =
			new VariableHandler<String>("version_comment",
					stringConverter,
					bothScope,
					"",
					EnumSet.of(VariableOption.READONLY, VariableOption.DVE_ONLY)) {
		
		@Override
		public String getValue(VariableStoreSource source, VariableScopeKind vs) {
			return Singletons.require(HostService.class).getServerVersionComment();
		}
		
		@Override
		public String getDefaultOnMissing() {
			return getValue(null,null);
		}
		
	};
	public static final VariableHandler<String> VERSION =
			new VariableHandler<String>("version",
					stringConverter,
					bothScope,
					"5.5.10",
					EnumSet.of(VariableOption.READONLY, VariableOption.DVE_ONLY));
	public static final VariableHandler<Boolean> SQL_LOGGING =
			new VariableHandler<Boolean>("sql_logging",
					booleanConverter,
					globalScope,
					Boolean.FALSE,
					dveOnly) {

		@Override
		public void onGlobalValueChange(Boolean newValue) throws PEException {
			InvokeParser.enableSqlLogging(newValue.booleanValue());
		}
	};
	public static final VariableHandler<Long> MAX_ALLOWED_PACKET =
			new VariableHandler<Long>("max_allowed_packet",
					integralConverter,
					globalScope,
					16777216L,
					noopt);
	
		
	@SuppressWarnings("rawtypes")
	static VariableHandler[] namedHandlers = new VariableHandler[] {
		LARGE_INSERT_CUTOFF,
		CACHED_PLAN_LITERALS_MAX,
		SLOW_QUERY_LOG,
		LONG_PLAN_STEP_TIME,
		LONG_QUERY_TIME,
		EMULATE_MYSQL_LIMIT,
		BALANCE_PERSISTENT_GROUPS,
		BALANCE_PERSISTENT_GROUPS_PREFIX,
		DYNAMIC_POLICY,
		STEPWISE_STATISTICS,
		COST_BASED_PLANNING,
		TEMPLATE_MODE,
		SHOW_METADATA_EXTENSIONS,
		OMIT_DIST_COMMENTS,
		SELECT_LIMIT,
		FOREIGN_KEY_CHECKS,
		STORAGE_ENGINE,
		REPL_INSERT_ID,
		REPL_TIMESTAMP,
		SQL_MODE,
		COLLATION_CONNECTION,
		CHARACTER_SET_CLIENT,
		CACHE_LIMIT,
		SCOPE_CACHE_LIMIT,
		TENANT_CACHE_LIMIT,
		TABLE_CACHE_LIMIT,
		PLAN_CACHE_LIMIT,
		RAW_PLAN_CACHE_LIMIT,
		TEMPLATE_CACHE_LIMIT,
		MAX_PREPARED_STMT_COUNT,
		REDIST_MAX_COLUMNS,
		REDIST_MAX_SIZE,
		WAIT_TIMEOUT,
		AUTOCOMMIT,
		TIME_ZONE,
		PERSISTENT_GROUP,
		GROUP_SERVICE,
		MAX_CONCURRENT,
		VERSION,
		VERSION_COMMENT,
		SQL_LOGGING,
		MAX_ALLOWED_PACKET
	};
	
	@SuppressWarnings("rawtypes")
	static final VariableHandler[] unnamedHandlers = new VariableHandler[] {
		new VariableHandler<Long>("group_concat_max_len",
				integralConverter,
				bothScope,
				1024L,
				noopt) {
			
		},
		new VariableHandler<String>("hostname",
				stringConverter,
				globalScope,
				"",
				EnumSet.of(VariableOption.DVE_ONLY,VariableOption.READONLY)) {
			
			@Override
			public String getValue(VariableStoreSource source, VariableScopeKind vs) {
				return Singletons.require(HostService.class).getHostName();
			}
		},
		new VariableHandler<Long>("auto_increment_increment",
				integralConverter,
				bothScope,
				1L,
				noopt),
		new VariableHandler<String>("character_set_connection",
				stringConverter,
				bothScope,
				"utf8",
				noopt),
		new VariableHandler<String>("character_set_results",
				stringConverter,
				bothScope,
				"utf8",
				nullable),
		new VariableHandler<String>("character_set_server",
				stringConverter,
				bothScope,
				"utf8",
				noopt),
		new VariableHandler<Long>(ADAPTIVE_CLEANUP_INTERVAL_NAME,
				new BoundedIntegralConverter(-1L,10000L),
				globalScope,
				10000L,
				dveOnly) {
			
			public void onGlobalValueChange(Long newValue) throws PEException {
				Singletons.require(BootstrapHostService.class).setTableCleanupInterval(newValue.intValue(),true);
			}
		},
		new VariableHandler<Long>(STATISTICS_INTERVAL_NAME,
				new BoundedIntegralConverter(-1L,1000L),
				globalScope,
				0L,
				dveOnly),
		new VariableHandler<Long>("max_connections",
				new BoundedIntegralConverter(1L,5000L),
				globalScope,
				1000L,
				dveOnly) {
			
			public void onGlobalValueChange(Long newValue) throws PEException {
				SSConnection.setMaxConnections(newValue.intValue());
			}
		},
		new VariableHandler<Long>("connection_mem_requirement",
				integralConverter,
				globalScope,
				1000000L,
				dveOnly) {
			public void onGlobalValueChange(Long newValue) throws PEException {
				ConnectionSemaphore.adjustMinMemory(newValue.intValue());
			}
		},
		new VariableHandler<Boolean>("debug_context",
				booleanConverter,
				globalScope,
				Boolean.FALSE,
				dveOnly) {
			public void onGlobalValueChange(Boolean newValue) throws PEException {
				PEThreadContext.setEnabled(newValue.booleanValue());
			}
		},
		new VariableHandler<Long>("last_insert_id",
				integralConverter,
				sessionScope,
				0L,
				dveOnly),
		new VariableHandler<String>("charset",
				literalConverter,
				sessionScope,
				"utf8",
				dveOnly),
		new VariableHandler<Boolean>("sql_auto_is_null",
				booleanConverter,
				bothScope,
				Boolean.FALSE,
				noopt),
		new VariableHandler<MySQLTransactionIsolation>("tx_isolation",
				new ValueMetadata<MySQLTransactionIsolation>() {

					@Override
					public MySQLTransactionIsolation convertToInternal(String varName, String in) throws PEException {
						String raw = stringConverter.convertToInternal(varName, in);
						MySQLTransactionIsolation isol = MySQLTransactionIsolation.find(raw);
						if (isol == null)
							throw new PEException("Invalid value for '" + varName + "' (allowed values are " + 
									StringUtils.join(MySQLTransactionIsolation.getExternalValuesAsList(), ", ") + ")");
						return isol;
					}

					@Override
					public String convertToExternal(MySQLTransactionIsolation in) {
						if (in == null) return "";
						return String.format("'%s'", in.getExternalName());
					}

					@Override
					public String toRow(MySQLTransactionIsolation in) {
						return in.getExternalName();
					}

					@Override
					public String getTypeName() {
						return "varchar";
					}
				},
				bothScope,
				MySQLTransactionIsolation.READ_COMMITTED,
				noopt),
		new VariableHandler<Boolean>("performance_trace",
				booleanConverter,
				globalScope,
				Boolean.FALSE,
				dveOnly) {

			@Override
			public void onGlobalValueChange(Boolean newValue) throws PEException {
				TimingServiceConfiguration timingConfiguration = Singletons.lookup(TimingServiceConfiguration.class);
				if (timingConfiguration != null)
					timingConfiguration.setTimingEnabled(newValue.booleanValue());
			}
		},
		new VariableHandler<Long>("rand_seed1",
				integralConverter,
				sessionScope,
				0L,
				noopt),
		new VariableHandler<Long>("rand_seed2",
				integralConverter,
				sessionScope,
				0L,
				noopt),
		new VariableHandler<Long>("insert_id",
				integralConverter,
				sessionScope,
				0L,
				noopt),
		new VariableHandler<Long>(INNODB_LOCK_WAIT_TIMEOUT_NAME,
				integralConverter,
				bothScope,
				3L,
				noopt),
		new VariableHandler<Boolean>("have_innodb",
				booleanConverter,
				bothScope,
				Boolean.TRUE,
				EnumSet.of(VariableOption.READONLY, VariableOption.NO_SESSION_ASSIGNMENT)),
		new VariableHandler<Boolean>("unique_checks",
				booleanConverter,
				bothScope,
				Boolean.TRUE,
				noopt),
		new VariableHandler<Boolean>("big_tables",
				booleanConverter,
				sessionScope,
				Boolean.FALSE,
				noopt),
		new VariableHandler<Long>(AUTO_INCREMENT_MIN_BLOCK_SIZE,
				new BoundedIntegralConverter(1L,null),
				globalScope,
				10L,
				dveOnly) {
			
			public void onGlobalValueChange(Long newValue) throws PEException {
				AutoIncrementTracker.setMinBlockSize(newValue.intValue());
			}
		},
		new VariableHandler<Long>(AUTO_INCREMENT_PREFETCH_THRESHOLD,
				new BoundedIntegralConverter(0L,100L),
				globalScope,
				50L,
				dveOnly) {
			public void onGlobalValueChange(Long newValue) throws PEException {
				AutoIncrementTracker.setPrefetchThreshold(newValue.intValue());
			}
			
		},
		new VariableHandler<Long>(AUTO_INCREMENT_MAX_BLOCK_SIZE,
				integralConverter,
				globalScope,
				1000L,
				dveOnly) {
			public void onGlobalValueChange(Long newValue) throws PEException {
				AutoIncrementTracker.setMaxBlockSize(newValue.intValue());
			}
			
		},
		new VariableHandler<Long>("sql_quote_show_create",
				new BoundedIntegralConverter(-1L,2L),
				bothScope,
				1L,
				noopt),
		new VariableHandler<String>("collation_database",
				new LiteralValueConverter() {

					@Override
					public String convertToInternal(String varName, String in) throws PEException {
						Singletons.require(HostService.class).getDBNative().assertValidCollation(in);
						return in;
					}
				},
				bothScope,
				"utf8_general_ci",
				noopt),
		new VariableHandler<Long>("sql_notes",
				new BoundedIntegralConverter(-1L,2L),
				bothScope,
				1L,
				noopt),
		new VariableHandler<String>("init_connect",
				stringConverter,
				globalScope,
				"",
				noopt),
		new VariableHandler<Long>("interactive_timeout",
				new BoundedIntegralConverter(0L,null),
				bothScope,
				28800L,
				noopt),
		new VariableHandler<Long>("lower_case_table_names",
				new BoundedIntegralConverter(-1L,3L),
				globalScope,
				1L,
				readonly),
		new VariableHandler<Long>("net_buffer_length",
				new BoundedIntegralConverter(1023L,1048577L),
				bothScope,
				16384L,
				EnumSet.of(VariableOption.NO_SESSION_ASSIGNMENT)),
		new VariableHandler<Long>("net_write_timeout",
				new BoundedIntegralConverter(0L,null),
				bothScope,
				60L,
				noopt),
		new VariableHandler<String>("system_time_zone",
				stringConverter,
				globalScope,
				"EDT", // todo: change this
				readonly),
		new VariableHandler<Long>("query_cache_size",
				new BoundedIntegralConverter(-1L,4294967296L), // maybe not right?
				globalScope,
				41943040L,
				noopt),
		new VariableHandler<Long>("query_cache_type",
				new BoundedIntegralConverter(-1L,3L),
				bothScope,
				1L,
				noopt),

	};

}

---
-- #%L
-- Tesora Inc.
-- Database Virtualization Engine
-- %%
-- Copyright (C) 2011 - 2014 Tesora Inc.
-- %%
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License, version 3,
-- as published by the Free Software Foundation.
-- 
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
-- GNU Affero General Public License for more details.
-- 
-- You should have received a copy of the GNU Affero General Public License
-- along with this program. If not, see <http://www.gnu.org/licenses/>.
-- #L%
---
create table auto_incr (incr_id integer not null auto_increment, nextId bigint not null, table_id integer, scope_id integer, primary key (incr_id)) ENGINE=InnoDB;
create table character_sets (id integer(11) not null, character_set_name varchar(32) default '' not null, description varchar(60) default '' not null, maxlen bigint(3) default '0' not null, pe_character_set_name varchar(32) default '' not null, primary key (id)) ENGINE=InnoDB;
create table collations (id int(11) not null, character_set_name varchar(32) not null, is_compiled int(11) default '1' not null, is_default int(11) default '0' not null, name varchar(32) not null, sortlen bigint(3) not null, primary key (id)) ENGINE=InnoDB;
create table container (container_id integer not null auto_increment, name varchar(255), base_table_id integer, distribution_model_id integer not null, range_id integer, storage_group_id integer not null, primary key (container_id)) ENGINE=InnoDB;
create table container_tenant (ctid integer not null auto_increment, discriminant longtext not null, container_id integer not null, primary key (ctid)) ENGINE=InnoDB;
create table distribution_model (name varchar(31) not null, id integer not null auto_increment, primary key (id)) ENGINE=InnoDB;
create table distribution_range (range_id integer not null auto_increment, name varchar(255) not null, signature varchar(255), persistent_group_id integer not null, primary key (range_id)) ENGINE=InnoDB;
create table dynamic_policy (policy_id integer not null auto_increment, aggregate_class varchar(255), aggregate_count integer, aggregate_provider varchar(255), large_class varchar(255), large_count integer, large_provider varchar(255), medium_class varchar(255), medium_count integer, medium_provider varchar(255), name varchar(255) not null, small_class varchar(255), small_count integer, small_provider varchar(255), strict bit not null, primary key (policy_id)) ENGINE=InnoDB;
create table engines (id integer(11) not null auto_increment, comment varchar(80) default '' not null, engine varchar(64) default '' not null, savepoints varchar(3), support varchar(8) default '' not null, transactions varchar(3), xa varchar(3), primary key (id)) ENGINE=InnoDB;
create table external_service (id integer not null auto_increment, auto_start bit not null, config longtext, connect_user varchar(255), name varchar(255) not null unique, plugin varchar(255) not null, uses_datastore bit not null, primary key (id)) ENGINE=InnoDB;
create table foreign_key (fk_id integer not null auto_increment, name varchar(255) not null, user_table_id integer not null, primary key (fk_id)) ENGINE=InnoDB;
create table generation_key_range (key_gen_id integer not null auto_increment, range_end longtext not null, range_start longtext not null, version integer not null, range_id integer not null, generation_id integer not null, primary key (key_gen_id)) ENGINE=InnoDB;
create table generation_sites (generation_id integer not null, site_id integer not null) ENGINE=InnoDB;
create table persistent_group (persistent_group_id integer not null auto_increment, name varchar(255) not null unique, primary key (persistent_group_id)) ENGINE=InnoDB;
create table priviledge (id integer not null auto_increment, user_database_id integer, tenant_id integer, user_id integer, primary key (id)) ENGINE=InnoDB;
create table project (project_id integer not null auto_increment, name varchar(255), root_user_id integer, primary key (project_id)) ENGINE=InnoDB;
create table provider (id integer not null auto_increment, config longtext, enabled bit not null, name varchar(255) not null unique, plugin varchar(255) not null, primary key (id)) ENGINE=InnoDB;
create table range_table_relation (relationship_id integer not null auto_increment, range_id integer not null, table_id integer not null, primary key (relationship_id), unique (table_id)) ENGINE=InnoDB;
create table rawplan (plan_id integer not null auto_increment, cachekey longtext not null, plan_comment varchar(255), definition longtext not null, enabled integer not null, name varchar(255) not null, user_database_id integer not null, primary key (plan_id)) ENGINE=InnoDB;
create table scope (scope_id integer not null auto_increment, local_name varchar(255) not null, scope_tenant_id integer, scope_table_id integer, primary key (scope_id), unique (scope_tenant_id, local_name)) ENGINE=InnoDB;
create table server (server_id integer not null auto_increment, ipAddress varchar(255), name varchar(255), primary key (server_id)) ENGINE=InnoDB;
create table shape (shape_id integer not null auto_increment, name varchar(255) not null, definition longtext not null, typehash varchar(40) not null, database_id integer not null, primary key (shape_id)) ENGINE=InnoDB;
create table site_instance (id integer not null auto_increment, instance_url varchar(255) not null, is_master integer not null, name varchar(255) not null unique, password varchar(255) not null, status varchar(255) not null, user varchar(255) not null, storage_site_id integer, primary key (id)) ENGINE=InnoDB;
create table statistics_log (id integer not null auto_increment, name varchar(255), opClass varchar(255), opCount integer not null, responseTime float not null, timestamp datetime, type varchar(255), primary key (id)) ENGINE=InnoDB;
create table storage_generation (generation_id integer not null auto_increment, locked bit not null, version integer not null, persistent_group_id integer not null, primary key (generation_id)) ENGINE=InnoDB;
create table storage_site (id integer not null auto_increment, haType varchar(255), name varchar(255), primary key (id), unique (name)) ENGINE=InnoDB;
create table table_fk (id integer not null auto_increment, fk_id integer not null, source_table_id integer not null, target_table_id integer not null, primary key (id)) ENGINE=InnoDB;
create table template (template_id integer not null auto_increment, template_comment varchar(255), dbmatch varchar(255), definition longtext not null, name varchar(255) not null, primary key (template_id)) ENGINE=InnoDB;
create table tenant (tenant_id integer not null auto_increment, description varchar(255), ext_tenant_id varchar(255) not null unique, suspended bit, user_database_id integer not null, primary key (tenant_id)) ENGINE=InnoDB;
create table txn_record (xid varchar(255) not null, created_date datetime not null, host varchar(255) not null, is_committed integer not null, primary key (xid)) ENGINE=InnoDB;
create table user (id integer not null auto_increment, accessSpec varchar(255), admin_user bit, grantPriv bit, name varchar(255), password varchar(255), primary key (id)) ENGINE=InnoDB;
create table user_column (user_column_id integer not null auto_increment, auto_generated bit not null, cdv integer, charset varchar(255), collation varchar(255), comment varchar(255), data_type integer not null, default_value longtext, default_value_is_constant integer not null, has_default_value bit not null, hash_position integer, name varchar(255) not null, native_type_modifiers varchar(255), native_type_name longtext not null, nullable bit not null, on_update integer not null, order_in_table integer not null, prec integer not null, scale integer not null, size integer not null, user_table_id integer, primary key (user_column_id)) ENGINE=InnoDB;
create table user_database (user_database_id integer not null auto_increment, default_character_set_name varchar(255), default_collation_name varchar(255), fk_mode varchar(255) not null, multitenant_mode varchar(255) not null, name varchar(255), template_mode varchar(255), template varchar(255), default_group_id integer, primary key (user_database_id)) ENGINE=InnoDB;
create table user_key (key_id integer not null auto_increment, key_comment varchar(255), constraint_type varchar(255), constraint_name varchar(255), fk_delete_action varchar(255), fk_update_action varchar(255), hidden integer not null, name varchar(255) not null, persisted integer not null, physical_constraint_name varchar(255), position integer not null, forward_schema_name varchar(255), forward_table_name varchar(255), synth integer not null, index_type varchar(255) not null, referenced_table integer, user_table_id integer not null, primary key (key_id)) ENGINE=InnoDB;
create table user_key_column (key_column_id integer not null auto_increment, cardinality bigint, length integer, position integer not null, forward_column_name varchar(255), src_column_id integer not null, key_id integer not null, targ_column_id integer, primary key (key_column_id)) ENGINE=InnoDB;
create table user_table (table_id integer not null auto_increment, collation varchar(255), comment varchar(2048), create_options varchar(255), create_table_stmt longtext, engine varchar(255), name varchar(255) not null, row_format varchar(255), state varchar(255), table_type varchar(255) not null, container_id integer, distribution_model_id integer not null, persistent_group_id integer not null, shape_id integer, user_database_id integer not null, primary key (table_id)) ENGINE=InnoDB;
create table user_temp_table (id integer not null auto_increment, db varchar(255) not null, table_engine varchar(255) not null, name varchar(255) not null, server_id varchar(255) not null, session_id integer not null, primary key (id)) ENGINE=InnoDB;
create table user_view (view_id integer not null auto_increment, algorithm varchar(255) not null, character_set_client varchar(255) not null, check_option varchar(255) not null, collation_connection varchar(255) not null, definition longtext not null, mode varchar(255) not null, security varchar(255) not null, user_id integer not null, table_id integer not null, primary key (view_id), unique (table_id)) ENGINE=InnoDB;
create table varconfig (id integer not null auto_increment, description varchar(255), name varchar(255) not null, options varchar(255) not null, scopes varchar(255) not null, value varchar(255), value_type varchar(255) not null, primary key (id), unique (name)) ENGINE=InnoDB;
alter table auto_incr add index fk_autoincr_scope (scope_id), add constraint fk_autoincr_scope foreign key (scope_id) references scope (scope_id);
alter table auto_incr add index fk_autoincr_table (table_id), add constraint fk_autoincr_table foreign key (table_id) references user_table (table_id);
alter table container add index fk_cont_dist_model (distribution_model_id), add constraint fk_cont_dist_model foreign key (distribution_model_id) references distribution_model (id);
alter table container add index fk_cont_basetable (base_table_id), add constraint fk_cont_basetable foreign key (base_table_id) references user_table (table_id);
alter table container add index fk_cont_range (range_id), add constraint fk_cont_range foreign key (range_id) references distribution_range (range_id);
alter table container add index fk_cont_sg (storage_group_id), add constraint fk_cont_sg foreign key (storage_group_id) references persistent_group (persistent_group_id);
alter table container_tenant add index fk_cont_tenant_cont (container_id), add constraint fk_cont_tenant_cont foreign key (container_id) references container (container_id);
alter table distribution_range add index fk_range_group (persistent_group_id), add constraint fk_range_group foreign key (persistent_group_id) references persistent_group (persistent_group_id);
alter table foreign_key add index FKA9B3C474E8DC89AB (user_table_id), add constraint FKA9B3C474E8DC89AB foreign key (user_table_id) references user_table (table_id);
alter table generation_key_range add index fk_gen_key_range_range (range_id), add constraint fk_gen_key_range_range foreign key (range_id) references distribution_range (range_id);
alter table generation_key_range add index fk_gen_key_range_gen (generation_id), add constraint fk_gen_key_range_gen foreign key (generation_id) references storage_generation (generation_id);
alter table generation_sites add index FK238DE425A76F01D6 (generation_id), add constraint FK238DE425A76F01D6 foreign key (generation_id) references storage_generation (generation_id);
alter table generation_sites add index FK238DE4253AD566C9 (site_id), add constraint FK238DE4253AD566C9 foreign key (site_id) references storage_site (id);
alter table priviledge add index fk_priv_db (user_database_id), add constraint fk_priv_db foreign key (user_database_id) references user_database (user_database_id);
alter table priviledge add index fk_priv_user (user_id), add constraint fk_priv_user foreign key (user_id) references user (id);
alter table priviledge add index fk_priv_tenant (tenant_id), add constraint fk_priv_tenant foreign key (tenant_id) references tenant (tenant_id);
alter table project add index fk_project_root_user (root_user_id), add constraint fk_project_root_user foreign key (root_user_id) references user (id);
alter table range_table_relation add index fk_range_table_range (range_id), add constraint fk_range_table_range foreign key (range_id) references distribution_range (range_id);
alter table range_table_relation add index fk_range_table_table (table_id), add constraint fk_range_table_table foreign key (table_id) references user_table (table_id);
alter table rawplan add index fk_raw_plan_db (user_database_id), add constraint fk_raw_plan_db foreign key (user_database_id) references user_database (user_database_id);
create index local_name_idx on scope (local_name);
alter table scope add index fk_scope_table (scope_table_id), add constraint fk_scope_table foreign key (scope_table_id) references user_table (table_id);
alter table scope add index fk_scope_tenant (scope_tenant_id), add constraint fk_scope_tenant foreign key (scope_tenant_id) references tenant (tenant_id);
alter table shape add index fk_shape_db (database_id), add constraint fk_shape_db foreign key (database_id) references user_database (user_database_id);
alter table site_instance add index fk_site_instance_site (storage_site_id), add constraint fk_site_instance_site foreign key (storage_site_id) references storage_site (id);
create index EntryDate on statistics_log (timestamp);
alter table storage_generation add index fk_sg_gen_group (persistent_group_id), add constraint fk_sg_gen_group foreign key (persistent_group_id) references persistent_group (persistent_group_id);
alter table table_fk add index FKCAA0FAD6A65E053 (target_table_id), add constraint FKCAA0FAD6A65E053 foreign key (target_table_id) references user_column (user_column_id);
alter table table_fk add index FKCAA0FAD66B2C02F8 (fk_id), add constraint FKCAA0FAD66B2C02F8 foreign key (fk_id) references foreign_key (fk_id);
alter table table_fk add index FKCAA0FAD6B0BFA509 (source_table_id), add constraint FKCAA0FAD6B0BFA509 foreign key (source_table_id) references user_column (user_column_id);
alter table tenant add index fk_tenant_db (user_database_id), add constraint fk_tenant_db foreign key (user_database_id) references user_database (user_database_id);
alter table user_column add index fk_column_table (user_table_id), add constraint fk_column_table foreign key (user_table_id) references user_table (table_id);
alter table user_database add index fk_db_def_sg (default_group_id), add constraint fk_db_def_sg foreign key (default_group_id) references persistent_group (persistent_group_id);
alter table user_key add index fk_key_src_table (user_table_id), add constraint fk_key_src_table foreign key (user_table_id) references user_table (table_id);
alter table user_key add index fk_key_targ_table (referenced_table), add constraint fk_key_targ_table foreign key (referenced_table) references user_table (table_id);
alter table user_key_column add index fk_key_column_src_column (src_column_id), add constraint fk_key_column_src_column foreign key (src_column_id) references user_column (user_column_id);
alter table user_key_column add index fk_key_column_key (key_id), add constraint fk_key_column_key foreign key (key_id) references user_key (key_id);
alter table user_key_column add index fk_key_column_targ_column (targ_column_id), add constraint fk_key_column_targ_column foreign key (targ_column_id) references user_column (user_column_id);
alter table user_table add index fk_table_sg (persistent_group_id), add constraint fk_table_sg foreign key (persistent_group_id) references persistent_group (persistent_group_id);
alter table user_table add index fk_table_model (distribution_model_id), add constraint fk_table_model foreign key (distribution_model_id) references distribution_model (id);
alter table user_table add index fk_table_shape (shape_id), add constraint fk_table_shape foreign key (shape_id) references shape (shape_id);
alter table user_table add index fk_table_db (user_database_id), add constraint fk_table_db foreign key (user_database_id) references user_database (user_database_id);
alter table user_table add index fk_table_container (container_id), add constraint fk_table_container foreign key (container_id) references container (container_id);
alter table user_view add index fk_view_user (user_id), add constraint fk_view_user foreign key (user_id) references user (id);
alter table user_view add index fk_view_table_def (table_id), add constraint fk_view_table_def foreign key (table_id) references user_table (table_id);
alter table container_tenant add key `cont_ten_idx` (container_id, discriminant(80));
alter table shape add unique key `unq_shape_idx` (database_id, name, typehash);
create table pe_version (schema_version int not null, code_version varchar(128) not null, state varchar(64) not null);

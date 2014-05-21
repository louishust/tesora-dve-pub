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
drop database if exists site2_TestDB;
create database site2_TestDB;
use site2_TestDB;

create table Random (
	id int,
	value varchar(20)
);

insert into Random 
values
(3, "value3"),
(4, "value4");

create table Broadcast (
	id int,
	value varchar(20)
);
insert into Broadcast
values
(1, "value1"),
(2, "value2"),
(3, "value3"),
(4, "value4"),
(5, "value5");

create table Static (
	id int,
	value varchar(20)
);
insert into Static 
values
(1, "value1"),
(3, "value3"),
(5, "value5");

create table `Range` (
	id int,
	value varchar(20)
);
insert into `Range` 
values
(1, "value1"),
(3, "value3"),
(5, "value5");

create table RandomGen2 (
	id int,
	value varchar(20)
);

create table BroadcastGen2 (
	id int,
	value varchar(20)
);
insert into BroadcastGen2
values
(6, "value6"),
(7, "value7");

create table StaticGen2 (
	id int,
	value varchar(20)
);
insert into StaticGen2
values
(6, "value6"),
(7, "value7");
create table `RangeGen2` (
	id int,
	value varchar(20)
);
insert into RangeGen2
select * from `Range`;
insert into RandomGen2
select * from Random;
insert into BroadcastGen2
select * from Broadcast;

create table RandomGen3 (
	id int,
	value varchar(20)
);

create table BroadcastGen3 (
	id int,
	value varchar(20)
);
insert into BroadcastGen3
values
(6, "value6"),
(7, "value7");

create table StaticGen3 (
	id int,
	value varchar(20)
);
insert into StaticGen3
values
(7, "value7");

insert into RandomGen3
select * from Random;
insert into BroadcastGen3
select * from Broadcast;

create table `RangeGen3` (
	id int,
	value varchar(20)
);
insert into RangeGen3
select * from `Range`;
insert into RangeGen3
values
(7, "value7");



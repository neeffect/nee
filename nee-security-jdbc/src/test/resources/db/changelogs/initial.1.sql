CREATE SEQUENCE GlobalStonesSeq;

CREATE TABLE  Stones
(
	id BIGINT not null, -- flaw in JOOQ or H2 (no returning works_
	name VARCHAR not null,
	price DECIMAL,
	constraint Stones_pk
		primary key (ID)
);

CREATE UNIQUE INDEX STONES_ID_UINDEX
	on Stones (ID);

CREATE TABLE AuditLogs (
    id BIGINT not null,
    user VARCHAR not null,
    operation VARCHAR not null,
    operationDate TIMESTAMP not null,
    constraint AutidLog_PK primary key (id)
);
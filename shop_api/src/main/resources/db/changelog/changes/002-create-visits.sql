--liquibase formatted sql

--changeset edu_shop:002-create-visits
CREATE TABLE visits
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE visits;

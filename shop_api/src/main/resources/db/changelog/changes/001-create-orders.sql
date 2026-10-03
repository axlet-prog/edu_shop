--liquibase formatted sql

--changeset edu_shop:001-create-orders
CREATE TABLE orders
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    amount       NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    items_count  INTEGER        NOT NULL CHECK (items_count > 0),
    status       VARCHAR(16)    NOT NULL CHECK (status IN ('NEW', 'PROCESSING', 'DONE')),
    created_at   TIMESTAMPTZ    NOT NULL,
    processed_at TIMESTAMPTZ
);
--rollback DROP TABLE orders;

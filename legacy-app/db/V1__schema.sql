-- Harbor Claims Portal schema (fictional data only). Flyway baseline for the modern app in M6.
CREATE TABLE roles (
    id          INTEGER PRIMARY KEY,
    name        VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(120) NOT NULL
);

CREATE TABLE app_users (
    id           INTEGER PRIMARY KEY,
    username     VARCHAR(40) NOT NULL UNIQUE,
    password     VARCHAR(64) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    role_id      INTEGER NOT NULL REFERENCES roles (id),
    active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE customers (
    id            SERIAL PRIMARY KEY,
    first_name    VARCHAR(50) NOT NULL,
    last_name     VARCHAR(50) NOT NULL,
    email         VARCHAR(120) NOT NULL UNIQUE,
    phone         VARCHAR(20),
    date_of_birth DATE,
    address_line  VARCHAR(120),
    city          VARCHAR(60),
    state         VARCHAR(2),
    postal_code   VARCHAR(10),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE policies (
    id             SERIAL PRIMARY KEY,
    policy_number  VARCHAR(20) NOT NULL UNIQUE,
    customer_id    INTEGER NOT NULL REFERENCES customers (id),
    product_type   VARCHAR(10) NOT NULL,
    coverage_limit NUMERIC(12,2) NOT NULL,
    premium        NUMERIC(10,2) NOT NULL,
    start_date     DATE NOT NULL,
    end_date       DATE NOT NULL,
    status         VARCHAR(12) NOT NULL
);

CREATE TABLE claims (
    id              SERIAL PRIMARY KEY,
    claim_number    VARCHAR(12) NOT NULL UNIQUE,
    policy_id       INTEGER NOT NULL REFERENCES policies (id),
    customer_id     INTEGER NOT NULL REFERENCES customers (id),
    status          VARCHAR(14) NOT NULL,
    loss_date       DATE NOT NULL,
    reported_date   DATE NOT NULL,
    amount_claimed  NUMERIC(12,2) NOT NULL,
    amount_approved NUMERIC(12,2),
    description     VARCHAR(2000) NOT NULL,
    assigned_to     INTEGER REFERENCES app_users (id),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE claim_notes (
    id         SERIAL PRIMARY KEY,
    claim_id   INTEGER NOT NULL REFERENCES claims (id),
    author     VARCHAR(40) NOT NULL,
    note       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_claims_policy ON claims (policy_id);
CREATE INDEX idx_claims_status ON claims (status);
CREATE INDEX idx_policies_customer ON policies (customer_id);
CREATE INDEX idx_notes_claim ON claim_notes (claim_id);

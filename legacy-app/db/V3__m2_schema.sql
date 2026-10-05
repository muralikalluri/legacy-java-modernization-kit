-- M2: policy cancellation date and nightly job history.
ALTER TABLE policies ADD COLUMN cancelled_on DATE;

CREATE TABLE job_runs (
    id            SERIAL PRIMARY KEY,
    job_name      VARCHAR(40) NOT NULL,
    trigger_type  VARCHAR(10) NOT NULL,
    triggered_by  VARCHAR(40) NOT NULL,
    as_of         DATE NOT NULL,
    started_at    TIMESTAMP NOT NULL DEFAULT now(),
    finished_at   TIMESTAMP,
    rows_affected INTEGER,
    status        VARCHAR(10) NOT NULL,
    error         VARCHAR(500)
);

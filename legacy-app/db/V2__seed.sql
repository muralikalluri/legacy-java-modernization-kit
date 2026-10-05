-- Deterministic seed. All people and companies are fictional. Fixed dates only, never now().
INSERT INTO roles (id, name, description) VALUES
 (1, 'ADMIN', 'Full access'),
 (2, 'ADJUSTER', 'Reviews and decides claims'),
 (3, 'SUPERVISOR', 'Approves large claims, reopens denials'),
 (4, 'UNDERWRITER', 'Manages policies'),
 (5, 'CSR', 'Customer service representative'),
 (6, 'AUDITOR', 'Read-only access');

INSERT INTO app_users (id, username, password, display_name, role_id, active) VALUES
 (1, 'admin',       'admin-demo',       'Avery Admin',          1, TRUE),
 (2, 'adjuster1',   'adjuster1-demo',   'Aiden Adjuster',       2, TRUE),
 (3, 'adjuster2',   'adjuster2-demo',   'Alba Adjuster',        2, TRUE),
 (4, 'supervisor1', 'supervisor1-demo', 'Sasha Supervisor',     3, TRUE),
 (5, 'underwriter1','underwriter1-demo','Uma Underwriter',      4, TRUE),
 (6, 'csr1',        'csr1-demo',        'Casey Service',        5, TRUE),
 (7, 'auditor1',    'auditor1-demo',    'Arlo Auditor',         6, TRUE),
 (8, 'retired1',    'retired1-demo',    'Rory Retired',         5, FALSE);

INSERT INTO customers (id, first_name, last_name, email, phone, date_of_birth, address_line, city, state, postal_code, created_at)
SELECT i,
       (ARRAY['Amara','Bruno','Celeste','Dmitri','Elena','Farid','Greta','Hugo','Ines','Jonas','Katya','Leon'])[(i - 1) % 12 + 1],
       (ARRAY['Almeida','Brandt','Castellan','Dumont','Eriksen','Fontaine','Garrido','Hollis','Ivanova','Jorgensen'])[(i - 1) % 10 + 1],
       lower((ARRAY['Amara','Bruno','Celeste','Dmitri','Elena','Farid','Greta','Hugo','Ines','Jonas','Katya','Leon'])[(i - 1) % 12 + 1])
         || '.' || lower((ARRAY['Almeida','Brandt','Castellan','Dumont','Eriksen','Fontaine','Garrido','Hollis','Ivanova','Jorgensen'])[(i - 1) % 10 + 1])
         || '@example.test',
       '555-01' || lpad(i::text, 2, '0'),
       DATE '1960-01-01' + ((i * 397) % 15000),
       (100 + i * 7)::text || ' Harbor Lane',
       (ARRAY['Port Alder','Marlow Bay','Kestrel Ridge','Dunmere','Ashford Quay'])[i % 5 + 1],
       'HB',
       lpad((10000 + i * 37)::text, 5, '0'),
       TIMESTAMP '2025-01-01 09:00:00' + (i || ' hours')::interval
FROM generate_series(1, 30) AS i;

INSERT INTO policies (id, policy_number, customer_id, product_type, coverage_limit, premium, start_date, end_date, status)
SELECT i,
       'POL-' || lpad(i::text, 6, '0'),
       ((i - 1) % 30) + 1,
       (ARRAY['AUTO','HOME','LIFE'])[i % 3 + 1],
       (ARRAY[25000, 50000, 100000, 250000])[i % 4 + 1],
       300 + ((i * 41) % 900),
       DATE '2025-01-01' + ((i * 11) % 200),
       (DATE '2025-01-01' + ((i * 11) % 200) + INTERVAL '1 year' - INTERVAL '1 day')::date,
       CASE WHEN i % 13 = 0 THEN 'CANCELLED' WHEN i % 9 = 0 THEN 'LAPSED' ELSE 'ACTIVE' END
FROM generate_series(1, 36) AS i;

INSERT INTO claims (id, claim_number, policy_id, customer_id, status, loss_date, reported_date,
                    amount_claimed, amount_approved, description, assigned_to, created_at, updated_at)
SELECT i,
       'CLM-' || lpad(i::text, 6, '0'),
       p.id,
       p.customer_id,
       (ARRAY['NEW','UNDER_REVIEW','APPROVED','DENIED','PAID','REOPENED'])[i % 6 + 1],
       p.start_date + ((i * 17) % 300),
       p.start_date + ((i * 17) % 300) + (i % 9 + 1),
       (500 + ((i * 1237) % 19000)) + ((i * 37) % 100) / 100.0,
       CASE WHEN i % 6 IN (2, 4) THEN (500 + ((i * 1237) % 19000)) + ((i * 37) % 100) / 100.0 END,
       'Seeded claim ' || i || ': ' || (ARRAY['rear bumper damage','water leak in kitchen','cracked windscreen','storm damage to roof','stolen bicycle','fallen tree on fence'])[i % 6 + 1],
       CASE WHEN i % 6 = 0 THEN NULL ELSE 2 + (i % 2) END,
       TIMESTAMP '2025-06-01 10:00:00' + (i || ' hours')::interval,
       TIMESTAMP '2025-06-01 10:00:00' + (i || ' hours')::interval
FROM generate_series(1, 60) AS i
JOIN policies p ON p.id = ((i - 1) % 36) + 1;

INSERT INTO claim_notes (claim_id, author, note, created_at)
SELECT id, 'adjuster1', 'Imported seed note for ' || claim_number, TIMESTAMP '2025-06-02 09:00:00' + (id || ' minutes')::interval
FROM claims WHERE status <> 'NEW';

SELECT setval(pg_get_serial_sequence('customers', 'id'), (SELECT max(id) FROM customers));
SELECT setval(pg_get_serial_sequence('policies', 'id'), (SELECT max(id) FROM policies));
SELECT setval(pg_get_serial_sequence('claims', 'id'), (SELECT max(id) FROM claims));
SELECT setval(pg_get_serial_sequence('claim_notes', 'id'), (SELECT max(id) FROM claim_notes));

CREATE ROLE harbor_app LOGIN PASSWORD 'x';
INSERT INTO roles (id, name, description) VALUES
 (1, 'ADMIN', 'Full access'),
 (2, 'CSR', 'Customer service');
INSERT INTO other (id, name) VALUES (9, 'NOT_A_ROLE');

-- Create schema if missing and a minimal "user" table so later migrations can add FKs/columns
CREATE SCHEMA IF NOT EXISTS historical_schema;

CREATE TABLE IF NOT EXISTS historical_schema."user" (
    uid uuid PRIMARY KEY,
    user_name varchar(100) NOT NULL UNIQUE,
    email varchar(100) NOT NULL UNIQUE,
    password varchar(255) NOT NULL,
    role varchar(50) NOT NULL,
    deleted_at TIMESTAMP NULL
);

-- Add an index on role for queries
CREATE INDEX IF NOT EXISTS idx_user_role ON historical_schema."user" (role);

INSERT INTO historical_schema."user" (uid, email, password, role, user_name)
VALUES
    (
        gen_random_uuid(),
        'customer@historytalk.com',
        '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', -- Hash của chữ '123456789'
        'CUSTOMER',
        'CUSTOMER1'
     ),
    (
        gen_random_uuid(),
        'user@historytalk.com',
        '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', -- Hash của chữ '123456789'
        'STAFF',
        'STAFF1'
    )
    ON CONFLICT (email) DO NOTHING;
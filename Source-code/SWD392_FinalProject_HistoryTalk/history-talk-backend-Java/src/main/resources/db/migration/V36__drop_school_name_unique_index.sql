-- V34: Drop unique index on school name to allow multi-tenant identical names differentiated by school_code
DROP INDEX IF EXISTS idx_school_name_unique;

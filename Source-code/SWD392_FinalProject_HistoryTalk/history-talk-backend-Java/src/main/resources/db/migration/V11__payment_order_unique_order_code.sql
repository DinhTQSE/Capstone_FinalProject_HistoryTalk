-- V11: Add UNIQUE constraint on payment_order.order_code
-- This prevents duplicate order codes from ever being stored,
-- protecting against race conditions in PaymentService.generateOrderCode().
--
-- WARNING: If duplicate order_code values exist from earlier testing, this migration
-- will fail with a unique constraint violation. Remove duplicate rows first.

ALTER TABLE historical_schema.payment_order
    ADD CONSTRAINT uq_payment_order_code UNIQUE (order_code);

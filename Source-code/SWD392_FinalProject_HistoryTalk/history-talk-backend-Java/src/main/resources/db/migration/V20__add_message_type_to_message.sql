-- V20__add_message_type_to_message.sql
-- Add message_type column to distinguish between TEXT and VOICE/VIDEO messages

ALTER TABLE message 
ADD COLUMN message_type VARCHAR(50) DEFAULT 'TEXT';

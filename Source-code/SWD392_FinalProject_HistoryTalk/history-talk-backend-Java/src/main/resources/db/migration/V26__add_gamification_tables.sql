-- V26: Add Gamification tables (daily_quest, user_quest_progress, daily_check_in)

CREATE TABLE IF NOT EXISTS historical_schema.daily_quest (
    quest_id        VARCHAR(50) PRIMARY KEY,
    type            VARCHAR(30) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    target          INT NOT NULL,
    reward_tokens   INT NOT NULL,
    order_index     INT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS historical_schema.user_quest_progress (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_uid        UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    date            DATE NOT NULL,
    quest_id        VARCHAR(50) NOT NULL,
    progress        INT NOT NULL DEFAULT 0,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    claimed         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_user_quest_date UNIQUE (user_uid, quest_id, date)
);

CREATE TABLE IF NOT EXISTS historical_schema.daily_check_in (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_uid        UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    date            DATE NOT NULL,
    streak_count    INT NOT NULL DEFAULT 1,
    CONSTRAINT uq_user_checkin_date UNIQUE (user_uid, date)
);

CREATE INDEX IF NOT EXISTS idx_user_quest_progress_user_date ON historical_schema.user_quest_progress(user_uid, date);
CREATE INDEX IF NOT EXISTS idx_daily_check_in_user_date ON historical_schema.daily_check_in(user_uid, date);

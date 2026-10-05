-- Accounts, login tokens, rooms and the record of every hand played (SPEC section 7).

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(24) NOT NULL,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ
);

-- "Asha" and "asha" are the same player.
CREATE UNIQUE INDEX users_username_unique ON users (LOWER(username));

-- The token column holds the SHA-256 of the token, never the token itself.
CREATE TABLE auth_tokens (
    token      CHAR(64)    PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX auth_tokens_user ON auth_tokens (user_id);

CREATE TABLE rooms (
    id             BIGSERIAL   PRIMARY KEY,
    code           CHAR(6)     NOT NULL UNIQUE,
    name           VARCHAR(40) NOT NULL,
    host_user_id   BIGINT      NOT NULL REFERENCES users (id),
    max_players    SMALLINT    NOT NULL CHECK (max_players BETWEEN 2 AND 9),
    small_blind    BIGINT      NOT NULL,
    big_blind      BIGINT      NOT NULL,
    starting_stack BIGINT      NOT NULL,
    turn_seconds   SMALLINT    NOT NULL,
    rebuy_allowed  BOOLEAN     NOT NULL,
    status         VARCHAR(10) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at      TIMESTAMPTZ
);

CREATE TABLE hands (
    id          BIGSERIAL   PRIMARY KEY,
    room_id     BIGINT      NOT NULL REFERENCES rooms (id),
    hand_no     INT         NOT NULL,
    button_seat SMALLINT    NOT NULL,
    board       VARCHAR(20) NOT NULL,
    total_pot   BIGINT      NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL,
    ended_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX hands_room_ended ON hands (room_id, ended_at);

CREATE TABLE hand_players (
    hand_id     BIGINT   NOT NULL REFERENCES hands (id) ON DELETE CASCADE,
    user_id     BIGINT   NOT NULL REFERENCES users (id),
    seat        SMALLINT NOT NULL,
    hole_cards  CHAR(4)  NOT NULL,
    start_stack BIGINT   NOT NULL,
    end_stack   BIGINT   NOT NULL,
    net         BIGINT   NOT NULL,
    showed_down BOOLEAN  NOT NULL,
    won         BOOLEAN  NOT NULL,
    PRIMARY KEY (hand_id, user_id)
);

CREATE INDEX hand_players_user ON hand_players (user_id);

-- action is one of POST_SB, POST_BB, FOLD, CHECK, CALL, BET, RAISE.
CREATE TABLE hand_actions (
    id      BIGSERIAL  PRIMARY KEY,
    hand_id BIGINT     NOT NULL REFERENCES hands (id) ON DELETE CASCADE,
    seq     INT        NOT NULL,
    user_id BIGINT     NOT NULL REFERENCES users (id),
    street  VARCHAR(8) NOT NULL,
    action  VARCHAR(8) NOT NULL,
    amount  BIGINT     NOT NULL
);

CREATE INDEX hand_actions_hand ON hand_actions (hand_id, seq);

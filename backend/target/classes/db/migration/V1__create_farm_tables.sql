-- CREATE SCHEMA IF NOT EXISTS farm;
        -- for later, persistent implementation

CREATE TABLE farm.farmer_account (
    id UUID PRIMARY KEY,
    username VARCHAR(40) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE farm.account_token (
    id UUID PRIMARY KEY,
    farmer_id UUID NOT NULL
        REFERENCES farm.farmer_account(id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL
        CHECK (purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET')),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP
);

-- CREATE TABLE farm.account_social (
--     id UUID PRIMARY KEY,
--     farmer_id UUID NOT NULL
--         REFERENCES farm.farmer_account(id) ON DELETE CASCADE,
--     provider VARCHAR(40) NOT NULL,
--     provider_subject VARCHAR(255) NOT NULL,
--     created_at TIMESTAMP WITH TIME ZONE NOT NULL
--         DEFAULT CURRENT_TIMESTAMP,
--     UNIQUE (provider, provider_subject)
-- );

CREATE TABLE farm.crop_entry (
    farmer_id UUID NOT NULL 
        REFERENCES farm.farmer_account(id)
        ON DELETE CASCADE,
    crop_type VARCHAR(30) NOT NULL,
    planted_at TIMESTAMP(0) WITH TIME ZONE NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL 
        CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9, 6) NOT NULL 
        CHECK (longitude BETWEEN -180 AND 180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL 
        DEFAULT CURRENT_TIMESTAMP,

    -- A farmer has many entries. Different farmers can plant the same crop at the same time.
    PRIMARY KEY (farmer_id, crop_type, planted_at),

    CHECK (crop_type IN (
        'POTATO', 'SUGAR_CANE', 'APPLE', 'RICE', 'WHEAT',
        'MAIZE', 'TOMATO', 'CARROT', 'LETTUCE', 'SOYBEAN'
    ))
);

CREATE INDEX crop_entry_farmer_index 
    ON farm.crop_entry (farmer_id, planted_at);

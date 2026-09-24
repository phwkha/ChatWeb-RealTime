-- ============================================================
-- ChatWeb PostgreSQL Database Schema Initialization
-- Auto-mounted to /docker-entrypoint-initdb.d/init.sql
-- Matching all JPA Entities for Hibernate ddl-auto: validate
-- ============================================================

-- 1. Table: permissions
CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    create_at TIMESTAMP WITH TIME ZONE,
    update_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_permission_name ON permissions(name);

-- 2. Table: roles
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    create_at TIMESTAMP WITH TIME ZONE,
    update_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_role_name ON roles(name);

-- 3. Table: role_has_permission (ManyToMany join table)
CREATE TABLE IF NOT EXISTS role_has_permission (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 4. Table: users
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    auth_provider VARCHAR(50),
    provider_id VARCHAR(255) UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(50),
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    user_status VARCHAR(50),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    avatar VARCHAR(255),
    birthday DATE,
    gender VARCHAR(50),
    token_version INTEGER DEFAULT 0,
    create_at TIMESTAMP WITH TIME ZONE,
    update_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_user_status ON users(user_status);
CREATE INDEX IF NOT EXISTS idx_user_role_id ON users(role_id);

-- 5. Table: addresses
CREATE TABLE IF NOT EXISTS addresses (
    id BIGSERIAL PRIMARY KEY,
    house_number VARCHAR(255),
    street VARCHAR(255),
    ward VARCHAR(255),
    district VARCHAR(255),
    city VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    postal_code VARCHAR(50),
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    create_at TIMESTAMP WITH TIME ZONE,
    update_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_address_user_id ON addresses(user_id);

-- 6. Table: friendships
CREATE TABLE IF NOT EXISTS friendships (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    create_at TIMESTAMP WITH TIME ZONE,
    update_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_friendship_requester_addressee UNIQUE (requester_id, addressee_id)
);

CREATE INDEX IF NOT EXISTS idx_friendship_requester_status ON friendships(requester_id, status);
CREATE INDEX IF NOT EXISTS idx_friendship_addressee_status ON friendships(addressee_id, status);

-- 7. Table: notifications
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id VARCHAR(255),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT NOT NULL,
    create_at TIMESTAMP WITH TIME ZONE,
    update_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_create_at_id 
ON notifications(recipient_id, create_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread 
ON notifications(recipient_id) WHERE is_read = false;

-- 8. Table: reports
CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(30) NOT NULL,
    details TEXT,
    status VARCHAR(20) NOT NULL,
    resolution_note TEXT,
    resolved_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    resolve_at TIMESTAMP WITH TIME ZONE,
    create_at TIMESTAMP WITH TIME ZONE,
    update_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_reports_status_create_at ON reports(status, create_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_reported_user ON reports(reported_user_id);


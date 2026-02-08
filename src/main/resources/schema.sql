-- Create users table (only if it doesn't exist)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    device_id VARCHAR(255),
    last_login TIMESTAMP,
    user_token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL
);

-- Create file_transfers table (only if it doesn't exist)
CREATE TABLE IF NOT EXISTS file_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    transfer_status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    sender_id BIGINT,
    receiver_id BIGINT,
    FOREIGN KEY (sender_id) REFERENCES users(id),
    FOREIGN KEY (receiver_id) REFERENCES users(id)
);

-- Create transfer_peers table (only if it doesn't exist)
CREATE TABLE IF NOT EXISTS transfer_peers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    peer_id VARCHAR(255) NOT NULL,
    peer_type VARCHAR(50) NOT NULL,
    FOREIGN KEY (transfer_id) REFERENCES file_transfers(id)
);

-- Create sender_transfers table (only if it doesn't exist)
CREATE TABLE IF NOT EXISTS sender_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    receiver_code VARCHAR(6) NOT NULL,
    transfer_status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    checksum VARCHAR(64),
    session_id VARCHAR(255),
    receiver_username VARCHAR(255),
    error_message TEXT,
    sender_ip VARCHAR(45),
    sender_port INTEGER
);

-- Create receiver_transfers table (only if it doesn't exist)
CREATE TABLE IF NOT EXISTS receiver_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    sender_code VARCHAR(6) NOT NULL,
    transfer_status VARCHAR(50) NOT NULL,
    received_time TIMESTAMP NOT NULL,
    saved_time TIMESTAMP,
    file_path VARCHAR(500),
    checksum VARCHAR(64),
    session_id VARCHAR(255),
    sender_username VARCHAR(255),
    error_message TEXT,
    auto_saved BOOLEAN NOT NULL DEFAULT FALSE
);

-- Add new columns to existing sender_transfers table if they don't exist
ALTER TABLE sender_transfers ADD COLUMN IF NOT EXISTS sender_ip VARCHAR(45);
ALTER TABLE sender_transfers ADD COLUMN IF NOT EXISTS sender_port INTEGER;
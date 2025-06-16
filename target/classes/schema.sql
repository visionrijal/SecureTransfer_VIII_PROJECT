-- Drop existing tables if they exist
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS file_transfers CASCADE;
DROP TABLE IF EXISTS transfer_peers CASCADE;

-- Create users table
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    device_id VARCHAR(255),
    last_login TIMESTAMP,
    user_token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL
);

-- Create file_transfers table
CREATE TABLE file_transfers (
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

-- Create transfer_peers table
CREATE TABLE transfer_peers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    peer_id VARCHAR(255) NOT NULL,
    peer_type VARCHAR(50) NOT NULL,
    FOREIGN KEY (transfer_id) REFERENCES file_transfers(id)
); 
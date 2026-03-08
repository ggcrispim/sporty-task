-- Schema for Bet Settlement System
-- H2 Database initialization script

-- Drop tables if they exist (for clean restart)
DROP TABLE IF EXISTS bet_settlement;
DROP TABLE IF EXISTS bet;

-- Create Bet table
CREATE TABLE bet (
    bet_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    event_market_id BIGINT NOT NULL,
    event_winner_id BIGINT NOT NULL,
    bet_amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP
);

-- Create index for bet table
CREATE INDEX idx_event_status ON bet(event_id, status);

-- Create BetSettlement table
CREATE TABLE bet_settlement (
    settlement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bet_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    event_winner_id BIGINT NOT NULL,
    bet_amount DECIMAL(19,2) NOT NULL,
    settlement_amount DECIMAL(19,2) NOT NULL,
    status ENUM('PENDING', 'WON', 'LOST') NOT NULL,
    settled_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_BET FOREIGN KEY (bet_id) REFERENCES bet(bet_id)
);

-- Create index for bet_settlement table
CREATE INDEX idx_settlement_event ON bet_settlement(event_id);
CREATE INDEX idx_settlement_bet ON bet_settlement(bet_id);


INSERT INTO bet (user_id, event_id, event_market_id, event_winner_id, bet_amount, status, placed_at)
VALUES
    (1001, 100, 5001, 201, 50.00, 'PENDING', CURRENT_TIMESTAMP),
    (1002, 100, 5001, 202, 100.00, 'PENDING', CURRENT_TIMESTAMP),
    (1003, 100, 5002, 201, 75.00, 'PENDING', CURRENT_TIMESTAMP),
    (1004, 101, 5003, 301, 200.00, 'PENDING', CURRENT_TIMESTAMP),
    (1005, 101, 5003, 302, 150.00, 'PENDING', CURRENT_TIMESTAMP);
-- Crickzy MySQL Database Schema
-- Run this in MySQL command line: source crickzy_db.sql

CREATE DATABASE IF NOT EXISTS crickzy_db;
USE crickzy_db;

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

-- Players Table
CREATE TABLE IF NOT EXISTS players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    phone VARCHAR(50),
    role VARCHAR(100),
    matchType VARCHAR(100),
    isWicketKeeper TINYINT DEFAULT 0,
    isAvailable TINYINT DEFAULT 1,
    skillRating INT DEFAULT 50,
    expLevel FLOAT DEFAULT 1.0,
    availabilityDate VARCHAR(50),
    matchTime VARCHAR(50),
    profileImageUri TEXT
);

-- Teams Table
CREATE TABLE IF NOT EXISTS teams (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    location VARCHAR(255),
    requiredRole VARCHAR(100),
    budgetProgress INT DEFAULT 0,
    matchDate VARCHAR(50),
    matchTime VARCHAR(50),
    needsPlayers TINYINT DEFAULT 1
);

-- Turfs Table
CREATE TABLE IF NOT EXISTS turfs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    location VARCHAR(255),
    pricePerHour DOUBLE DEFAULT 0,
    imageUrl TEXT
);

-- Bookings Table
CREATE TABLE IF NOT EXISTS bookings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    turfId INT,
    date VARCHAR(50),
    time VARCHAR(50),
    userId INT
);

-- Matches Table
CREATE TABLE IF NOT EXISTS matches_table (
    id INT AUTO_INCREMENT PRIMARY KEY,
    team1Id INT,
    team2Id INT,
    team1Name VARCHAR(255),
    team2Name VARCHAR(255),
    team1Runs INT DEFAULT 0,
    team1Wickets INT DEFAULT 0,
    team1Overs INT DEFAULT 0,
    team2Runs INT DEFAULT 0,
    team2Wickets INT DEFAULT 0,
    team2Overs INT DEFAULT 0,
    matchStatus VARCHAR(50) DEFAULT 'Ongoing',
    winner VARCHAR(255) DEFAULT ''
);

-- Tournaments Table
CREATE TABLE IF NOT EXISTS tournaments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    location VARCHAR(255),
    startDate VARCHAR(50),
    endDate VARCHAR(50),
    entryFee DOUBLE DEFAULT 0,
    prizePool TEXT,
    organizerPhone VARCHAR(50),
    overs INT DEFAULT 20,
    groundName VARCHAR(255) DEFAULT '',
    ballType VARCHAR(50) DEFAULT 'Tennis',
    powerplayOvers INT DEFAULT 6
);

-- Requests Table
CREATE TABLE IF NOT EXISTS requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    playerId INT,
    teamId INT,
    status VARCHAR(50) DEFAULT 'Pending'
);

-- Insert default turfs
INSERT INTO turfs (name, location, pricePerHour, imageUrl) VALUES
('Green Valley Turf', 'Downtown', 1500.0, 'https://images.unsplash.com/photo-1540747913346-19e32dc3e97e'),
('Royal Cricket Ground', 'Uptown', 2000.0, 'https://images.unsplash.com/photo-1599839619722-39751411ea63'),
('Spartan Arena', 'West End', 1800.0, 'https://images.unsplash.com/photo-1531415074968-036ba1b575da'),
('Knight Riders Academy', 'East Coast', 2500.0, 'https://images.unsplash.com/photo-1624526267942-ab0ff8a3e972'),
('Lords Local Turf', 'Central Park', 1200.0, 'https://images.unsplash.com/photo-1563299796-b729d0af54a5'),
('Eclipse Stadium', 'North Suburbs', 2200.0, 'https://images.unsplash.com/photo-1518063319789-7217e6706b04'),
('Pro Pitch Arena', 'South District', 3000.0, 'https://images.unsplash.com/photo-1587329310686-91414b8e3cb7'),
('All-Star Turf', 'City Outskirts', 1000.0, 'https://images.unsplash.com/photo-1580629905303-faaa03202631');

SELECT 'Crickzy database created successfully!' AS Status;

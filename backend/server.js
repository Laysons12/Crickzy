const express = require('express');
const mysql = require('mysql2');
const cors = require('cors');
const bodyParser = require('body-parser');

const app = express();
app.use(cors());
app.use(bodyParser.json());

// ========== MySQL Connection ==========
const db = mysql.createConnection({
    host: 'localhost',
    user: 'root',           // Change if your MySQL user is different
    password: '',           // Change to your MySQL password
    database: 'crickzy_db'
});

db.connect((err) => {
    if (err) {
        console.error('MySQL Connection Error:', err);
        return;
    }
    console.log('Connected to MySQL database: crickzy_db');
});

// ========== USERS ==========
// Register
app.post('/api/register', (req, res) => {
    const { name, email, password } = req.body;
    const sql = 'INSERT INTO users (name, email, password) VALUES (?, ?, ?)';
    db.query(sql, [name, email, password], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, id: result.insertId });
    });
});

// Login
app.post('/api/login', (req, res) => {
    const { email, password } = req.body;
    const sql = 'SELECT * FROM users WHERE email = ? AND password = ?';
    db.query(sql, [email, password], (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        if (results.length > 0) {
            res.json({ success: true, user: results[0] });
        } else {
            res.json({ success: false, message: 'Invalid credentials' });
        }
    });
});

// Get all users
app.get('/api/users', (req, res) => {
    db.query('SELECT id, name, email FROM users', (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, data: results });
    });
});

// ========== PLAYERS ==========
app.post('/api/players', (req, res) => {
    const p = req.body;
    const sql = 'INSERT INTO players (name, phone, role, matchType, isWicketKeeper, isAvailable, skillRating, expLevel, availabilityDate, matchTime, profileImageUri) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)';
    db.query(sql, [p.name, p.phone, p.role, p.matchType, p.isWicketKeeper ? 1 : 0, p.isAvailable ? 1 : 0, p.skillRating, p.expLevel, p.availabilityDate, p.matchTime, p.profileImageUri || ''], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, id: result.insertId });
    });
});

app.get('/api/players', (req, res) => {
    db.query('SELECT * FROM players', (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, data: results });
    });
});

// ========== TEAMS ==========
app.post('/api/teams', (req, res) => {
    const t = req.body;
    const sql = 'INSERT INTO teams (name, location, requiredRole, budgetProgress, matchDate, matchTime, needsPlayers) VALUES (?, ?, ?, ?, ?, ?, ?)';
    db.query(sql, [t.name, t.location, t.requiredRole, t.budgetProgress, t.matchDate, t.matchTime, t.needsPlayers ? 1 : 0], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, id: result.insertId });
    });
});

app.get('/api/teams', (req, res) => {
    db.query('SELECT * FROM teams', (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, data: results });
    });
});

// ========== TURFS ==========
app.post('/api/turfs', (req, res) => {
    const t = req.body;
    const sql = 'INSERT INTO turfs (name, location, pricePerHour, imageUrl) VALUES (?, ?, ?, ?)';
    db.query(sql, [t.name, t.location, t.pricePerHour, t.imageUrl || ''], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, id: result.insertId });
    });
});

app.get('/api/turfs', (req, res) => {
    db.query('SELECT * FROM turfs', (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, data: results });
    });
});

// ========== BOOKINGS ==========
app.post('/api/bookings', (req, res) => {
    const b = req.body;
    const sql = 'INSERT INTO bookings (turfId, date, time, userId) VALUES (?, ?, ?, ?)';
    db.query(sql, [b.turfId, b.date, b.time, b.userId || -1], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, id: result.insertId });
    });
});

app.get('/api/bookings', (req, res) => {
    db.query('SELECT * FROM bookings', (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, data: results });
    });
});

// ========== MATCHES ==========
app.post('/api/matches', (req, res) => {
    const m = req.body;
    const sql = 'INSERT INTO matches_table (team1Id, team2Id, team1Name, team2Name, team1Runs, team1Wickets, team1Overs, team2Runs, team2Wickets, team2Overs, matchStatus, winner) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)';
    db.query(sql, [m.team1Id, m.team2Id, m.team1Name, m.team2Name, m.team1Runs || 0, m.team1Wickets || 0, m.team1Overs || 0, m.team2Runs || 0, m.team2Wickets || 0, m.team2Overs || 0, m.matchStatus || 'Ongoing', m.winner || ''], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, id: result.insertId });
    });
});

app.put('/api/matches/:id', (req, res) => {
    const m = req.body;
    const sql = 'UPDATE matches_table SET team1Runs=?, team1Wickets=?, team1Overs=?, team2Runs=?, team2Wickets=?, team2Overs=?, matchStatus=?, winner=? WHERE id=?';
    db.query(sql, [m.team1Runs, m.team1Wickets, m.team1Overs, m.team2Runs, m.team2Wickets, m.team2Overs, m.matchStatus, m.winner, req.params.id], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, affected: result.affectedRows });
    });
});

app.get('/api/matches', (req, res) => {
    db.query('SELECT * FROM matches_table', (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, data: results });
    });
});

// ========== TOURNAMENTS ==========
app.post('/api/tournaments', (req, res) => {
    const t = req.body;
    const sql = 'INSERT INTO tournaments (name, location, startDate, endDate, entryFee, prizePool, organizerPhone, overs, groundName, ballType, powerplayOvers) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)';
    db.query(sql, [t.name, t.location, t.startDate, t.endDate, t.entryFee, t.prizePool, t.organizerPhone, t.overs || 20, t.groundName || '', t.ballType || 'Tennis', t.powerplayOvers || 6], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, id: result.insertId });
    });
});

app.get('/api/tournaments', (req, res) => {
    db.query('SELECT * FROM tournaments', (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, data: results });
    });
});

// ========== REQUESTS ==========
app.post('/api/requests', (req, res) => {
    const r = req.body;
    const sql = 'INSERT INTO requests (playerId, teamId, status) VALUES (?, ?, ?)';
    db.query(sql, [r.playerId, r.teamId, r.status || 'Pending'], (err, result) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, id: result.insertId });
    });
});

app.get('/api/requests', (req, res) => {
    db.query('SELECT * FROM requests', (err, results) => {
        if (err) return res.json({ success: false, message: err.message });
        res.json({ success: true, data: results });
    });
});

// ========== START SERVER ==========
const PORT = 3000;
app.listen(PORT, '0.0.0.0', () => {
    console.log(`Crickzy API Server running on http://localhost:${PORT}`);
    console.log('Endpoints:');
    console.log('  POST /api/register, /api/login');
    console.log('  GET/POST /api/players, /api/teams, /api/turfs');
    console.log('  GET/POST /api/bookings, /api/matches, /api/tournaments, /api/requests');
});

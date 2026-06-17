<?php
/**
 * login.php
 * ─────────────────────────────────────────────────────────────
 * Riceve HTTP POST da login.html
 * Verifica le credenziali nella tabella `users` di TIW_DB
 * Se corrette avvia la sessione PHP e reindirizza alla dashboard
 */

// ── Configurazione database ───────────────────────────────────
define('DB_HOST',    'localhost');
define('DB_NAME',    'tiw_db');
define('DB_USER',    'root');   // ← cambia
define('DB_PASS',    '');       // ← cambia
define('DB_CHARSET', 'utf8mb4');
define('DB_PORT', '3306');

// ── Helper redirect ───────────────────────────────────────────
function redirect(string $url): void {
    header('Location: ' . $url);
    exit;
}

// ── Accetta solo POST ─────────────────────────────────────────
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    redirect('login.html');
}

// ── Avvia la sessione PHP ─────────────────────────────────────
session_start();

// ── Leggi i campi ────────────────────────────────────────────
$username = trim($_POST['username'] ?? '');
$password =       $_POST['password'] ?? '';

if (!$username || !$password) {
    redirect('login.html?errore=' . urlencode('Inserisci username e password.'));
}

// ── Connessione al database ───────────────────────────────────
try {
    $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET;
    $pdo = new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ]);
} catch (PDOException $e) {
    error_log('DB connection error: ' . $e->getMessage());
    redirect('login.html?errore=' . urlencode('Errore del server. Riprova più tardi.'));
}

// ── Cerca l'utente nel database ───────────────────────────────
$stmt = $pdo->prepare('
    SELECT id, username, password_hash, first_name, last_name, role, photo
    FROM users
    WHERE username = :username
    LIMIT 1
');
$stmt->execute([':username' => $username]);
$user = $stmt->fetch();

// ── Verifica password con password_verify() ───────────────────
// password_verify confronta la password in chiaro con il bcrypt hash
if (!$user || !password_verify($password, $user['password_hash'])) {
    redirect('login.html?errore=' . urlencode('Username o password non corretti.'));
}

// ── Salva i dati dell'utente nella sessione ───────────────────
$_SESSION['user_id']     = $user['id'];
$_SESSION['username']    = $user['username'];
$_SESSION['first_name']  = $user['first_name'];
$_SESSION['last_name']   = $user['last_name'];
$_SESSION['role']        = $user['role'];
$_SESSION['photo']       = $user['photo'];

// ── Reindirizza alla dashboard ────────────────────────────────
redirect('dashboard.php');
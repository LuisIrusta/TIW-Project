<?php
/**
 * register.php
 * ─────────────────────────────────────────────────────────────
 * Riceve HTTP POST da register.html
 * Inserisce un nuovo utente nella tabella `users` di TIW_DB
 *
 * Struttura tabella users:
 *   id, username, password_hash, first_name, last_name,
 *   photo (VARCHAR 255, nullable), role ENUM('administrative','technical')
 */
 
// ── Configurazione database ─
define('DB_HOST',    'localhost');
define('DB_NAME',    'tiw_db');       // nome esatto del tuo database
define('DB_USER',    'root');         // ← cambia con il tuo utente MySQL
define('DB_PASS',    '');             // ← cambia con la tua password MySQL
define('DB_CHARSET', 'utf8mb4');
 
// Cartella dove salvare le foto caricate (deve esistere e avere permessi di scrittura)
define('UPLOAD_DIR', __DIR__ . '/uploads/');
// 1. Costruiamo la stringa DSN (Data Source Name)
// Questa dice a PHP: tipo di DB, dove si trova, nome del DB e codifica caratteri
$dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET;

// 2. Definiamo le opzioni di configurazione
$options = [
    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION, // Trasforma gli errori SQL in eccezioni leggibili
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,       // Ritorna i dati come array associativi
    PDO::ATTR_EMULATE_PREPARES   => false,                  // Usa i prepared statements reali per sicurezza
    PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT => false,        // RISOLVE l'errore del certificato server che vedevi prima
];

// 3. Ora proviamo a connetterci usando le variabili appena create
try {
    $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
} catch (PDOException $e) {
    // Se c'è un errore (password sbagliata, database inesistente), lo stampiamo qui
    error_log('DB connection error: ' . $e->getMessage());
    die("ERRORE DI ACCESSO AL DATABASE: " . $e->getMessage());
}
// ── Helper redirect ───────────────────────────────────────────
function redirect(string $url): void {
    header('Location: ' . $url);
    exit;
}
 
// ── Accetta solo POST ─────────────────────────────────────────
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    redirect('register.html');
}
 
// ── Leggi i campi dalla richiesta HTTP POST ───────────────────
$first_name = trim($_POST['first_name'] ?? '');
$last_name  = trim($_POST['last_name']  ?? '');
$username   = trim($_POST['username']   ?? '');
$password   =       $_POST['password']  ?? '';
$conferma   =       $_POST['conferma']  ?? '';
$role       = trim($_POST['role']       ?? '');
 
// ── Validazione ───────────────────────────────────────────────
if (!$first_name || !$last_name || !$username || !$password || !$role) {
    redirect('register.html?errore=' . urlencode('Compila tutti i campi obbligatori.'));
}
 
if (strlen(trim($first_name)) < 1 || strlen(trim($last_name)) < 1) {
    redirect('register.html?errore=' . urlencode('Nome e cognome non possono essere vuoti.'));
}
if ($password !== $conferma) {
    redirect('register.html?errore=' . urlencode('Le password non corrispondono.'));
}
 
// Controlla che il ruolo sia uno dei due valori dell'ENUM
if (!in_array($role, ['administrative', 'technical'], true)) {
    redirect('register.html?errore=' . urlencode('Ruolo non valido.'));
}
 
// ── Gestione foto (opzionale) ─────────────────────────────────
$photo = null; // NULL nel DB se non caricata
 
if (!empty($_FILES['photo']['name'])) {
 
    $file     = $_FILES['photo'];
    $allowed  = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    $maxSize  = 2 * 1024 * 1024; // 2 MB
 
    if ($file['error'] !== UPLOAD_ERR_OK) {
        redirect('register.html?errore=' . urlencode('Errore durante il caricamento della foto.'));
    }
    if (!in_array(mime_content_type($file['tmp_name']), $allowed, true)) {
        redirect('register.html?errore=' . urlencode('Formato foto non valido. Usa JPG, PNG, GIF o WEBP.'));
    }
    if ($file['size'] > $maxSize) {
        redirect('register.html?errore=' . urlencode('La foto non deve superare 2 MB.'));
    }
 
    // Crea la cartella uploads/ se non esiste
    if (!is_dir(UPLOAD_DIR)) {
        mkdir(UPLOAD_DIR, 0755, true);
    }
 
    // Nome file univoco per evitare sovrascritture
    $ext   = pathinfo($file['name'], PATHINFO_EXTENSION);
    $fname = uniqid('photo_', true) . '.' . strtolower($ext);
 
    if (!move_uploaded_file($file['tmp_name'], UPLOAD_DIR . $fname)) {
        redirect('register.html?errore=' . urlencode('Impossibile salvare la foto.'));
    }
 
    $photo = 'uploads/' . $fname; // percorso relativo salvato nel DB
}
 
// ── Connessione al database ───────────────────────────────────
try {
    $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
} catch (PDOException $e) {
    // Questo interrompe tutto e ti scrive ESATTAMENTE perché non entra
    die("ERRORE DI ACCESSO: " . $e->getMessage());
}
// ── Controlla username duplicato ──────────────────────────────
$stmt = $pdo->prepare('SELECT id FROM users WHERE username = :username LIMIT 1');
$stmt->execute([':username' => $username]);
 
if ($stmt->fetch()) {
    redirect('register.html?errore=' . urlencode('Username già in uso. Scegline un altro.'));
}
 
// ── Hash della password (bcrypt) ──────────────────────────────
$hash = password_hash($password, PASSWORD_BCRYPT, ['cost' => 12]);
 
// ── INSERT nella tabella users ────────────────────────────────
try {
    $insert = $pdo->prepare('
        INSERT INTO users (username, password_hash, first_name, last_name, photo, role)
        VALUES (:username, :password_hash, :first_name, :last_name, :photo, :role)
    ');
    
    $risultato = $insert->execute([
        ':username'      => $username,
        ':password_hash' => $hash,
        ':first_name'    => $first_name,
        ':last_name'     => $last_name,
        ':photo'         => $photo,
        ':role'          => $role,
    ]);
    
    if ($risultato) {
        // Se arrivi qui, i dati SONO nel DB.
        die("Successo! Riga inserita con ID: " . $pdo->lastInsertId());
    }
    
} catch (PDOException $e) {
    // Questo stamperà l'errore esatto (es. "Column 'role' cannot be null")
    die("Errore SQL fatale: " . $e->getMessage());
}
// ── Successo: reindirizza al login ────────────────────────────
redirect('login.php?successo=' . urlencode('Account creato! Ora puoi accedere.'));
 
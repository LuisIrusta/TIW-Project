<?php
/**
 * logout.php
 * ─────────────────────────────────────────────────────────────
 * Distrugge la sessione PHP e reindirizza al login.
 * Accetta solo POST (il form nella dashboard usa method="POST").
 */

session_start();

// Svuota tutte le variabili di sessione
$_SESSION = [];

// Cancella il cookie di sessione dal browser
if (ini_get('session.use_cookies')) {
    $params = session_get_cookie_params();
    setcookie(
        session_name(),
        '',
        time() - 42000,
        $params['path'],
        $params['domain'],
        $params['secure'],
        $params['httponly']
        );
}

// Distrugge la sessione lato server
session_destroy();

// Reindirizza al login con messaggio di conferma
header('Location: login.html?successo=' . urlencode('Hai effettuato il logout con successo.'));
exit;
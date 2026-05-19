<?php
/**
 * dashboard.php
 * 
 *pagina temporanea da eliminare una volta create le interfacce
 */

session_start();

// ── Controlla se l'utente è loggato ──────────────────────────
if (empty($_SESSION['user_id'])) {
    header('Location: login.html?errore=' . urlencode('Devi effettuare il login.'));
    exit;
}
?>
<!DOCTYPE html>
<html lang="it">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Dashboard — TIW</title>
  <link rel="stylesheet" href="style.css" />
</head>
<body>

  <div class="card card--dashboard">

    <div class="card__header">
      <div class="logo">✦</div>
      <h1>Dashboard</h1>
    </div>

    <!-- Profilo utente -->
    <div class="profile">

      <?php if (!empty($_SESSION['photo'])): ?>
        <img class="profile__photo"
             src="<?php echo htmlspecialchars($_SESSION['photo']); ?>"
             alt="Foto profilo" />
      <?php else: ?>
        <div class="profile__avatar">
          <?php echo strtoupper(substr($_SESSION['first_name'], 0, 1) . substr($_SESSION['last_name'], 0, 1)); ?>
        </div>
      <?php endif; ?>

      <div class="profile__info">
        <p class="profile__name">
          <?php echo htmlspecialchars($_SESSION['first_name'] . ' ' . $_SESSION['last_name']); ?>
        </p>
        <p class="profile__username">@<?php echo htmlspecialchars($_SESSION['username']); ?></p>
        <span class="badge badge--<?php echo $_SESSION['role'] === 'administrative' ? 'admin' : 'tech'; ?>">
          <?php echo htmlspecialchars($_SESSION['role']); ?>
        </span>
      </div>

    </div>

    <div class="divider"></div>

    <p class="welcome">
      Benvenuto, <strong><?php echo htmlspecialchars($_SESSION['first_name']); ?></strong>!
      Sei connesso come utente <em><?php echo htmlspecialchars($_SESSION['role']); ?></em>.
    </p>

    <!-- Bottone logout: form HTTP POST verso logout.php -->
    <form action="logout.php" method="POST">
      <button type="submit" class="btn btn--logout">Esci dall'account</button>
    </form>

  </div>

</body>
</html>
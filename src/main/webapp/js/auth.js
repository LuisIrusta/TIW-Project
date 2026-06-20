/**
 * Gestore Login + Registrazione + Tab Switching (Architettura RIA)
 */
(function() {
    "use strict";

    document.addEventListener("DOMContentLoaded", () => {

        // ═══════════════════════════════════════════
        // TAB SWITCHING
        // ═══════════════════════════════════════════
        const tabBtns = document.querySelectorAll(".tab-btn");
        const tabContents = document.querySelectorAll(".tab-content");

        function switchTab(targetId) {
            tabContents.forEach(tc => tc.classList.remove("active"));
            tabBtns.forEach(btn => btn.classList.remove("active"));

            document.getElementById(targetId).classList.add("active");
            document.querySelector('[data-target="' + targetId + '"]').classList.add("active");
        }

        tabBtns.forEach(btn => {
            btn.addEventListener("click", () => {
                switchTab(btn.dataset.target);
            });
        });

        // ═══════════════════════════════════════════
        // REGISTRAZIONE
        // ═══════════════════════════════════════════
        const registerForm = document.getElementById("registerForm");
        const registerErrorDiv = document.getElementById('register-error');

        if (registerForm) {
            registerForm.addEventListener("submit", (e) => {
                e.preventDefault();

                if (registerErrorDiv) registerErrorDiv.style.display = 'none';

                const payload = {
                    first_name: document.getElementById("reg-firstName").value,
                    last_name: document.getElementById("reg-lastName").value,
                    username: document.getElementById("reg-username").value,
                    password: document.getElementById("reg-password").value,
                    conferma: document.getElementById("reg-conferma").value,
                    role: document.getElementById("reg-role").value
                };

                fetch('api/register', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json; charset=UTF-8'
                    },
                    body: JSON.stringify(payload)
                })
                .then(response => {
                    if (response.ok) {
                        return response.json();
                    } else {
                        return response.text().then(errorText => {
                            throw new Error(errorText || "Errore durante la registrazione.");
                        });
                    }
                })
                .then(data => {
                    if (data.success) {
                        alert("Registrazione completata con successo! Ora puoi accedere.");
                        registerForm.reset();
                        switchTab('login-section');
                    }
                })
                .catch(error => {
                    console.error("Errore riscontrato:", error);
                    if (registerErrorDiv) {
                        registerErrorDiv.textContent = error.message;
                        registerErrorDiv.style.display = 'block';
                    }
                });
            });
        }

        // ═══════════════════════════════════════════
        // LOGIN
        // ═══════════════════════════════════════════
        const loginForm = document.getElementById('loginForm');
        const loginErrorDiv = document.getElementById('login-error');

        if (loginForm) {
            loginForm.addEventListener('submit', function(e) {
                e.preventDefault();

                if (loginErrorDiv) loginErrorDiv.style.display = 'none';

                const payload = {
                    username: document.getElementById('login-username').value,
                    password: document.getElementById('login-password').value,
                    tipo: document.getElementById('login-role').value
                };

                fetch('api/login', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json; charset=UTF-8'
                    },
                    body: JSON.stringify(payload)
                })
                .then(function(response) {
                    if (response.ok) {
                        return response.json();
                    } else {
                        return response.text().then(function(errorText) {
                            throw new Error(errorText || "Errore durante il login.");
                        });
                    }
                })
                .then(function(data) {
					if (data.success !== false) {

					    sessionStorage.setItem("loggedUser", JSON.stringify(data.user || data));
					    const tipo = payload.tipo;
					    if (tipo === "TECHNICAL") {
					        window.location.href = "home-manager.html"; // <-- Anche loro diventeranno HTML statici
					    } else if (tipo === "COLLABORATOR") {
					        // 🚀 Salto diretto alla pagina HTML statica
					        window.location.href = "home-collaborator.html"; 
					    } else if (tipo === "ADMINISTRATIVE") {
					        window.location.href = "home-admin.html";
					    }
					}
                })
                .catch(function(error) {
                    if (loginErrorDiv) {
                        loginErrorDiv.textContent = error.message;
                        loginErrorDiv.style.display = 'block';
                    }
                });
            });
        }

    });

})();
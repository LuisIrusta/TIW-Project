/**
 * Gestore Home Collaboratore (Architettura RIA Pura)
 */
(function() {
    "use strict";

    // ── 1. CONTROLLO SICUREZZA IMMEDIATO (Sentinella Client-Side) ──
    const userJson = sessionStorage.getItem("loggedUser");
    if (!userJson) {
        // Non sei loggato? Fuori! Ti rimando alla pagina di login
        window.location.href = "index.html";
        return; 
    }
    const loggedUser = JSON.parse(userJson);

    // Stato corrente della selezione filtri
    let state = {
        projectId: null,
        wpId: null,
        taskId: null
    };

    // ── 2. CARICAMENTO DATI DAL SERVER ─────────────────────
    function loadHomeCollaborator() {
        const errorEl = document.getElementById('hc-error');
        const successEl = document.getElementById('hc-success');
        if (errorEl) errorEl.style.display = 'none';
        if (successEl) successEl.style.display = 'none';

        // Prepariamo i parametri da mandare all'API dei progetti
        const params = new URLSearchParams();
        if (state.projectId) params.set('projectId', state.projectId);
        if (state.wpId)      params.set('wpId', state.wpId);
        if (state.taskId)    params.set('taskId', state.taskId);
        
        // 💡 PASSO L'ID COLLABORATORE PRESO DAL SESSION STORAGE
        params.set('collaboratorId', loggedUser.id); 

        fetch('api/home-collaborator?' + params.toString(), {
            method: 'GET'
        })
        .then(response => {
            // Se la sessione scade anche lato server (401), puliamo tutto e cacciamo l'utente
            if (response.status === 401) {
                sessionStorage.clear();
                window.location.href = "index.html";
                return;
            }
            if (response.ok) return response.json();
            return response.json().then(data => {
                throw new Error(data.error || "Errore nel caricamento.");
            });
        })
        .then(data => {
            renderHomeCollaborator(data);
        })
        .catch(error => {
            if (errorEl) {
                errorEl.textContent = error.message;
                errorEl.style.display = 'block';
            }
        });
    }

    // Espongo la funzione globalmente per lo script inline dell'HTML
    window.loadHomeCollaborator = loadHomeCollaborator;

    // ── 3. RENDERING DINAMICO DELL'INTERFACCIA ──────────────
    function renderHomeCollaborator(data) {
        // Stampiamo subito il nome e il cognome recuperati dal sessionStorage
        const fullnameEl = document.getElementById('hc-fullname');
        if (fullnameEl) {
            fullnameEl.textContent = loggedUser.firstName + ' ' + loggedUser.lastName;
        }

        const projectSelect = document.getElementById('hc-project');
        const wpField = document.getElementById('hc-wp-field');
        const wpSelect = document.getElementById('hc-wp');
        const taskField = document.getElementById('hc-task-field');
        const taskSelect = document.getElementById('hc-task');
        const detailDiv = document.getElementById('hc-task-detail');

        // Svuota e popola Progetti
        if (projectSelect) {
            projectSelect.innerHTML = '<option value="">-- seleziona un progetto --</option>';
            (data.progetti || []).forEach(p => {
                const opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = p.name || p.title;
                if (data.selectedProject && p.id === data.selectedProject.id) opt.selected = true;
                projectSelect.appendChild(opt);
            });
        }

        // Gestione campo Work Package
        if (wpField && wpSelect) {
            if (data.selectedProject && data.wps) {
                wpField.style.display = 'block';
                wpSelect.innerHTML = '<option value="">-- seleziona un WP --</option>';
                data.wps.forEach(wp => {
                    const opt = document.createElement('option');
                    opt.value = wp.id;
                    opt.textContent = wp.name || wp.title;
                    if (data.selectedWp && wp.id === data.selectedWp.id) opt.selected = true;
                    wpSelect.appendChild(opt);
                });
            } else {
                wpField.style.display = 'none';
                wpSelect.innerHTML = '';
            }
        }

        // Gestione campo Task
        if (taskField && taskSelect) {
            if (data.selectedWp && data.tasks) {
                taskField.style.display = 'block';
                taskSelect.innerHTML = '<option value="">-- seleziona un task --</option>';
                data.tasks.forEach(t => {
                    const opt = document.createElement('option');
                    opt.value = t.id;
                    opt.textContent = t.name || t.title;
                    if (data.selectedTask && t.id === data.selectedTask.id) opt.selected = true;
                    taskSelect.appendChild(opt);
                });
            } else {
                taskField.style.display = 'none';
                taskSelect.innerHTML = '';
            }
        }

        // Gestione dettaglio ore
        if (detailDiv) {
            if (data.selectedTask) {
                detailDiv.style.display = 'block';
				document.getElementById('hc-tot-previste').textContent = 
				        (data.totPreviste !== undefined && data.totPreviste !== null) ? data.totPreviste : '-';
				        
				    document.getElementById('hc-tot-lavorate').textContent = 
				        (data.totLavorate !== undefined && data.totLavorate !== null) ? data.totLavorate : '-';
                const meseSelect = document.getElementById('hc-mese');
                if (meseSelect) {
                    meseSelect.innerHTML = '';
                    (data.mesiTask || []).forEach(m => {
                        const opt = document.createElement('option');
                        opt.value = m;
                        opt.textContent = 'Mese ' + m;
                        meseSelect.appendChild(opt);
                    });
                }
            } else {
                detailDiv.style.display = 'none';
            }
        }
    }

    // ── 4. AGGANCIO DEI LISTENER AI COMPONENTI DOM ──────────
    document.addEventListener("DOMContentLoaded", () => {
        const projectSelect = document.getElementById('hc-project');
        const wpSelect = document.getElementById('hc-wp');
        const taskSelect = document.getElementById('hc-task');
        const oreForm = document.getElementById('hc-ore-form');
        const logoutBtn = document.getElementById('btn-hc-logout');

        if (projectSelect) {
            projectSelect.addEventListener('change', function() {
                state.projectId = this.value || null;
                state.wpId = null;
                state.taskId = null;
                loadHomeCollaborator();
            });
        }

        if (wpSelect) {
            wpSelect.addEventListener('change', function() {
                state.wpId = this.value || null;
                state.taskId = null;
                loadHomeCollaborator();
            });
        }

        if (taskSelect) {
            taskSelect.addEventListener('change', function() {
                state.taskId = this.value || null;
                loadHomeCollaborator();
            });
        }

        if (oreForm) {
            oreForm.addEventListener('submit', function(e) {
                e.preventDefault();
                const errorEl = document.getElementById('hc-error');
                const successEl = document.getElementById('hc-success');
                
                const payload = {
                    taskId: state.taskId,
                    mese: document.getElementById('hc-mese').value,
                    ore: document.getElementById('hc-ore').value
                };

                fetch('api/save-worked-hours', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
                    body: JSON.stringify(payload)
                })
                .then(response => {
                    if (response.ok) return response.json();
                    return response.json().then(data => {
                        throw new Error(data.error || "Errore nel salvataggio.");
                    });
                })
                .then(data => {
                    if (successEl) {
                        successEl.textContent = data.message || "Ore salvate correttamente!";
                        successEl.style.display = 'block';
                    }
                    if (errorEl) errorEl.style.display = 'none';
                    document.getElementById('hc-ore').value = '';
                    loadHomeCollaborator();
                })
                .catch(error => {
                    if (errorEl) {
                        errorEl.textContent = error.message;
                        errorEl.style.display = 'block';
                    }
                    if (successEl) successEl.style.display = 'none';
                });
            });
        }

        if (logoutBtn) {
            logoutBtn.addEventListener('click', function() {
                fetch('api/logout', { method: 'POST' })
                .then(() => {
                    sessionStorage.clear();
                    window.location.href = "index.html";
                });
            });
        }
    });

})();
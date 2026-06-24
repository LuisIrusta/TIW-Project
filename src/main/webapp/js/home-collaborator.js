(function() {
    "use strict";

    const userJson = sessionStorage.getItem("loggedUser");
    if (!userJson) {
        window.location.href = "index.html";
        return;
    }
    const loggedUser = JSON.parse(userJson);
    let treeData = null;

    let state = {
        projectId: null,
        taskId: null
    };

    // ───────────────────────────────────────────────
    // 1. CARICAMENTO UNICO DAL SERVER
    // ───────────────────────────────────────────────
    function loadHomeCollaborator() {
        const errorEl = document.getElementById('hc-error');
        const successEl = document.getElementById('hc-success');
        if (errorEl) errorEl.style.display = 'none';
        if (successEl) successEl.style.display = 'none';

        fetch('api/home-collaborator', { method: 'GET' })
        .then(response => {
            if (response.status === 401) {
                sessionStorage.clear();
                window.location.href = "index.html";
                return null;
            }
            if (response.ok) return response.json();
            return response.json().then(data => { throw new Error(data.error); });
        })
        .then(data => {
            if (!data) return;
            treeData = data;
            renderProjects();
        })
        .catch(err => {
            errorEl.textContent = err.message;
            errorEl.style.display = 'block';
        });
    }

    window.loadHomeCollaborator = loadHomeCollaborator;

    // ───────────────────────────────────────────────
    // 2. FINDERS
    // ───────────────────────────────────────────────
    function findProject(projectId) {
        return treeData.progetti.find(p => String(p.id) === String(projectId)) || null;
    }

    // ───────────────────────────────────────────────
    // 3. RENDER PROGETTI
    // ───────────────────────────────────────────────
    function renderProjects() {
        const fullnameEl = document.getElementById('hc-fullname');
        fullnameEl.textContent =
            (treeData.collaboratore.firstName || treeData.collaboratore.nome) + " " +
            (treeData.collaboratore.lastName || treeData.collaboratore.cognome);

        const projectSelect = document.getElementById('hc-project');
        projectSelect.innerHTML = '<option value="">-- seleziona un progetto --</option>';

        treeData.progetti.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p.id;
            opt.textContent = p.title || p.name;
            projectSelect.appendChild(opt);
        });
    }

    // ───────────────────────────────────────────────
    // 4. TABELLA TASK (CON SELETTORE MESE)
    // ───────────────────────────────────────────────
    function renderTaskTable() {
        const tableDiv = document.getElementById("hc-task-table");
        tableDiv.innerHTML = "";

        const project = findProject(state.projectId);
        if (!project || !project.workPackages) return;

        const allTasks = project.workPackages.flatMap(wp => wp.tasks || []);

        if (allTasks.length === 0) {
            tableDiv.innerHTML = "<p>Nessun task assegnato.</p>";
            return;
        }

        let html = `
            <table class="hc-table">
                <thead>
                    <tr>
                        <th>Work Package</th>
                        <th>Task</th>
                        <th>Ore previste</th>
                        <th>Mese</th>
                        <th>Ore lavorate</th>
                    </tr>
                </thead>
                <tbody>
        `;

        allTasks.forEach(task => {
            const wp = project.workPackages.find(w => w.tasks.some(t => t.id === task.id));

            html += `
                <tr data-task-id="${task.id}">
                    <td>${wp.title}</td>
                    <td>${task.title}</td>
                    <td>${task.totalPlannedHours}</td>

                    <td>
                        <select class="mese-select">
                            ${Array.from({length: task.endMonth - task.startMonth + 1}, (_, i) => {
                                const m = task.startMonth + i;
                                return `<option value="${m}">M${m}</option>`;
                            }).join("")}
                        </select>
                    </td>

                    <td class="editable" contenteditable="true">${task.totalWorkedHours}</td>
                </tr>
            `;
        });

        html += `</tbody></table>`;
        tableDiv.innerHTML = html;

        // EVENTI DI EDITING INLINE
        tableDiv.querySelectorAll("td.editable").forEach(cell => {
            cell.addEventListener("focus", function () {
                this.dataset.oldValue = this.textContent.trim();
            });

            cell.addEventListener("blur", function () {
                const newValue = this.textContent.trim();
                const oldValue = this.dataset.oldValue;

                if (!/^\d+$/.test(newValue)) {
                    this.textContent = oldValue;
                    showError("Le ore devono essere un numero intero non negativo.");
                    return;
                }

                if (newValue === oldValue) return;

                const row = this.closest("tr");
                const taskId = row.dataset.taskId;
                const mese = row.querySelector(".mese-select").value;

                state.taskId = taskId;
                saveWorkedHours(parseInt(newValue, 10), parseInt(mese, 10));
            });
        });
    }

    // ───────────────────────────────────────────────
    // 5. SALVATAGGIO ORE (CON MESE)
    // ───────────────────────────────────────────────
    function saveWorkedHours(ore, mese) {
        const errorEl = document.getElementById('hc-error');
        const successEl = document.getElementById('hc-success');

        const payload = { 
            taskId: state.taskId, 
            ore: ore,
            mese: mese
        };

        return fetch('api/save-worked-hours', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json; charset=UTF-8' },
            body: JSON.stringify(payload)
        })
        .then(response => {
            if (response.ok) return response.json();
            return response.json().then(data => { throw new Error(data.error); });
        })
        .then(data => {

            const project = findProject(state.projectId);
            project.workPackages.forEach(wp => {
                wp.tasks.forEach(t => {
                    if (String(t.id) === String(state.taskId)) {
                        t.totalWorkedHours = ore;
                    }
                });
            });

            renderTaskTable();

            successEl.textContent = data.message || "Ore salvate correttamente!";
            successEl.style.display = 'block';
            errorEl.style.display = 'none';
        })
        .catch(err => {
            errorEl.textContent = err.message;
            errorEl.style.display = 'block';
            successEl.style.display = 'none';
        });
    }

    function showError(msg) {
        const errorEl = document.getElementById('hc-error');
        errorEl.textContent = msg;
        errorEl.style.display = 'block';
    }

    // ───────────────────────────────────────────────
    // 6. EVENTI
    // ───────────────────────────────────────────────
    document.addEventListener("DOMContentLoaded", () => {
        loadHomeCollaborator();

        const projectSelect = document.getElementById('hc-project');
        const logoutBtn = document.getElementById('btn-hc-logout');

        projectSelect.addEventListener('change', function() {
            state.projectId = this.value || null;
            renderTaskTable();
        });

        logoutBtn.addEventListener('click', function() {
            fetch('api/logout', { method: 'POST' })
            .then(() => {
                sessionStorage.clear();
                window.location.href = "index.html";
            });
        });
    });

})();

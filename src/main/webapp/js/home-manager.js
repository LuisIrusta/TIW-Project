/* ============================================================
   home-manager.js
   Logica dedicata esclusivamente alla servlet /api/home-manager
   (nessuna chiamata ad altre servlet, sola lettura)
   ============================================================ */

var API = "";

function hmShowError(msg) {
  var el = document.getElementById("hm-error");
  var ok = document.getElementById("hm-success");
  ok.style.display = "none";
  el.textContent = msg;
  el.style.display = "block";
}

function setFullname(user) {
  var span = document.getElementById("hm-fullname");
  if (span) {
    span.textContent = user ? (user.name || user.username || "Responsabile") : "-";
  }
}

function fillSelect(selectEl, items, placeholder, selectedId, labelFn) {
  selectEl.innerHTML = "<option value=\"\">-- " + placeholder + " --</option>";
  items = items || [];
  for (var i = 0; i < items.length; i++) {
    var item = items[i];
    var opt = document.createElement("option");
    opt.value = item.id;
    opt.textContent = labelFn(item);
    if (item.id === selectedId) {
      opt.selected = true;
    }
    selectEl.appendChild(opt);
  }
}

function getJSON(url) {
  return fetch(API + url, { credentials: "same-origin" }).then(function (res) {
    return res.json().catch(function () { return {}; }).then(function (data) {
      if (!res.ok) {
        var msg = data.error || data.message || ("Errore " + res.status);
        var err = new Error(msg);
        err.payload = data;
        err.status = res.status;
        throw err;
      }
      return data;
    });
  });
}

var homeState = { projectId: null, wpId: null, taskId: null };

function loadHome(projectId, wpId, taskId) {
  var url = "api/home-manager";
  var qs = [];
  if (projectId) { qs.push("projectId=" + projectId); }
  if (wpId) { qs.push("wpId=" + wpId); }
  if (taskId) { qs.push("taskId=" + taskId); }
  if (qs.length > 0) { url += "?" + qs.join("&"); }

  getJSON(url).then(function (data) {
    setFullname(data.responsabile);

    homeState.projectId = data.selectedProject ? data.selectedProject.id : null;
    homeState.wpId = data.selectedWp ? data.selectedWp.id : null;
    homeState.taskId = data.selectedTask ? data.selectedTask.id : null;

    var projectSelect = document.getElementById("home-project");
    fillSelect(projectSelect, data.progetti, "seleziona un progetto", homeState.projectId, function (p) {
      return p.title || p.titolo || ("Progetto #" + p.id);
    });
    projectSelect.onchange = function () {
      var val = parseInt(projectSelect.value, 10);
      loadHome(isNaN(val) ? null : val, null, null);
    };

    var wpField = document.getElementById("home-wp-field");
    var wpSelect = document.getElementById("home-wp");
    if (homeState.projectId) {
      wpField.style.display = "block";
      fillSelect(wpSelect, data.wps, "seleziona un WP", homeState.wpId, function (wp) {
        return (wp.title || wp.titolo || ("WP #" + wp.id)) + " (mesi " + wp.startMonth + "-" + wp.endMonth + ")";
      });
      wpSelect.onchange = function () {
        var val = parseInt(wpSelect.value, 10);
        loadHome(homeState.projectId, isNaN(val) ? null : val, null);
      };
    } else {
      wpField.style.display = "none";
    }

    var taskField = document.getElementById("home-task-field");
    var taskSelect = document.getElementById("home-task");
    if (homeState.wpId) {
      taskField.style.display = "block";
      fillSelect(taskSelect, data.tasks, "seleziona un task", homeState.taskId, function (t) {
        return (t.title || t.titolo || ("Task #" + t.id)) + " (mesi " + t.startMonth + "-" + t.endMonth + ")";
      });
      taskSelect.onchange = function () {
        var val = parseInt(taskSelect.value, 10);
        loadHome(homeState.projectId, homeState.wpId, isNaN(val) ? null : val);
      };
    } else {
      taskField.style.display = "none";
    }

    renderAssignmentPanel(data);
  }).catch(function (e) {
    hmShowError(e.message);
  });
}

function hmShowSuccess(msg) {
  var el = document.getElementById("hm-success");
  var err = document.getElementById("hm-error");
  err.style.display = "none";
  el.textContent = msg;
  el.style.display = "block";
  setTimeout(function () { el.style.display = "none"; }, 4000);
}

function renderAssignmentPanel(data, errorInfo) {
  var panel = document.getElementById("home-assignment-panel");
  var task = data.selectedTask;
  if (!task) {
    panel.innerHTML = "<p class=\"empty\">Seleziona un task per vedere ore previste e collaboratori.</p>";
    return;
  }

  var months = data.mesiTask || [];
  var plannedHours = errorInfo ? errorInfo.submittedHours : (data.orePreviste || {});
  var assignedIdsArr = errorInfo ? (errorInfo.submittedCollaborators || []) : (data.assignedIds || []);
  var assignedIds = {};
  for (var i = 0; i < assignedIdsArr.length; i++) {
    assignedIds[assignedIdsArr[i]] = true;
  }
  var collaborators = data.collaboratori || [];

  var monthHeaders = "";
  var monthCells = "";
  for (var m = 0; m < months.length; m++) {
    var mese = months[m];
    monthHeaders += "<th>M" + mese + "</th>";
    var val = plannedHours[mese] || "";
    monthCells += "<td><input type=\"number\" min=\"0\" data-month=\"" + mese + "\" value=\"" + val + "\"></td>";
  }

  var collabList = "";
  for (var c = 0; c < collaborators.length; c++) {
    var collaborator = collaborators[c];
    var name = collaborator.name || collaborator.username || ("Utente #" + collaborator.id);
    var isAssigned = assignedIds[collaborator.id] === true;
    collabList += "<label class=\"chip" + (isAssigned ? " active" : "") + "\" data-cid=\"" + collaborator.id + "\">" +
                  "<input type=\"checkbox\" value=\"" + collaborator.id + "\"" + (isAssigned ? " checked" : "") + ">" +
                  name + "</label>";
  }
  if (collabList === "") {
    collabList = "<span class=\"empty\">Nessun collaboratore disponibile.</span>";
  }

  var taskName = task.title || task.titolo || "";
  var errorHtml = errorInfo ? "<p class=\"msg-error\" style=\"display:block;\">" + errorInfo.error + "</p>" : "";

  /* Bottone "Assegna progetto": visibile solo se il progetto è in stato CREATED */
  var projectState = data.selectedProject
    ? (data.selectedProject.state || data.selectedProject.stato || null)
    : null;

  // Convertiamo lo stato in MAIUSCOLO prima del controllo per evitare bug
  if (projectState) { projectState = projectState.toUpperCase(); }

  var assignBtn = (projectState === "CREATED")
    ? "<button class=\"btn-success\" id=\"assign-project-btn\">Assegna progetto</button>"
    : "";

  panel.innerHTML =
    "<div class=\"breadcrumb\">Progetto <b>#" + homeState.projectId + "</b> &rarr; WP <b>#" + homeState.wpId +
    "</b> &rarr; Task <b>#" + task.id + "</b> - " + taskName + "</div>" +
    "<h3>Ore previste per mese</h3>" +
    "<table class=\"hours\"><thead><tr>" + monthHeaders + "</tr></thead><tbody><tr>" + monthCells + "</tr></tbody></table>" +
    "<h3>Collaboratori assegnati</h3>" +
    "<div class=\"collab-chips\" id=\"collab-chips\">" + collabList + "</div>" +
    errorHtml +
    "<div class=\"row-actions\">" +
      "<button class=\"btn-primary\" id=\"save-assignment-btn\">Salva assegnazione</button>" +
      assignBtn +
    "</div>";

  var chips = panel.querySelectorAll(".chip");
  for (var k = 0; k < chips.length; k++) {
    var checkbox = chips[k].querySelector("input[type=checkbox]");
    if (checkbox) {
      checkbox.addEventListener("change", function () {
        var label = this.closest(".chip");
        if (this.checked) {
          label.classList.add("active");
        } else {
          label.classList.remove("active");
        }
      });
    }
  }

  document.getElementById("save-assignment-btn").addEventListener("click", function () {
    saveAssignment(task);
  });

  var assignProjBtn = document.getElementById("assign-project-btn");
  if (assignProjBtn) {
    assignProjBtn.addEventListener("click", function () {
      assignProject();
    });
  }
}
function saveAssignment(task) {
  var panel = document.getElementById("home-assignment-panel");
  var hours = {};
  var hourInputs = panel.querySelectorAll("input[type=number]");
  for (var i = 0; i < hourInputs.length; i++) {
    hours[hourInputs[i].dataset.month] = hourInputs[i].value;
  }

  var collaborators = [];
  var collabInputs = panel.querySelectorAll(".chip.active input");
  for (var j = 0; j < collabInputs.length; j++) {
    collaborators.push(parseInt(collabInputs[j].value, 10));
  }

  var payload = { taskId: task.id, hours: hours, collaborators: collaborators };

  fetch(API + "api/save-assignment", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  }).then(function (res) {
    return res.json().catch(function () { return {}; }).then(function (data) {
      if (!res.ok) {
        var err = new Error(data.error || data.message || ("Errore " + res.status));
        err.payload = data;
        throw err;
      }
      return data;
    });
  }).then(function (data) {
    hmShowSuccess(data.message || "Assegnazione salvata.");
    loadHome(homeState.projectId, homeState.wpId, homeState.taskId);
  }).catch(function (e) {
    hmShowError(e.message);
    // SaveAssignment in caso di errore restituisce submittedHours/submittedCollaborators
    // direttamente nel payload: li riusiamo per non far perdere all'utente
    // quello che aveva digitato ma non ancora salvato con successo.
    if (e.payload && (e.payload.submittedHours || e.payload.submittedCollaborators)) {
      getJSON("api/home-manager?projectId=" + homeState.projectId + "&wpId=" + homeState.wpId + "&taskId=" + homeState.taskId)
        .then(function (refreshed) {
          renderAssignmentPanel(refreshed, {
            error: e.message,
            submittedHours: e.payload.submittedHours || hours,
            submittedCollaborators: e.payload.submittedCollaborators
              ? Array.from(e.payload.submittedCollaborators)
              : collaborators
          });
        });
    } else {
      // fallback: se il server non ha rimandato i submitted, usiamo comunque
      // quello che l'utente aveva nel form al momento del click
      getJSON("api/home-manager?projectId=" + homeState.projectId + "&wpId=" + homeState.wpId + "&taskId=" + homeState.taskId)
        .then(function (refreshed) {
          renderAssignmentPanel(refreshed, {
            error: e.message,
            submittedHours: hours,
            submittedCollaborators: collaborators
          });
        });
    }
  });
}

function assignProject() {
  if (!homeState.projectId) return;

  // Cattura lo stato corrente del form (ore/collaboratori NON ancora salvati)
  // così, se l'assegnazione fallisce e la pagina viene "ricaricata",
  // l'utente non perde quello che aveva digitato.
  var panel = document.getElementById("home-assignment-panel");
  var hours = {};
  var hourInputs = panel.querySelectorAll("input[type=number]");
  for (var i = 0; i < hourInputs.length; i++) {
    hours[hourInputs[i].dataset.month] = hourInputs[i].value;
  }
  var collaborators = [];
  var collabInputs = panel.querySelectorAll(".chip.active input");
  for (var j = 0; j < collabInputs.length; j++) {
    collaborators.push(parseInt(collabInputs[j].value, 10));
  }

  fetch(API + "api/assign-project", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ projectId: homeState.projectId })
  }).then(function (res) {
    return res.json().catch(function () { return {}; }).then(function (data) {
      if (!res.ok) {
        var err = new Error(data.error || data.message || ("Errore " + res.status));
        err.payload = data;
        throw err;
      }
      return data;
    });
  }).then(function (data) {
    var msg = data.message || "Progetto assegnato.";
    try { msg = decodeURIComponent(msg); } catch (e) {}
    hmShowSuccess(msg);
    loadHome(homeState.projectId, homeState.wpId, homeState.taskId);
  }).catch(function (e) {
    // AssignProject in caso di blocco restituisce { error, blockers: [...] }
    // (struttura diversa da SaveAssignment): componiamo un messaggio leggibile.
    var fullMessage = e.message;
    if (e.payload && Array.isArray(e.payload.blockers) && e.payload.blockers.length > 0) {
      fullMessage += ": " + e.payload.blockers.join("; ");
    }
    hmShowError(fullMessage);

    // "Ricarica" la home ma ripristina nel form quanto l'utente aveva
    // digitato e non ancora salvato con SALVA.
    getJSON("api/home-manager?projectId=" + homeState.projectId + "&wpId=" + homeState.wpId + "&taskId=" + homeState.taskId)
      .then(function (refreshed) {
        renderAssignmentPanel(refreshed, {
          error: fullMessage,
          submittedHours: hours,
          submittedCollaborators: collaborators
        });
      });
  });
}

/* =====================================================================
   MONITOR PROGETTI — /api/monitor-projects + /api/conclude-project
   ===================================================================== */

var monitorState = { projectId: null };

function hourCell(mh) {
  if (!mh || !mh.active) { return "<td class=\"inactive\">-</td>"; }
  return "<td>" + mh.workedHours + "/" + mh.plannedHours + "h</td>";
}

function badge(stato) {
  var map = {
    CREATED: ["created", "Creato"],
    ASSIGNED: ["assigned", "Assegnato"],
    CONCLUDED: ["concluded", "Concluso"]
  };
  var pair = map[stato] || ["concluded", stato];
  return "<span class=\"badge " + pair[0] + "\">" + pair[1] + "</span>";
}

function loadMonitorProjects(projectId) {
  var url = "api/monitor-projects";
  if (projectId) { url += "?projectId=" + projectId; }

  getJSON(url).then(function (data) {
    monitorState.projectId = data.selected ? data.selected.id : null;

    var select = document.getElementById("monitor-project");
    fillSelect(select, data.progetti, "seleziona un progetto", monitorState.projectId, function (p) {
      return (p.title || p.titolo || ("Progetto #" + p.id)) + " - " + (p.state || p.stato || "");
    });
    select.onchange = function () {
      var val = parseInt(select.value, 10);
      loadMonitorProjects(isNaN(val) ? null : val);
    };

    renderMonitorDetail(data);
  }).catch(function (e) {
    hmShowError(e.message);
  });
}

function renderMonitorDetail(data) {
  var box = document.getElementById("monitor-detail");
  var project = data.selected;
  if (!project) {
    box.innerHTML = "<p class=\"empty\">Nessun progetto selezionato.</p>";
    return;
  }

  var months = data.mesi || [];
  var header = "<th>WP / Task</th>";
  for (var m = 0; m < months.length; m++) {
    header += "<th>M" + months[m] + "</th>";
  }

  var rows = "";
  var wps = project.workPackages || [];
  for (var w = 0; w < wps.length; w++) {
    var wp = wps[w];
    var wpName = wp.title || wp.titolo || ("WP#" + wp.id);
    rows += "<tr class=\"wp-row\"><td>" + wpName + "</td>";
    var wpHours = wp.monthHours || [];
    for (var i = 0; i < wpHours.length; i++) {
      rows += hourCell(wpHours[i]);
    }
    rows += "</tr>";

    var tasks = wp.tasks || [];
    for (var t = 0; t < tasks.length; t++) {
      var task = tasks[t];
      var taskName = task.title || task.titolo || ("Task#" + task.id);
      rows += "<tr><td style=\"padding-left:18px;color:#777;\">" + taskName + "</td>";
      var taskHours = task.monthHours || [];
      for (var j = 0; j < taskHours.length; j++) {
        rows += hourCell(taskHours[j]);
      }
      rows += "</tr>";
    }
  }

  var projectName = project.title || project.titolo || "";
  var concludeWarning = !data.canConclude
    ? "<p class=\"msg-error\" style=\"display:block;text-align:right;\">Concludibile solo se assegnato e con tutte le ore lavorate &ge; previste.</p>"
    : "";

  box.innerHTML =
    "<div class=\"breadcrumb\">Progetto <b>#" + project.id + "</b> - " + projectName + " " + badge(project.state || project.stato) + "</div>" +
    "<table class=\"hours\"><thead><tr>" + header + "</tr></thead><tbody>" + rows + "</tbody></table>" +
    "<div class=\"row-actions\"><button class=\"btn-primary\" id=\"conclude-btn\"" + (data.canConclude ? "" : " disabled") + ">Concludi progetto</button></div>" +
    concludeWarning;

  var btn = document.getElementById("conclude-btn");
  if (btn) {
    btn.addEventListener("click", function () {
      concludeProject(project.id);
    });
  }
}

function concludeProject(projectId) {
  fetch(API + "api/conclude-project", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ projectId: projectId })
  }).then(function (res) {
    return res.json().catch(function () { return {}; }).then(function (data) {
      if (!res.ok) {
        throw new Error(data.error || data.message || ("Errore " + res.status));
      }
      return data;
    });
  }).then(function (data) {
    var msg = data.message || "Progetto concluso.";
    try { msg = decodeURIComponent(msg); } catch (e) { /* ignora, msg resta com'è */ }
    hmShowSuccess(msg);
    loadMonitorProjects(projectId);
  }).catch(function (e) {
    hmShowError(e.message);
  });
}

/* =====================================================================
   MONITOR COLLABORATORI — /api/monitor-collaborators
   ===================================================================== */

var collabState = { collaboratorId: null };

function loadMonitorCollaborators(collaboratorId) {
  var url = "api/monitor-collaborators";
  if (collaboratorId) { url += "?collaborator_id=" + collaboratorId; }

  getJSON(url).then(function (data) {
    collabState.collaboratorId = data.selected ? data.selected.id : null;

    var select = document.getElementById("collab-select");
    fillSelect(select, data.collaboratori, "seleziona un collaboratore", collabState.collaboratorId, function (c) {
      return c.name || c.username || ("Utente #" + c.id);
    });
    select.onchange = function () {
      var val = parseInt(select.value, 10);
      loadMonitorCollaborators(isNaN(val) ? null : val);
    };

    renderCollabDetail(data);
  }).catch(function (e) {
    hmShowError(e.message);
  });
}

function renderCollabDetail(data) {
  var box = document.getElementById("collab-detail");
  var collaborator = data.selected;
  if (!collaborator) {
    box.innerHTML = "<p class=\"empty\">Nessun collaboratore selezionato.</p>";
    return;
  }

  var projects = data.progetti || [];
  var collabName = collaborator.name || collaborator.username || "";

  if (projects.length === 0) {
    box.innerHTML =
      "<div class=\"breadcrumb\">Collaboratore <b>#" + collaborator.id + "</b> - " + collabName + "</div>" +
      "<p class=\"empty\">Nessun progetto in comune.</p>";
    return;
  }

  var html = "<div class=\"breadcrumb\">Collaboratore <b>#" + collaborator.id + "</b> - " + collabName + "</div>";

  for (var p = 0; p < projects.length; p++) {
    var project = projects[p];
    var months = [];
    for (var m = 1; m <= project.durationMonths; m++) { months.push(m); }

    var header = "<th>WP / Task</th>";
    for (var hm = 0; hm < months.length; hm++) {
      header += "<th>M" + months[hm] + "</th>";
    }

    var rows = "";
    var wps = project.workPackages || [];
    for (var w = 0; w < wps.length; w++) {
      var wp = wps[w];
      var wpName = wp.title || wp.titolo || ("WP#" + wp.id);
      rows += "<tr class=\"wp-row\"><td>" + wpName + "</td>";
      var wpHours = wp.monthHours || [];
      for (var i = 0; i < wpHours.length; i++) {
        var mh = wpHours[i];
        rows += (!mh || !mh.active) ? "<td class=\"inactive\">-</td>" : ("<td>" + mh.workedHours + "h</td>");
      }
      rows += "</tr>";

      var tasks = wp.tasks || [];
      for (var t = 0; t < tasks.length; t++) {
        var task = tasks[t];
        var taskName = task.title || task.titolo || ("Task#" + task.id);
        rows += "<tr><td style=\"padding-left:18px;color:#777;\">" + taskName + "</td>";
        var taskHours = task.monthHours || [];
        for (var j = 0; j < taskHours.length; j++) {
          var tmh = taskHours[j];
          rows += (!tmh || !tmh.active) ? "<td class=\"inactive\">-</td>" : ("<td>" + tmh.workedHours + "h</td>");
        }
        rows += "</tr>";
      }
    }

    var projectName = project.title || project.titolo || ("Progetto #" + project.id);
    html += "<h3>" + projectName + "</h3>" +
            "<table class=\"hours\"><thead><tr>" + header + "</tr></thead><tbody>" + rows + "</tbody></table>";
  }

  box.innerHTML = html;
}
function initLogout() {
  var btn = document.getElementById("btn-logout");
  if (!btn) return;

  btn.addEventListener("click", function () {
    fetch(API + "api/logout", { method: "POST" })
      .finally(function () {
        window.location.href = "index.html";
      });
  });
}


function initTabs() {
  var buttons = document.querySelectorAll(".tab-btn");
  for (var i = 0; i < buttons.length; i++) {
    buttons[i].addEventListener("click", function () {
      var btn = this;
      var allButtons = document.querySelectorAll(".tab-btn");
      for (var a = 0; a < allButtons.length; a++) { allButtons[a].classList.remove("active"); }
      var allViews = document.querySelectorAll(".view");
      for (var v = 0; v < allViews.length; v++) { allViews[v].classList.remove("active"); }

      btn.classList.add("active");
      document.getElementById("view-" + btn.dataset.view).classList.add("active");

      if (btn.dataset.view === "home") { loadHome(); }
      if (btn.dataset.view === "monitor") { loadMonitorProjects(); }
      if (btn.dataset.view === "collab") { loadMonitorCollaborators(); }
    });
  }
}

window.loadHomeManager = function () {
  initTabs();
  loadHome();
  initLogout();
};
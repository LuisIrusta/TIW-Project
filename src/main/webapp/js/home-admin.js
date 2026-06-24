var API = "";
let undoStack = [];
let redoStack = [];

/* ── utils ──────────────────────────────────────────────────────────── */

function haShowError(msg) {
  var el = document.getElementById("ha-error");
  el.textContent = msg;
  el.style.display = "block";
  setTimeout(function () { el.style.display = "none"; }, 5000);
}

function haShowSuccess(msg) {
  var el  = document.getElementById("ha-success");
  var err = document.getElementById("ha-error");
  err.style.display = "none";
  el.textContent = msg;
  el.style.display = "block";
  setTimeout(function () { el.style.display = "none"; }, 4000);
}

function setFullname(user) {
  var span = document.getElementById("ha-fullname");
  if (span) span.textContent = user
    ? (user.firstName || user.name || user.username || user.id || "Admin")
    : "-";
}

function fillSelect(selectEl, items, placeholder, labelFn) {
  selectEl.innerHTML = "<option value=\"\">-- " + placeholder + " --</option>";
  items = items || [];
  for (var i = 0; i < items.length; i++) {
    var opt = document.createElement("option");
    opt.value = items[i].id;
    opt.textContent = labelFn(items[i]);
    selectEl.appendChild(opt);
  }
}

function getJSON(url) {
  return fetch(API + url, { credentials: "same-origin" }).then(function (res) {
    return res.json().catch(function () { return {}; }).then(function (data) {
      if (!res.ok) {
        var err = new Error(data.error || data.message || ("Errore " + res.status));
        err.payload = data; err.status = res.status;
        throw err;
      }
      return data;
    });
  });
}

function postJSON(url, body) {
  return fetch(API + url, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  }).then(function (res) {
    return res.json().catch(function () { return {}; }).then(function (data) {
      if (!res.ok) {
        var err = new Error(data.error || data.message || ("Errore " + res.status));
        err.payload = data;
        throw err;
      }
      return data;
    });
  });
}

function postForm(url, params) {
  var body = Object.keys(params).map(function (k) {
    return encodeURIComponent(k) + "=" + encodeURIComponent(params[k]);
  }).join("&");
  return fetch(API + url, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body
  }).then(function (res) {
    return res.json().catch(function () { return {}; }).then(function (data) {
      if (!res.ok) {
        var err = new Error(data.error || data.message || ("Errore " + res.status));
        err.payload = data;
        throw err;
      }
      return data;
    });
  });
}

/* ── fetch dati home ────────────────────────────────────────────────── */

function fetchHomeAdminData() {
  getJSON("api/home-admin").then(function (data) {
    setFullname(data.admin);
    renderProjects(data);
    fillSelect(
      document.getElementById("draft-manager"),
      data.technicals,
      "seleziona un responsabile",
      function (t) { return (t.firstName || t.name || t.username || ("Utente #" + t.id)); }
    );
  }).catch(function (e) { haShowError(e.message); });
}

/* ── render gestione con drag & drop ───────────────────────────────── */

var drag = { taskId: null, taskEl: null, sourceWpId: null, projectId: null };

function renderProjects(data) {
  var container = document.getElementById("projects-container");
  var projects  = data.createdProjects || [];

  if (projects.length === 0) {
    container.innerHTML = "<p class=\"empty\">Nessun progetto in stato CREATED.</p>";
    return;
  }

  container.innerHTML = "";

  for (var p = 0; p < projects.length; p++) {
    var project = projects[p];
    var wps     = project.workPackages || [];

    var card = document.createElement("div");
    card.className = "project-card";
    card.innerHTML =
      "<h4>" + (project.title || "Progetto #" + project.id) + "</h4>" +
      "<div class=\"meta\">ID: " + project.id + " | Durata: " + project.durationMonths + " mesi</div>";

    if (wps.length === 0) {
      var empty = document.createElement("p");
      empty.style.cssText = "color:#999;font-size:13px;";
      empty.textContent = "Nessun work package.";
      card.appendChild(empty);
    } else {
      var wpList = document.createElement("div");
      wpList.className = "wp-list";
      for (var w = 0; w < wps.length; w++) {
        wpList.appendChild(buildWpBlock(wps[w], project.id));
      }
      card.appendChild(wpList);
    }

    container.appendChild(card);
  }
}

function buildWpBlock(wp, projectId) {
  var block = document.createElement("div");
  block.className         = "manage-wp-block";
  block.dataset.wpId      = wp.id;
  block.dataset.wpStart   = wp.startMonth;
  block.dataset.wpEnd     = wp.endMonth;
  block.dataset.projectId = projectId;

  block.innerHTML =
    "<h5>" + (wp.title || "WP #" + wp.id) + "</h5>" +
    "<div class=\"wp-meta\">Mesi " + wp.startMonth + " - " + wp.endMonth + "</div>";

  var zone = document.createElement("div");
  zone.className = "task-drop-zone";

  var tasks = wp.tasks || [];
  for (var t = 0; t < tasks.length; t++) {
    zone.appendChild(buildTaskItem(tasks[t], wp.id));
  }

  attachDropZone(zone, wp, projectId);
  block.appendChild(zone);
  return block;
}

function buildTaskItem(task, wpId) {
  var item = document.createElement("div");
  item.className      = "manage-task-item";
  item.draggable      = true;
  item.dataset.taskId = task.id;
  item.dataset.wpId   = wpId;
  item.dataset.start  = task.startMonth;
  item.dataset.end    = task.endMonth;

  item.innerHTML =
    "<span class=\"task-order\">#" + task.orderNumber + "</span>" +
    "<span class=\"task-name\">"   + (task.title || "Task #" + task.id) + "</span>" +
    "<span class=\"task-months\">mesi " + task.startMonth + "-" + task.endMonth + "</span>";
  item.addEventListener("dragstart", onDragStart);
  item.addEventListener("dragend",   onDragEnd);
  return item;
}

/* ── drag handlers ──────────────────────────────────────────────────── */
document.addEventListener("DOMContentLoaded", function () {
    const undoDD = document.getElementById("btn-undo-dd");
    const redoDD = document.getElementById("btn-redo-dd");

    if (undoDD) undoDD.addEventListener("click", undo);
    if (redoDD) redoDD.addEventListener("click", redo);
});
function onDragStart(e) {
  drag.taskId     = parseInt(this.dataset.taskId, 10);
  drag.taskEl     = this;
  drag.sourceWpId = parseInt(this.dataset.wpId,   10);
  drag.projectId  = parseInt(this.closest(".manage-wp-block").dataset.projectId, 10);
  this.classList.add("dragging");
  e.dataTransfer.effectAllowed = "move";
}

function onDragEnd() {
  this.classList.remove("dragging");
  document.querySelectorAll(".drop-placeholder").forEach(function (el) { el.remove(); });
  drag.taskId = null; drag.taskEl = null; drag.sourceWpId = null; drag.projectId = null;
}

function renumberZone(zone) {
  var items = zone.querySelectorAll(".manage-task-item");
  for (var i = 0; i < items.length; i++) {
    var orderSpan = items[i].querySelector(".task-order");
    if (orderSpan) orderSpan.textContent = "#" + (i + 1);
  }
}

function attachDropZone(zone, wp, projectId) {
  zone.addEventListener("dragover", function (e) {
    if (drag.taskId === null) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = "move";
    zone.classList.add("drag-over");

    var placeholder = zone.querySelector(".drop-placeholder");
    if (!placeholder) {
      placeholder = document.createElement("div");
      placeholder.className = "drop-placeholder";
    }
    var afterEl = getDragAfterElement(zone, e.clientY);
    if (afterEl == null) zone.appendChild(placeholder);
    else zone.insertBefore(placeholder, afterEl);
  });

  zone.addEventListener("dragleave", function (e) {
    if (!zone.contains(e.relatedTarget)) {
      zone.classList.remove("drag-over");
      var ph = zone.querySelector(".drop-placeholder");
      if (ph) ph.remove();
    }
  });

  zone.addEventListener("drop", function (e) {
    e.preventDefault();
    zone.classList.remove("drag-over");

    var ph = zone.querySelector(".drop-placeholder");
    if (ph) ph.remove();

    // blocca se WP di progetto diverso
    if (drag.projectId !== projectId) {
      haShowError("Non puoi spostare un task tra progetti diversi.");
      return;
    }

    // blocca se il WP sorgente rimarrebbe senza task
    if (drag.sourceWpId !== wp.id) {
      var sourceZone = null;
      document.querySelectorAll(".task-drop-zone").forEach(function (z) {
        var block = z.closest(".manage-wp-block");
        if (block && parseInt(block.dataset.wpId, 10) === drag.sourceWpId) {
          sourceZone = z;
        }
      });
      if (sourceZone && sourceZone.querySelectorAll(".manage-task-item").length <= 1) {
        haShowError("Non puoi spostare l'unico task di un Work Package.");
        return;
      }
    }

    var afterEl        = getDragAfterElement(zone, e.clientY);
    var items          = Array.from(zone.querySelectorAll(".manage-task-item"));
    var targetPosition = afterEl == null ? items.length + 1 : items.indexOf(afterEl) + 1;
    if (targetPosition < 1) targetPosition = 1;

    var taskEl     = drag.taskEl;
    var taskId     = drag.taskId;
    var sourceWpId = drag.sourceWpId;
    var targetWpId = wp.id;

    postForm("api/move-task", {
      taskId:         taskId,
      targetWpId:     targetWpId,
      targetPosition: targetPosition
    }).then(function () {
      haShowSuccess("Task spostato con successo.");

      // aggiorna il dataset wpId del task
      taskEl.dataset.wpId = targetWpId;

	  const sourceZone = document.querySelector(`.manage-wp-block[data-wp-id="${sourceWpId}"] .task-drop-zone`);

	  // --- Calcolo l'indice ORIGINALE PRIMA dello spostamento ---
	  const sourceIndex = [...sourceZone.querySelectorAll(".manage-task-item")].indexOf(taskEl);
	  // --- REGISTRAZIONE UNDO/REDO DEL DRAG & DROP ---
	  const targetZone = zone;
	  const targetIndex = afterEl == null
	      ? targetZone.querySelectorAll(".manage-task-item").length
	      : [...targetZone.querySelectorAll(".manage-task-item")].indexOf(afterEl);

	  const taskHTML = taskEl.outerHTML;

	  // --- ESECUZIONE DELLO SPOSTAMENTO ---
	  if (afterEl == null) targetZone.appendChild(taskEl);
	  else targetZone.insertBefore(taskEl, afterEl);

	  renumberZone(targetZone);
	  if (sourceWpId !== targetWpId) renumberZone(sourceZone);

	  // --- UNDO/REDO ---
	  undoStack.push({
	      type: "moveTask",
	      taskHTML,
	      sourceWpId,
	      sourceIndex,
	      targetWpId,
	      targetIndex,

		  undo: () => {
		      // aggiorna backend
		      postForm("api/move-task", {
		          taskId: taskId,
		          targetWpId: sourceWpId,
		          targetPosition: sourceIndex + 1
		      });

		      // rimuovi dalla destinazione
		      const current = targetZone.querySelectorAll(".manage-task-item")[targetIndex];
		      if (current) current.remove();

		      // ricrea nella posizione originale
		      const temp = document.createElement("div");
		      temp.innerHTML = taskHTML.trim();
		      const restored = temp.firstElementChild;

		      const list = sourceZone;
		      if (sourceIndex >= list.children.length) list.appendChild(restored);
		      else list.insertBefore(restored, list.children[sourceIndex]);

		      renumberZone(sourceZone);
		  },

		  redo: () => {
		      // aggiorna backend
		      postForm("api/move-task", {
		          taskId: taskId,
		          targetWpId: targetWpId,
		          targetPosition: targetIndex + 1
		      });

		      // rimuovi dalla posizione originale
		      const current = sourceZone.querySelectorAll(".manage-task-item")[sourceIndex];
		      if (current) current.remove();

		      // ricrea nella destinazione
		      const temp = document.createElement("div");
		      temp.innerHTML = taskHTML.trim();
		      const restored = temp.firstElementChild;

		      if (targetIndex >= targetZone.children.length) targetZone.appendChild(restored);
		      else targetZone.insertBefore(restored, targetZone.children[targetIndex]);

		      renumberZone(targetZone);
		  }

	  });

	  redoStack = [];

    }).catch(function (err) {
      haShowError(err.message);
    });
  });
}

function getDragAfterElement(zone, y) {
  var items = Array.from(zone.querySelectorAll(".manage-task-item:not(.dragging)"));
  var closest = null;
  var closestOffset = Number.NEGATIVE_INFINITY;
  items.forEach(function (child) {
    var box    = child.getBoundingClientRect();
    var offset = y - box.top - box.height / 2;
    if (offset < 0 && offset > closestOffset) {
      closestOffset = offset;
      closest = child;
    }
  });
  return closest;
}

/* ── draft ──────────────────────────────────────────────────────────── */
function undo() {
  const action = undoStack.pop();
  if (!action) return;
  action.undo();
  redoStack.push(action);
}

function redo() {
  const action = redoStack.pop();
  if (!action) return;
  action.redo();
  undoStack.push(action);
}
// UNDO/REDO: titolo progetto
const titleInput = document.getElementById("draft-title");
titleInput.addEventListener("input", function (e) {
  const oldValue = this.dataset.oldValue || "";
  const newValue = e.target.value;

  undoStack.push({
    type: "editTitle",
    oldValue,
    newValue,
    undo: () => { this.value = oldValue; },
    redo: () => { this.value = newValue; }
  });

  this.dataset.oldValue = newValue;
  redoStack = [];
});

// UNDO/REDO: durata progetto
const durationInput = document.getElementById("draft-duration");
durationInput.addEventListener("input", function (e) {
  const oldValue = this.dataset.oldValue || this.defaultValue || "6";
  const newValue = e.target.value;

  undoStack.push({
    type: "editDuration",
    oldValue,
    newValue,
    undo: () => { this.value = oldValue; },
    redo: () => { this.value = newValue; }
  });

  this.dataset.oldValue = newValue;
  redoStack = [];
});

// UNDO/REDO: responsabile progetto
const managerSelect = document.getElementById("draft-manager");
managerSelect.addEventListener("change", function (e) {
  const oldValue = this.dataset.oldValue || "";
  const newValue = e.target.value;

  undoStack.push({
    type: "editManager",
    oldValue,
    newValue,
    undo: () => { this.value = oldValue; },
    redo: () => { this.value = newValue; }
  });

  this.dataset.oldValue = newValue;
  redoStack = [];
});

function recreateTask(wpDiv, taskHTML) {
  const temp = document.createElement("div");
  temp.innerHTML = taskHTML.trim();
  const row = temp.firstElementChild;

  const parent = wpDiv.querySelector(".task-list");
  parent.appendChild(row);

  // --- UNDO/REDO: mesi Task (ricreati anche nel redo) ---
  const taskStart = row.querySelector(".task-start");
  const taskEnd   = row.querySelector(".task-end");

  taskStart.addEventListener("input", function (e) {
    const oldValue = this.dataset.oldValue || this.defaultValue || "1";
    const newValue = e.target.value;

    undoStack.push({
      type: "editTaskStart",
      wpDiv: wpDiv,
      row: row,
      oldValue,
      newValue,
      undo: () => { this.value = oldValue; },
      redo: () => { this.value = newValue; }
    });

    this.dataset.oldValue = newValue;
    redoStack = [];
  });

  taskEnd.addEventListener("input", function (e) {
    const oldValue = this.dataset.oldValue || this.defaultValue || "1";
    const newValue = e.target.value;

    undoStack.push({
      type: "editTaskEnd",
      wpDiv: wpDiv,
      row: row,
      oldValue,
      newValue,
      undo: () => { this.value = oldValue; },
      redo: () => { this.value = newValue; }
    });

    this.dataset.oldValue = newValue;
    redoStack = [];
  });

  // --- Listener rimozione task ---
  row.querySelector(".btn-remove").addEventListener("click", function () {
    const removedHTML = row.outerHTML;
    const removedIndex = [...parent.children].indexOf(row);

    undoStack.push({
      type: "removeTask",
      wpDiv: wpDiv,
      index: removedIndex,
      taskHTML: removedHTML,

      undo: () => recreateTask(wpDiv, removedHTML),
      redo: () => parent.removeChild(parent.children[removedIndex])
    });

    redoStack = [];
    row.remove();
  });
}

function recreateWp(container, wpHTML) {
  const temp = document.createElement("div");
  temp.innerHTML = wpHTML.trim();
  const block = temp.firstElementChild;

  container.appendChild(block);

  // Listener rimozione WP
  block.querySelector(".btn-remove").addEventListener("click", function () {
    const removedHTML = block.outerHTML;
    const removedIndex = [...container.children].indexOf(block);

    undoStack.push({
      type: "removeWP",
      index: removedIndex,
      wpHTML: removedHTML,

      undo: () => recreateWp(container, removedHTML),
      redo: () => container.removeChild(container.children[removedIndex])
    });

    redoStack = [];
    block.remove();
  });

  // Listener aggiunta task
  block.querySelector(".btn-add-task").addEventListener("click", function () {
    var s = parseInt(block.querySelector(".wp-start").value, 10) || 1;
    var e = parseInt(block.querySelector(".wp-end").value, 10) || 1;
    createTaskRow(block, s, e);
  });
}


function getProjectDuration() {
  return parseInt(document.getElementById("draft-duration").value, 10) || 1;
}
function createTaskRow(wpDiv, defaultStart, defaultEnd) {
  var row = document.createElement("div");
  row.className = "draft-task";
  row.innerHTML =
    "<input type=\"text\" class=\"task-title\" placeholder=\"Titolo del task\" value=\"Titolo del task\">" +
    "<input type=\"text\" class=\"task-desc\"  placeholder=\"Descrizione (opzionale)\" value=\"\">" +
    "<span style=\"font-size:12px;color:#777;\">Mesi:</span>" +
    "<input type=\"number\" class=\"task-start\" min=\"1\" value=\"" + defaultStart + "\">" +
    "<span style=\"font-size:12px;color:#777;\">-</span>" +
    "<input type=\"number\" class=\"task-end\"   min=\"1\" value=\"" + defaultEnd + "\">" +
    "<button class=\"btn-remove\" type=\"button\" title=\"Rimuovi task\">&minus;</button>";
	
	// UNDO/REDO: mesi Task
	const taskStart = row.querySelector(".task-start");
	const taskEnd   = row.querySelector(".task-end");

	taskStart.addEventListener("input", function (e) {
	  const oldValue = this.dataset.oldValue || defaultStart;
	  const newValue = e.target.value;

	  undoStack.push({
	    type: "editTaskStart",
	    wpDiv: wpDiv,
	    row: row,
	    oldValue,
	    newValue,
	    undo: () => { this.value = oldValue; },
	    redo: () => { this.value = newValue; }
	  });

	  this.dataset.oldValue = newValue;
	  redoStack = [];
	});

	taskEnd.addEventListener("input", function (e) {
	  const oldValue = this.dataset.oldValue || defaultEnd;
	  const newValue = e.target.value;

	  undoStack.push({
	    type: "editTaskEnd",
	    wpDiv: wpDiv,
	    row: row,
	    oldValue,
	    newValue,
	    undo: () => { this.value = oldValue; },
	    redo: () => { this.value = newValue; }
	  });

	  this.dataset.oldValue = newValue;
	  redoStack = [];
	});


  // Aggiungo il task al DOM
  const parent = wpDiv.querySelector(".task-list");
  parent.appendChild(row);

  // Salvo una copia del task per undo/redo
  const index = [...parent.children].indexOf(row);
  const taskHTML = row.outerHTML;

  // Registro l’azione nello stack undo
  undoStack.push({
    type: "addTask",
    wpDiv: wpDiv,
    index: index,
    taskHTML: taskHTML,
    undo: () => {
      parent.removeChild(parent.children[index]);
    },
    redo: () => {
		recreateTask(wpDiv, taskHTML);
    }
  });

  // Ogni nuova azione invalida il redo
  redoStack = [];

  // Listener per rimozione task (con undo)
  row.querySelector(".btn-remove").addEventListener("click", function () {

    const removedHTML = row.outerHTML;
    const removedIndex = [...parent.children].indexOf(row);

    undoStack.push({
      type: "removeTask",
      wpDiv: wpDiv,
      index: removedIndex,
      taskHTML: removedHTML,

      undo: () => {
        recreateTask(wpDiv, removedHTML);
      },

      redo: () => {
        parent.removeChild(parent.children[removedIndex]);
      }
    });

    redoStack = [];
    row.remove();
  });
}
function createWpBlock() {
  var dur   = getProjectDuration();
  var block = document.createElement("div");
  block.className = "draft-wp";
  block.innerHTML =
    "<div class=\"draft-wp-header\">" +
      "<input type=\"text\" class=\"wp-title\" value=\"Titolo del WP\" placeholder=\"Titolo del WP\">" +
      "<button class=\"btn-remove\" type=\"button\" title=\"Rimuovi WP\">&minus;</button>" +
    "</div>" +
    "<div class=\"draft-months\">" +
      "<span>Mesi:</span>" +
      "<input type=\"number\" class=\"wp-start\" min=\"1\" value=\"1\">" +
      "<span>-</span>" +
      "<input type=\"number\" class=\"wp-end\" min=\"1\" value=\"" + dur + "\">" +
    "</div>" +
    "<div class=\"task-list\"></div>" +
    "<button class=\"btn-add btn-add-task\" type=\"button\">+ Aggiungi Task</button>";
	// UNDO/REDO: mesi WP
	
	
	const wpStart = block.querySelector(".wp-start");
	const wpEnd   = block.querySelector(".wp-end");

	wpStart.addEventListener("input", function (e) {
	  const oldValue = this.dataset.oldValue || this.defaultValue || "1";
	  const newValue = e.target.value;

	  undoStack.push({
	    type: "editWpStart",
	    wpDiv: block,
	    oldValue,
	    newValue,
	    undo: () => { this.value = oldValue; },
	    redo: () => { this.value = newValue; }
	  });

	  this.dataset.oldValue = newValue;
	  redoStack = [];
	});

	wpEnd.addEventListener("input", function (e) {
	  const oldValue = this.dataset.oldValue || this.defaultValue || "1";
	  const newValue = e.target.value;
	  undoStack.push({
	    type: "editWpEnd",
	    wpDiv: block,
	    oldValue,
	    newValue,
	    undo: () => { this.value = oldValue; },
	    redo: () => { this.value = newValue; }
	  });
	  this.dataset.oldValue = newValue;
	  redoStack = [];
	});
	// fine undu redo
  const container = document.getElementById("draft-wps-container");

  // Aggiungo il WP al DOM
  container.appendChild(block);

  // Salvo posizione e HTML per undo/redo
  const index = [...container.children].indexOf(block);
  const wpHTML = block.outerHTML;

  // Registro l'azione "aggiungi WP"
  undoStack.push({
    type: "addWP",
    index: index,
    wpHTML: wpHTML,

    undo: () => {
      container.removeChild(container.children[index]);
    },

    redo: () => {
      recreateWp(container, wpHTML);
    }
  });

  redoStack = [];

  // Listener per rimozione WP (con undo)
  block.querySelector(".btn-remove").addEventListener("click", function () {

    const removedHTML = block.outerHTML;
    const removedIndex = [...container.children].indexOf(block);

    undoStack.push({
      type: "removeWP",
      index: removedIndex,
      wpHTML: removedHTML,

      undo: () => {
       recreateWp(container, removedHTML);
      },

      redo: () => {
        container.removeChild(container.children[removedIndex]);
      }
    });

    redoStack = [];
    block.remove();
  });

  // Listener per aggiunta task
  block.querySelector(".btn-add-task").addEventListener("click", function () {
    var s = parseInt(block.querySelector(".wp-start").value, 10) || 1;
    var e = parseInt(block.querySelector(".wp-end").value,   10) || dur;
    createTaskRow(block, s, e);
  });
}


function readDraftFromDOM() {
  var title     = document.getElementById("draft-title").value.trim();
  var duration  = document.getElementById("draft-duration").value.trim();
  var managerId = document.getElementById("draft-manager").value.trim();
  var wpBlocks  = document.querySelectorAll("#draft-wps-container .draft-wp");
  var workPackages = [];

  for (var i = 0; i < wpBlocks.length; i++) {
    var block    = wpBlocks[i];
    var taskRows = block.querySelectorAll(".draft-task");
    var tasks    = [];
    for (var j = 0; j < taskRows.length; j++) {
      var row = taskRows[j];
      tasks.push({
        title:       row.querySelector(".task-title").value.trim(),
        description: row.querySelector(".task-desc").value.trim(),
        startMonth:  row.querySelector(".task-start").value.trim(),
        endMonth:    row.querySelector(".task-end").value.trim()
      });
    }
    workPackages.push({
      title:      block.querySelector(".wp-title").value.trim(),
      startMonth: block.querySelector(".wp-start").value.trim(),
      endMonth:   block.querySelector(".wp-end").value.trim(),
      tasks:      tasks
    });
  }
  return { title: title, durationMonth: duration, managerId: managerId, workPackages: workPackages };
}

function initDraft() {
	document.getElementById("btn-undo").addEventListener("click", undo);
	document.getElementById("btn-redo").addEventListener("click", redo);
  document.getElementById("btn-add-wp").addEventListener("click", createWpBlock);
  document.getElementById("btn-save-draft").addEventListener("click", function () {
    var payload = readDraftFromDOM();
    postJSON("api/save-project", payload).then(function (data) {
      haShowSuccess(data.message || "Progetto salvato con successo.");
      document.getElementById("draft-title").value    = "";
      document.getElementById("draft-duration").value = "6";
      document.getElementById("draft-manager").value  = "";
      document.getElementById("draft-wps-container").innerHTML = "";
      fetchHomeAdminData();
    }).catch(function (err) {
      haShowError(err.message);
    });
  });
}

/* ── verifica progetti ──────────────────────────────────────────────── */

function loadVerificaProgetti(projectId) {
  var url = "api/verifica-progetti";
  if (projectId) url += "?projectId=" + projectId;
  getJSON(url).then(function (data) {
    var selectedId = data.selected ? data.selected.id : null;
    var select = document.getElementById("verify-project");
    fillSelect(select, data.progetti, "seleziona un progetto", function (p) {
      return (p.title || "Progetto #" + p.id) + " - " + (p.state || p.stato || "");
    });
    if (selectedId) select.value = selectedId;
    select.onchange = function () {
      var val = parseInt(select.value, 10);
      loadVerificaProgetti(isNaN(val) ? null : val);
    };
    renderVerificaDetail(data);
  }).catch(function (e) { haShowError(e.message); });
}

function renderVerificaDetail(data) {
  var box     = document.getElementById("verify-detail");
  var project = data.selected;
  if (!project) {
    box.innerHTML = "<p class=\"empty\">Nessun progetto selezionato.</p>";
    return;
  }
  var wps    = project.workPackages || [];
  var wpHtml = wps.length === 0
    ? "<p style=\"color:#999;font-size:13px;\">Nessun work package.</p>"
    : "<div class=\"wp-list\">" + wps.map(function (wp) {
        var tasks    = wp.tasks || [];
        var taskHtml = tasks.length === 0
          ? "<div class=\"task-item\">Nessun task.</div>"
          : "<div class=\"task-list\">" + tasks.map(function (t) {
              return "<div class=\"task-item\">#" + t.orderNumber + " " +
                     (t.title || "Task #" + t.id) +
                     " (mesi " + t.startMonth + "-" + t.endMonth + ")</div>";
            }).join("") + "</div>";
        return "<div class=\"wp-item\"><strong>" + (wp.title || "WP #" + wp.id) + "</strong>" +
               " (mesi " + wp.startMonth + "-" + wp.endMonth + ")" + taskHtml + "</div>";
      }).join("") + "</div>";

  box.innerHTML =
    "<div class=\"project-card\">" +
    "<h4>" + (project.title || "Progetto #" + project.id) + "</h4>" +
    "<div class=\"meta\">ID: " + project.id + " | Durata: " + project.durationMonths + " mesi</div>" +
    wpHtml + "</div>";
}

/* ── tabs ───────────────────────────────────────────────────────────── */

function initTabs() {
  var buttons = document.querySelectorAll(".tab-btn");
  for (var i = 0; i < buttons.length; i++) {
    buttons[i].addEventListener("click", function () {
      document.querySelectorAll(".tab-btn").forEach(function (b) { b.classList.remove("active"); });
      document.querySelectorAll(".view").forEach(function (v)    { v.classList.remove("active"); });
      this.classList.add("active");
      document.getElementById("view-" + this.dataset.view).classList.add("active");
      if (this.dataset.view === "verify") loadVerificaProgetti();
    });
  }
}

/* ── logout ─────────────────────────────────────────────────────────── */

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



window.loadHomeAdmin = function () {
  initLogout();
  initTabs();
  initDraft();
  fetchHomeAdminData();
};
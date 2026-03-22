const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

let statusPoller = null;

async function loadStats() {
    const data = await fetchJson('/api/stats');
    if (!data) return;
    document.getElementById('stat-boats').textContent = data.boats.toLocaleString();
    document.getElementById('stat-designs').textContent = data.designs.toLocaleString();
    document.getElementById('stat-races').textContent = data.races.toLocaleString();
}

async function loadImporters() {
    const data = await fetchJson('/api/importers');
    if (!data) return;

    const container = document.getElementById('importers');
    container.innerHTML = '';

    let anyRunning = false;
    for (const imp of data) {
        if (imp.status === 'running') anyRunning = true;
        container.appendChild(buildImporterCard(imp));
    }

    if (anyRunning) startStatusPoller();
}

function buildImporterCard(imp) {
    const sc = (imp.schedule && imp.schedule.name) ? imp.schedule : {
        enabled: false, day: 'FRIDAY', time: '03:00', mode: 'api'
    };
    const isRunning = imp.status === 'running';
    const timeVal = (sc.time || '03:00').substring(0, 5);

    const card = document.createElement('div');
    card.className = 'importer-card';
    card.id = 'card-' + imp.name;

    card.innerHTML = `
      <h3>
        ${esc(imp.name)}
        <span class="badge ${isRunning ? 'badge-running' : 'badge-idle'}" id="badge-${esc(imp.name)}">${esc(imp.status)}</span>
      </h3>
      <div class="run-row">
        <select id="run-mode-${esc(imp.name)}">
          <option value="api">API</option>
          <option value="directory">Directory</option>
        </select>
        <button onclick="runImporter('${esc(imp.name)}')">Run now</button>
      </div>
      <div class="schedule-form">
        <label>
          <input type="checkbox" id="sched-enabled-${esc(imp.name)}" ${sc.enabled ? 'checked' : ''}>
          Scheduled
        </label>
        <label>Day:
          <select id="sched-day-${esc(imp.name)}">
            ${DAYS.map(d => `<option value="${d}" ${(sc.day || 'FRIDAY') === d ? 'selected' : ''}>${d}</option>`).join('')}
          </select>
        </label>
        <label>Time: <input type="time" id="sched-time-${esc(imp.name)}" value="${esc(timeVal)}"></label>
        <label>Mode:
          <select id="sched-mode-${esc(imp.name)}">
            <option value="api" ${sc.mode === 'api' ? 'selected' : ''}>API</option>
            <option value="directory" ${sc.mode === 'directory' ? 'selected' : ''}>Directory</option>
          </select>
        </label>
        <button onclick="saveSchedule('${esc(imp.name)}')">Save schedule</button>
      </div>`;

    return card;
}

async function runImporter(name) {
    const mode = document.getElementById('run-mode-' + name).value;
    const resp = await fetch('/api/importers/' + name + '/run?mode=' + encodeURIComponent(mode), {
        method: 'POST'
    });
    const data = await resp.json().catch(() => ({}));
    if (resp.status === 409) {
        alert('An import is already running');
    } else if (resp.status === 202) {
        setBadge(name, 'running');
        startStatusPoller();
    } else {
        alert('Unexpected response: ' + resp.status);
    }
}

async function saveSchedule(name) {
    const enabled = document.getElementById('sched-enabled-' + name).checked;
    const day = document.getElementById('sched-day-' + name).value;
    const time = document.getElementById('sched-time-' + name).value;
    const mode = document.getElementById('sched-mode-' + name).value;

    const resp = await fetch('/api/importers/' + name + '/schedule', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, enabled, day, time, mode })
    });
    if (resp.ok) {
        alert('Schedule saved for ' + name);
    } else {
        const err = await resp.json().catch(() => ({ error: 'unknown error' }));
        alert('Failed: ' + (err.error || resp.status));
    }
}

function startStatusPoller() {
    if (statusPoller) return;
    statusPoller = setInterval(async () => {
        const data = await fetchJson('/api/importers/status');
        if (!data) return;
        if (!data.running) {
            clearInterval(statusPoller);
            statusPoller = null;
            loadImporters();
            loadStats();
        } else {
            setBadge(data.name, 'running');
        }
    }, 2000);
}

function setBadge(name, status) {
    const badge = document.getElementById('badge-' + name);
    if (!badge) return;
    badge.textContent = status;
    badge.className = 'badge ' + (status === 'running' ? 'badge-running' : 'badge-idle');
}

loadStats();
loadImporters();
setInterval(loadStats, 30000);

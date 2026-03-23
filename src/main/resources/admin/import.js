const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const DAY_LABELS = {
    MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu',
    FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun'
};
const MODE_LABEL = { directory: 'files', api: 'network', run: '—' };

const DISPLAY_NAMES = {
    'sailsys-boats': 'SailSys Boats',
    'sailsys-races': 'SailSys Races',
    'orc': 'ORC',
    'ams': 'AMS',
    'topyacht': 'TopYacht',
    'analysis': 'Analysis',
    'reference-factors': 'Reference Factors'
};

let currentEntries = [];
let statusPoller = null;

function displayName(name) {
    return DISPLAY_NAMES[name] || name;
}

async function loadImporters() {
    const data = await fetchJson('/api/importers');
    if (!data) return;

    currentEntries = data.entries || [];
    buildTable(currentEntries);
    buildDayPicker((data.schedule && data.schedule.days) ? data.schedule.days : []);
    if (data.schedule && data.schedule.time) {
        const t = data.schedule.time;
        document.getElementById('schedule-time').value =
            typeof t === 'string' ? t.substring(0, 5) : t;
    }
    const yearInput = document.getElementById('target-irc-year');
    yearInput.value = data.targetIrcYear != null ? data.targetIrcYear : '';

    const anyRunning = currentEntries.some(e => e.status === 'running');
    if (anyRunning) startStatusPoller();
}

function buildTable(entries) {
    const tbody = document.getElementById('importers-body');
    tbody.innerHTML = '';
    for (const entry of entries) {
        tbody.appendChild(buildRow(entry));
    }
}

function buildRow(entry) {
    const tr = document.createElement('tr');
    tr.id = 'row-' + entry.name + '-' + entry.mode;
    tr.dataset.name = entry.name;
    tr.dataset.mode = entry.mode;
    const isRunning = entry.status === 'running';
    const isSailSysApi = entry.mode === 'api' &&
        (entry.name === 'sailsys-boats' || entry.name === 'sailsys-races');
    const key = entry.name + '-' + entry.mode;
    const defaultStart = (entry.lastId != null) ? entry.lastId + 1 : 1;
    const startInput = isSailSysApi
        ? `<input type="number" id="start-${esc(key)}" value="${defaultStart}" min="1" style="width:5em">`
        : '';
    const runStopBtns = isSailSysApi
        ? `<button id="run-btn-${esc(key)}"
                   onclick="runImporter('${esc(entry.name)}','${esc(entry.mode)}')"
                   ${isRunning ? 'style="display:none"' : ''}>Run</button>
           <button id="stop-btn-${esc(key)}"
                   onclick="stopImport()"
                   ${isRunning ? '' : 'style="display:none"'}>Stop</button>`
        : `<button onclick="runImporter('${esc(entry.name)}','${esc(entry.mode)}')">Run</button>`;
    tr.innerHTML = `
      <td class="order-col">
        <button class="order-btn" title="Move up"
                onclick="moveRow('${esc(entry.name)}','${esc(entry.mode)}','up')">↑</button>
        <button class="order-btn" title="Move down"
                onclick="moveRow('${esc(entry.name)}','${esc(entry.mode)}','down')">↓</button>
      </td>
      <td>${esc(displayName(entry.name))}</td>
      <td>${esc(MODE_LABEL[entry.mode] || entry.mode)}</td>
      <td><span class="badge ${isRunning ? 'badge-running' : 'badge-idle'}"
               id="badge-${esc(key)}">${esc(entry.status)}</span></td>
      <td>${startInput}${runStopBtns}</td>
      <td><input type="checkbox" id="sched-${esc(key)}"
                 ${entry.includeInSchedule ? 'checked' : ''}></td>`;
    return tr;
}

function moveRow(name, mode, dir) {
    const tbody = document.getElementById('importers-body');
    const row = document.getElementById('row-' + name + '-' + mode);
    if (dir === 'up' && row.previousElementSibling) {
        tbody.insertBefore(row, row.previousElementSibling);
    } else if (dir === 'down' && row.nextElementSibling) {
        tbody.insertBefore(row.nextElementSibling, row);
    }
}

function buildDayPicker(scheduledDays) {
    const container = document.getElementById('schedule-days');
    container.innerHTML = DAYS.map(d =>
        `<label><input type="checkbox" value="${d}" ${scheduledDays.includes(d) ? 'checked' : ''}> ${DAY_LABELS[d]}</label>`
    ).join('');
}

async function runImporter(name, mode) {
    const key = name + '-' + mode;
    const startInput = document.getElementById('start-' + key);
    const startId = startInput ? parseInt(startInput.value, 10) || 1 : 1;
    const resp = await fetch(
        '/api/importers/' + name + '/run?mode=' + encodeURIComponent(mode) + '&startId=' + startId,
        { method: 'POST' }
    );
    const data = await resp.json().catch(() => ({}));
    if (resp.status === 409) {
        alert('An import is already running');
    } else if (resp.status === 202) {
        setBadge(name, mode, 'running');
        startStatusPoller();
    } else {
        alert('Unexpected response: ' + resp.status);
    }
}

async function stopImport() {
    await fetch('/api/importers/stop', { method: 'POST' });
}

async function saveSchedule() {
    const days = [...document.querySelectorAll('#schedule-days input:checked')].map(cb => cb.value);
    const time = document.getElementById('schedule-time').value;
    const importers = [...document.querySelectorAll('#importers-body tr')].map(tr => {
        const name = tr.dataset.name;
        const mode = tr.dataset.mode;
        return {
            name,
            mode,
            includeInSchedule: document.getElementById('sched-' + name + '-' + mode).checked
        };
    });
    const yearVal = parseInt(document.getElementById('target-irc-year').value, 10);
    const targetIrcYear = yearVal > 0 ? yearVal : null;
    const resp = await fetch('/api/schedule', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ days, time, importers, targetIrcYear })
    });
    if (!resp.ok) {
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
            await loadImporters();   // rebuilds table, applying any saved nextStartId values
        } else {
            setBadge(data.name, data.mode, 'running');
            if (data.currentId != null) {
                const key = data.name + '-' + data.mode;
                const startInput = document.getElementById('start-' + key);
                if (startInput) startInput.value = data.currentId + 1;
                const runBtn  = document.getElementById('run-btn-'  + key);
                const stopBtn = document.getElementById('stop-btn-' + key);
                if (runBtn)  runBtn.style.display  = 'none';
                if (stopBtn) stopBtn.style.display = '';
            }
        }
    }, 2000);
}

function setBadge(name, mode, status) {
    const badge = document.getElementById('badge-' + name + '-' + mode);
    if (!badge) return;
    badge.textContent = status;
    badge.className = 'badge ' + (status === 'running' ? 'badge-running' : 'badge-idle');
}

loadImporters();

const COLUMNS = {
    boats: [
        { label: 'ID',     key: 'id' },
        { label: 'Sail',   key: 'sailNumber' },
        { label: 'Name',   key: 'name' },
        { label: 'Design', key: 'designId' },
        { label: 'Club',   key: 'clubId' },
    ],
    designs: [
        { label: 'ID',     key: 'id' },
        { label: 'Name',   key: 'canonicalName' },
        { label: 'Makers', key: 'makerIds', render: v => esc((v || []).join(', ')) },
    ],
    races: [
        { label: 'ID',        key: 'id' },
        { label: 'Club',      key: 'clubId' },
        { label: 'Date',      key: 'date' },
        { label: 'Series',    key: 'seriesName' },
        { label: 'Race',      key: 'name' },
        { label: 'Finishers', key: 'finishers' },
    ],
};

const state = {
    pages:  { boats: 0, designs: 0, races: 0 },
    sort:   { boats: 'id', designs: 'id', races: 'date' },
    dir:    { boats: 'asc', designs: 'asc', races: 'desc' },
    searchTimers: {},
    activeTab: 'boats',
    selected:     { boats: new Set(), designs: new Set() },   // IDs of checked rows
    selectedData: { boats: new Map(), designs: new Map() },   // id → item for merge panel
};

function switchTab(entity) {
    ['boats', 'designs', 'races'].forEach(e => {
        document.getElementById('tab-btn-' + e).classList.toggle('active', e === entity);
        document.getElementById('panel-' + e).classList.toggle('active', e === entity);
    });
    state.activeTab = entity;
    if (document.querySelector('#tbody-' + entity + ' tr') === null) {
        loadList(entity, 0);
    }
}

function debounceSearch(entity) {
    clearTimeout(state.searchTimers[entity]);
    state.searchTimers[entity] = setTimeout(() => doSearch(entity), 300);
}

function doSearch(entity) {
    state.pages[entity] = 0;
    loadList(entity, 0);
}

async function loadList(entity, page) {
    state.pages[entity] = page;
    const q    = document.getElementById('q-' + entity).value;
    const sort = state.sort[entity];
    const dir  = state.dir[entity];
    let url = `/api/${entity}?page=${page}&size=50&q=${encodeURIComponent(q)}&sort=${sort}&dir=${dir}`;
    if (entity === 'boats') {
        if (document.getElementById('filter-dupe-sails').checked) url += '&dupeSails=true';
    }
    if (document.getElementById('show-excluded-' + entity).checked) url += '&showExcluded=true';
    const data = await fetchJson(url);
    if (!data) return;

    renderHeaders(entity);
    renderTable(entity, data.items);
    renderPager(entity, data);
}

function renderHeaders(entity) {
    const thead  = document.getElementById('thead-' + entity);
    const cols   = COLUMNS[entity];
    const active = state.sort[entity];
    const dir    = state.dir[entity];
    let html = '';
    if (entity === 'boats' || entity === 'designs') html += '<th style="width:2rem"></th>';
    html += cols.map(col => {
        const isActive = col.key === active;
        const arrow    = isActive ? (dir === 'asc' ? ' ↑' : ' ↓') : '';
        return `<th class="sortable${isActive ? ' sort-active' : ''}"
                    onclick="sortBy('${entity}', '${col.key}')">${esc(col.label)}${arrow}</th>`;
    }).join('');
    thead.innerHTML = html;
}

function sortBy(entity, key) {
    if (state.sort[entity] === key) {
        state.dir[entity] = state.dir[entity] === 'asc' ? 'desc' : 'asc';
    } else {
        state.sort[entity] = key;
        state.dir[entity]  = 'asc';
    }
    loadList(entity, 0);
}

function renderTable(entity, items) {
    const tbody = document.getElementById('tbody-' + entity);
    tbody.innerHTML = '';
    const cols = COLUMNS[entity];

    for (const item of items) {
        const tr = document.createElement('tr');
        if (entity === 'boats' || entity === 'designs') {
            if (state.selected[entity].has(item.id)) tr.classList.add('selected');
            // Checkbox cell — stop propagation so clicking the checkbox doesn't also open detail
            const tdCb = document.createElement('td');
            tdCb.style.textAlign = 'center';
            const cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.checked = state.selected[entity].has(item.id);
            cb.onclick = (e) => { e.stopPropagation(); toggleSelect(entity, item, cb.checked); };
            tdCb.appendChild(cb);
            tr.appendChild(tdCb);
        }
        tr.onclick = () => loadDetail(entity, item.id);
        cols.forEach(col => {
            const td = document.createElement('td');
            const v = item[col.key];
            td.innerHTML = col.render ? col.render(v) : esc(v != null ? String(v) : '');
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    }
}

function renderPager(entity, data) {
    const page = data.page;
    const totalPages = Math.ceil(data.total / data.size) || 1;
    let html = '';
    if (page > 0)
        html += `<button onclick="loadList('${entity}', ${page - 1})">← Prev</button>`;
    html += `<span>Page ${page + 1} of ${totalPages} (${data.total.toLocaleString()} total)</span>`;
    if ((page + 1) * data.size < data.total)
        html += `<button onclick="loadList('${entity}', ${page + 1})">Next →</button>`;

    document.getElementById('pager-' + entity).innerHTML = html;
    const topEl = document.getElementById('pager-top-' + entity);
    if (topEl) topEl.innerHTML = html;
}

async function loadDetail(entity, id) {
    const data = await fetchJson('/api/' + entity + '/' + encodeURIComponent(id));
    if (!data) return;

    const panel = document.getElementById('detail-' + entity);
    const pre   = document.getElementById('pre-' + entity);
    pre.textContent = JSON.stringify(data, null, 2);

    if (entity === 'boats') {
        const refDiv = document.getElementById('ref-factors-boats');
        refDiv.innerHTML = '<em>Loading reference factors…</em>';
        const ref = await fetchJson('/api/boats/' + encodeURIComponent(id) + '/reference');
        refDiv.innerHTML = ref ? renderReferenceFactors(ref) : '<em>No reference factors available</em>';
    }

    panel.classList.add('visible');
    panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function renderReferenceFactors(ref) {
    function row(label, f) {
        if (!f) return `<tr><td>${label}</td><td colspan="2" style="color:#999">—</td></tr>`;
        const barWidth = Math.round(f.weight * 80);
        return `<tr>
          <td>${label}</td>
          <td>${f.value.toFixed(4)}</td>
          <td>${f.weight.toFixed(3)} <span class="weight-bar" style="width:${barWidth}px"></span></td>
        </tr>`;
    }
    return `<strong>Reference factors (IRC equivalent, ${ref.currentYear})</strong>
      <table style="width:auto;margin-top:0.4rem;">
        <thead><tr><th>Variant</th><th>Value (TCF)</th><th>Weight</th></tr></thead>
        <tbody>
          ${row('Spin',       ref.spin)}
          ${row('Non-spin',   ref.nonSpin)}
          ${row('Two-handed', ref.twoHanded)}
        </tbody>
      </table>`;
}

// ---- Selection and merge (boats and designs) ----

function toggleSelect(entity, item, checked) {
    if (checked) {
        state.selected[entity].add(item.id);
        state.selectedData[entity].set(item.id, item);
    } else {
        state.selected[entity].delete(item.id);
        state.selectedData[entity].delete(item.id);
    }
    updateMergeBar(entity);
}

function updateMergeBar(entity) {
    const n   = state.selected[entity].size;
    const bar = document.getElementById('merge-bar-' + entity);
    bar.style.display = n >= 2 ? '' : 'none';
    const noun = entity === 'boats' ? 'boat' : 'design';
    document.getElementById('merge-bar-count-' + entity).textContent =
        n + ' ' + noun + (n !== 1 ? 's' : '') + ' selected';
}

function clearSelection(entity) {
    state.selected[entity].clear();
    state.selectedData[entity].clear();
    updateMergeBar(entity);
    hideMergePanel(entity);
    document.querySelectorAll('#tbody-' + entity + ' input[type=checkbox]').forEach(cb => cb.checked = false);
    document.querySelectorAll('#tbody-' + entity + ' tr.selected').forEach(tr => tr.classList.remove('selected'));
}

function showMergePanel(entity) {
    const panel = document.getElementById('merge-panel-' + entity);
    const list  = document.getElementById('merge-radio-list-' + entity);
    document.getElementById('merge-status-' + entity).textContent = '';
    list.innerHTML = '';
    const ids = Array.from(state.selected[entity]);
    ids.forEach((id, i) => {
        const item  = state.selectedData[entity].get(id);
        const label = document.createElement('label');
        label.style.display = 'block';
        label.style.margin  = '0.25rem 0';
        const radio = document.createElement('input');
        radio.type  = 'radio';
        radio.name  = 'merge-keep-' + entity;
        radio.value = id;
        if (i === 0) radio.checked = true;
        label.appendChild(radio);
        const desc = entity === 'boats'
            ? ' ' + esc(id) + '  —  sail: ' + esc(item.sailNumber || '') + '  name: ' + esc(item.name || '')
            : ' ' + esc(id) + '  —  ' + esc(item.canonicalName || '');
        label.appendChild(document.createTextNode(desc));
        list.appendChild(label);
    });
    panel.style.display = '';
}

function hideMergePanel(entity) {
    document.getElementById('merge-panel-' + entity).style.display = 'none';
}

async function performMerge(entity) {
    const keepRadio = document.querySelector('#merge-radio-list-' + entity + ' input[name="merge-keep-' + entity + '"]:checked');
    if (!keepRadio) return;
    const keepId   = keepRadio.value;
    const mergeIds = Array.from(state.selected[entity]).filter(id => id !== keepId);
    const statusEl = document.getElementById('merge-status-' + entity);
    statusEl.textContent = 'Merging…';

    const result = await fetchJson('/api/' + entity + '/merge', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ keepId, mergeIds })
    });

    if (!result) {
        statusEl.textContent = 'Merge failed — see console.';
        return;
    }
    if (entity === 'boats') {
        statusEl.textContent =
            'Merged. Updated ' + result.updatedRaces + ' race(s), ' + result.updatedFinishers + ' finisher record(s).';
    } else {
        statusEl.textContent = 'Merged. Updated ' + result.updatedBoats + ' boat(s).';
    }
    clearSelection(entity);
    hideMergePanel(entity);
    loadList(entity, 0);
}

loadList('boats', 0);

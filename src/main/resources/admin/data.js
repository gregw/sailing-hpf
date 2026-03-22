const state = {
    pages: { boats: 0, designs: 0, races: 0 },
    searchTimers: {},
    activeTab: 'boats'
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
    const q = document.getElementById('q-' + entity).value;
    const data = await fetchJson(`/api/${entity}?page=${page}&size=50&q=${encodeURIComponent(q)}`);
    if (!data) return;

    renderTable(entity, data.items);
    renderPager(entity, data);
}

function renderTable(entity, items) {
    const tbody = document.getElementById('tbody-' + entity);
    tbody.innerHTML = '';

    for (const item of items) {
        const tr = document.createElement('tr');
        tr.onclick = () => loadDetail(entity, item.id);
        if (entity === 'boats') {
            tr.innerHTML = `<td>${esc(item.id)}</td><td>${esc(item.sailNumber)}</td><td>${esc(item.name)}</td><td>${esc(item.designId)}</td>`;
        } else if (entity === 'designs') {
            tr.innerHTML = `<td>${esc(item.id)}</td><td>${esc(item.canonicalName)}</td><td>${esc((item.makerIds || []).join(', '))}</td>`;
        } else if (entity === 'races') {
            tr.innerHTML = `<td>${esc(item.id)}</td><td>${esc(item.clubId)}</td><td>${esc(item.date)}</td><td>${esc(item.handicapSystem)}</td>`;
        }
        tbody.appendChild(tr);
    }
}

function renderPager(entity, data) {
    const div = document.getElementById('pager-' + entity);
    div.innerHTML = '';
    const page = data.page;
    const totalPages = Math.ceil(data.total / data.size) || 1;

    if (page > 0) {
        const btn = document.createElement('button');
        btn.textContent = '← Prev';
        btn.onclick = () => loadList(entity, page - 1);
        div.appendChild(btn);
    }

    const info = document.createElement('span');
    info.textContent = `Page ${page + 1} of ${totalPages} (${data.total.toLocaleString()} total)`;
    div.appendChild(info);

    if ((page + 1) * data.size < data.total) {
        const btn = document.createElement('button');
        btn.textContent = 'Next →';
        btn.onclick = () => loadList(entity, page + 1);
        div.appendChild(btn);
    }
}

async function loadDetail(entity, id) {
    const data = await fetchJson('/api/' + entity + '/' + encodeURIComponent(id));
    if (!data) return;

    const panel = document.getElementById('detail-' + entity);
    const pre = document.getElementById('pre-' + entity);
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

loadList('boats', 0);

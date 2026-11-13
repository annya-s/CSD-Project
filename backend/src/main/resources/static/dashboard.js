import {
    $, api, requireFarmer, showPageError, node, icon, toast, confirmDialog, refreshPeriodically,
    formatDay, cropDisplayName, cropImage, readingTone, readingIcon, readingShortName, readingText, formatNumber
} from './api.js';

const SVG_NS = 'http://www.w3.org/2000/svg';
const filters = { q: '', farm: '', health: '', sort: 'newest' };
let cropTypes = [];
let dashboardData = { crops: [] };

/* ---------- Pieces ---------- */

// Temperature and sunlight within range, out of 2 (the only readings with backend reference ranges).
function rangeRing(crop) {
    const assessed = crop.readings.filter((r) => r.name === 'Temperature' || r.name === 'Sunlight');
    if (!assessed.length) return null;
    const inRange = assessed.filter((r) => r.status === 'WITHIN_RANGE').length;
    const ring = node('div', undefined, `ring${crop.needsAttention ? ' warn' : ''}`);
    ring.setAttribute('role', 'img');
    ring.setAttribute('aria-label', `${inRange} of ${assessed.length} readings within range`);
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('viewBox', '0 0 54 54');
    svg.setAttribute('aria-hidden', 'true');
    const circumference = 2 * Math.PI * 23;
    for (const cls of ['track', 'value']) {
        const circle = document.createElementNS(SVG_NS, 'circle');
        circle.setAttribute('class', cls);
        circle.setAttribute('cx', '27');
        circle.setAttribute('cy', '27');
        circle.setAttribute('r', '23');
        if (cls === 'value') {
            circle.setAttribute('stroke-dasharray', String(circumference));
            circle.setAttribute('stroke-dashoffset', String(circumference * (1 - inRange / assessed.length)));
        }
        svg.append(circle);
    }
    const label = node('div', undefined, 'ring-label');
    label.append(node('b', `${inRange}/${assessed.length}`));
    ring.append(svg, label);
    const wrap = node('div', undefined, 'ring-wrap');
    const caption = node('span', 'in range', 'ring-caption');
    caption.setAttribute('aria-hidden', 'true');
    wrap.append(ring, caption);
    return wrap;
}

function tiles(crop) {
    if (!crop.readings.length) return node('p', 'No readings yet.', 'tiles-empty');
    const grid = node('div', undefined, 'tiles');
    for (const reading of crop.readings) {
        const tile = node('div', undefined, 'tile');
        const chip = node('span', undefined, `chip ${readingTone(reading.status)}`);
        chip.append(icon(readingIcon(reading.name)));
        const text = node('div');
        text.append(node('b', readingText(reading)), node('span', readingShortName(reading.name)));
        tile.append(chip, text);
        grid.append(tile);
    }
    return grid;
}

function statusPill(crop, extraClass = '') {
    const pill = node('span', crop.needsAttention ? 'Needs attention' : 'Healthy',
        `pill pill-dot ${crop.needsAttention ? 'warn' : 'ok'} ${extraClass}`.trim());
    return pill;
}

let openMenu = null;
function closeMenu() {
    if (!openMenu) return;
    openMenu.menu.hidden = true;
    openMenu.button.setAttribute('aria-expanded', 'false');
    openMenu = null;
}
document.addEventListener('click', (event) => {
    if (openMenu && !openMenu.wrap.contains(event.target)) closeMenu();
});
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && openMenu) {
        const { button } = openMenu;
        closeMenu();
        button.focus();
    }
});

function cardMenu(crop, name) {
    const wrap = node('div', undefined, 'menu-wrap');
    const button = node('button', undefined, 'icon-btn');
    button.type = 'button';
    button.setAttribute('aria-label', `More actions for ${name}`);
    button.setAttribute('aria-haspopup', 'menu');
    button.setAttribute('aria-expanded', 'false');
    button.append(icon('i-more'));
    const menu = node('div', undefined, 'menu');
    menu.setAttribute('role', 'menu');
    menu.hidden = true;
    const remove = node('button', undefined, 'danger');
    remove.type = 'button';
    remove.setAttribute('role', 'menuitem');
    remove.append(icon('trash-icon', 'icon-sm'), 'Delete crop');
    menu.append(remove);
    wrap.append(button, menu);

    button.addEventListener('click', () => {
        const opening = menu.hidden;
        closeMenu();
        if (!opening) return;
        menu.hidden = false;
        button.setAttribute('aria-expanded', 'true');
        openMenu = { wrap, menu, button };
        remove.focus();
    });
    remove.addEventListener('click', async () => {
        closeMenu();
        const ok = await confirmDialog({
            title: `Delete ${name}?`,
            message: `This removes the ${crop.farmName ? crop.farmName + ' ' : ''}planting and its weather history. This cannot be undone.`,
            confirm: 'Delete crop',
            danger: true
        });
        if (!ok) return;
        button.disabled = true;
        try {
            await api('/api/crops/' + encodeURIComponent(crop.cropCycleId), { method: 'DELETE' });
            render(await api('/api/dashboard'));
            toast(`${name} was deleted.`);
        } catch (error) {
            button.disabled = false;
            if (error.status === 401) showPageError(error); else toast(error.message, 'error');
        }
    });
    return wrap;
}

function cropCard(crop) {
    const name = cropDisplayName(crop.cropType, cropTypes);
    const href = '/crop-details.html?' + new URLSearchParams({ cycleId: crop.cropCycleId });
    const card = node('article', undefined, 'crop-card');

    const media = node('div', undefined, 'crop-media');
    const img = node('img');
    img.alt = '';
    img.loading = 'lazy';
    img.decoding = 'async';
    img.addEventListener('error', () => {
        img.remove();
        const fallback = node('div', undefined, 'fallback');
        fallback.append(icon('i-leaf'));
        media.prepend(fallback);
    }, { once: true });
    img.src = cropImage(crop.cropType);
    media.append(img, statusPill(crop, 'on-photo'));

    const body = node('div', undefined, 'crop-body');
    const head = node('div', undefined, 'crop-head');
    const titles = node('div');
    if (crop.farmName) titles.append(node('span', crop.farmName, 'eyebrow'));
    const title = node('h2', undefined, 'crop-name');
    const link = node('a', name);
    link.href = href;
    title.append(link);
    const date = node('time', `Planted ${formatDay(crop.plantedAt)}`, 'crop-date');
    date.dateTime = crop.plantedAt;
    titles.append(title, date, statusPill(crop, 'list-only'));
    head.append(titles);
    const ring = rangeRing(crop);
    if (ring) head.append(ring);
    body.append(head, tiles(crop));

    const foot = node('div', undefined, 'crop-foot');
    const details = node('a', undefined, 'text-link');
    details.href = href;
    details.setAttribute('aria-label', `View details for ${name}`);
    details.append(node('span', 'View details'), icon('i-arrow-right', 'icon-sm icon-arrow'));
    foot.append(details, cardMenu(crop, name));

    card.append(media, body, foot);
    return card;
}

/* ---------- Page ---------- */

function renderSummary(crops) {
    const attention = crops.filter((crop) => crop.needsAttention).length;
    const healthy = crops.length - attention;
    $('dash-sub').textContent = crops.length
        ? `${crops.length} active ${crops.length === 1 ? 'crop' : 'crops'} · ${healthy} healthy`
        : 'No crops recorded yet';
    $('sum-attention').textContent = String(attention);
    $('sum-healthy').textContent = String(healthy);

    // Weather from the most recently planted crop that has it.
    const withWeather = crops.filter((crop) => crop.weather)
        .sort((a, b) => new Date(b.plantedAt) - new Date(a.plantedAt))[0];
    const w = withWeather?.weather;
    if (w) {
        const parts = [];
        if (w.temperatureMeanC != null) parts.push(`${formatNumber(w.temperatureMeanC)}°C`);
        if (w.sunshineDurationSeconds != null) parts.push(`${formatNumber(w.sunshineDurationSeconds / 3600)}h sun`);
        $('sum-weather').textContent = parts.join(' · ') || '—';
        $('sum-weather-label').textContent = withWeather.farmName ? `Weather · ${withWeather.farmName}` : 'Weather';
        // weatherMessage is a long technical note; it stays on the details page caption.
        $('sum-weather-note').textContent = `Daily average · ${formatDay(w.date)}`;
    } else {
        $('sum-weather').textContent = '—';
        $('sum-weather-label').textContent = 'Weather';
        $('sum-weather-note').textContent = crops.length ? 'No weather data yet' : '';
    }
}

function populateFarms(crops) {
    const select = $('filter-farm');
    const farms = [...new Set(crops.map((crop) => crop.farmName).filter(Boolean))].sort((a, b) => a.localeCompare(b));
    select.replaceChildren(select.options[0]);
    for (const farm of farms) {
        const option = node('option', farm);
        option.value = farm;
        select.append(option);
    }
    if (!farms.includes(filters.farm)) filters.farm = '';
    select.value = filters.farm;
}

const SORTS = {
    newest: (a, b) => new Date(b.plantedAt) - new Date(a.plantedAt),
    oldest: (a, b) => new Date(a.plantedAt) - new Date(b.plantedAt),
    attention: (a, b) => Number(b.needsAttention) - Number(a.needsAttention) || SORTS.newest(a, b),
    name: (a, b) => cropDisplayName(a.cropType, cropTypes).localeCompare(cropDisplayName(b.cropType, cropTypes))
};

function render(data) {
    dashboardData = data;
    const all = data.crops || [];
    renderSummary(all);
    populateFarms(all);

    const q = filters.q.toLowerCase();
    const crops = all.filter((crop) =>
        (!q || cropDisplayName(crop.cropType, cropTypes).toLowerCase().includes(q) || (crop.farmName || '').toLowerCase().includes(q)) &&
        (!filters.farm || crop.farmName === filters.farm) &&
        (!filters.health || (filters.health === 'attention') === Boolean(crop.needsAttention))
    ).sort(SORTS[filters.sort]);

    closeMenu();
    $('crop-grid').replaceChildren(...crops.map(cropCard));
    $('empty-state').hidden = all.length !== 0;
    $('summary-strip').hidden = all.length === 0;
    document.querySelector('.toolbar').hidden = all.length === 0;
    $('filter-empty').hidden = crops.length !== 0 || all.length === 0;
}

function setView(view) {
    $('crop-grid').classList.toggle('is-list', view === 'list');
    $('view-grid').setAttribute('aria-pressed', String(view !== 'list'));
    $('view-list').setAttribute('aria-pressed', String(view === 'list'));
    try { localStorage.setItem('dome-view', view); } catch { /* storage unavailable */ }
}

function bindControls() {
    $('search').addEventListener('input', (event) => { filters.q = event.target.value.trim(); render(dashboardData); });
    $('filter-farm').addEventListener('change', (event) => { filters.farm = event.target.value; render(dashboardData); });
    $('filter-health').addEventListener('change', (event) => { filters.health = event.target.value; render(dashboardData); });
    $('sort').addEventListener('change', (event) => { filters.sort = event.target.value; render(dashboardData); });
    $('clear-filters').addEventListener('click', () => {
        Object.assign(filters, { q: '', farm: '', health: '', sort: filters.sort });
        $('search').value = '';
        $('filter-health').value = '';
        render(dashboardData);
    });
    $('view-grid').addEventListener('click', () => setView('grid'));
    $('view-list').addEventListener('click', () => setView('list'));
    let saved = 'grid';
    try { saved = localStorage.getItem('dome-view') || 'grid'; } catch { /* storage unavailable */ }
    setView(saved);
}

async function start() {
    try {
        await requireFarmer();
        const [dashboard, types] = await Promise.all([api('/api/dashboard'), api('/api/crop-types')]);
        cropTypes = types;
        bindControls();
        render(dashboard);
        $('loading').hidden = true;
        $('dashboard-page').hidden = false;
        refreshPeriodically(async () => render(await api('/api/dashboard')));
    } catch (error) { showPageError(error); }
}

start();

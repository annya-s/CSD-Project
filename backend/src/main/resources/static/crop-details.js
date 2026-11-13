import {
    $, api, requireFarmer, showPageError, node, icon, toast, confirmDialog, setBusy, refreshPeriodically,
    weatherCaption, formatDay, cropDisplayName, cropImage, readingTone, readingStatusLabel, readingIcon,
    readingShortName, shortUnit, formatNumber, pinIcon, tileLayer, formatCoord
} from './api.js';

const SVG_NS = 'http://www.w3.org/2000/svg';
const DAY = 86400000;

// One reference photo for each supported crop, with the attribution its licence requires.
const cropPhotos = {
    POTATO: { title: 'Potato plant .jpg', author: 'Arg12730', license: 'by-sa/4.0' },
    SUGAR_CANE: { title: 'Sugar Cane (2858291490).jpg', author: 'Cliff', license: 'by/2.0' },
    APPLE: { title: 'Apple tree 1.jpg', author: 'GerFes', license: null },
    RICE: { title: 'Rice plant.jpg', author: 'Douglas Paul Perkins', license: 'by/4.0' },
    WHEAT: { title: 'Field of wheat.jpg', author: 'Depressedlymotivated', license: 'by/4.0' },
    MAIZE: { title: 'Maize plant.jpg', author: 'Kenogenic', license: 'by-sa/4.0' },
    TOMATO: { title: 'Tomato plant (1).jpg', author: 'Russell Yarwood', license: 'by-sa/2.0' },
    CARROT: { title: 'Carrot harvest.jpg', author: 'woodleywonderworks', license: 'by/2.0' },
    LETTUCE: { title: 'Lettuce (390008184).jpg', author: 'Clinton & Charles Robertson', license: 'by/2.0' },
    SOYBEAN: { title: 'Soybean Field (9620817061).jpg', author: 'United Soybean Board', license: 'by/2.0' }
};

let cropTypes = [];
let details;
let miniMap;
const doneActions = new Set(); // local-only ticks, keyed by action text

/* ---------- Helpers ---------- */
const startOfDay = (value) => {
    const date = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00`) : new Date(value);
    date.setHours(0, 0, 0, 0);
    return date;
};
const daysBetween = (a, b) => Math.round((startOfDay(b) - startOfDay(a)) / DAY);
const shortDay = (value) => new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' }).format(startOfDay(value));
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

// Two significant figures for small readings (0.15), one decimal for larger ones (18.3).
function formatReading(value) {
    return Math.abs(value) >= 10 ? value.toFixed(1) : String(Number(value.toPrecision(2)));
}

function svg(tag, attributes = {}) {
    const element = document.createElementNS(SVG_NS, tag);
    for (const [key, value] of Object.entries(attributes)) element.setAttribute(key, value);
    return element;
}

/* ---------- Hero ---------- */
function renderPhoto(cropType, cropName) {
    const image = $('crop-photo');
    const credit = $('crop-photo-credit');
    const photo = cropPhotos[cropType];
    const path = cropImage(cropType);
    if (image.getAttribute('src') === path) return; // refreshes keep the same photo
    if (!photo) {
        image.hidden = true;
        credit.replaceChildren();
        return;
    }
    image.alt = `Reference photograph of ${cropName}`;
    image.onload = () => { image.hidden = false; };
    image.onerror = () => { image.hidden = true; credit.replaceChildren(); };

    const source = node('a', `${photo.title} — ${photo.author}`);
    source.href = 'https://commons.wikimedia.org/wiki/File:' + encodeURIComponent(photo.title.replaceAll(' ', '_'));
    source.target = '_blank';
    source.rel = 'noopener noreferrer';
    credit.replaceChildren('Reference photo, not your planting: ', source);
    if (photo.license) {
        const license = node('a', `CC ${photo.license.replace('/', ' ').toUpperCase()}`);
        license.href = `https://creativecommons.org/licenses/${photo.license}/`;
        license.target = '_blank';
        license.rel = 'noopener noreferrer';
        credit.append(' · ', license);
    } else {
        credit.append(' · Public domain');
    }
    image.src = path;
}

function renderHero(data, name) {
    const { entry } = data;
    document.title = `Dome · ${name}`;
    $('detail-title').textContent = name;
    renderPhoto(entry.cropType, name);
    const status = $('hero-status');
    status.textContent = data.needsAttention ? 'Needs attention' : 'Healthy';
    status.className = `pill pill-dot on-photo ${data.needsAttention ? 'warn' : 'ok'}`;
    $('hero-farm').textContent = entry.farmName || '';
    $('hero-farm').hidden = !entry.farmName;
    $('detail-date').textContent = `Planted ${formatDay(entry.plantedAt, true)}`;
    $('detail-coords').textContent = `${formatCoord(entry.latitude)}, ${formatCoord(entry.longitude)}`;

    const score = Number.isFinite(data.healthScore) ? data.healthScore : null;
    $('hero-score').hidden = score === null;
    $('score-block').hidden = score === null;
    if (score !== null) {
        $('hero-score-value').textContent = String(score);
        $('hero-score').setAttribute('aria-label', `Health score ${score} out of 100`);
        $('score-text').textContent = `${score}/100`;
    }
}

/* ---------- Readings ---------- */
function readingCard(reading) {
    const tone = readingTone(reading.status);
    const card = node('article', undefined, `card reading ${tone}`);
    const top = node('div', undefined, 'reading-top');
    const chip = node('span', undefined, `chip ${tone}`);
    chip.append(icon(readingIcon(reading.name)));
    top.append(chip, node('span', readingStatusLabel(reading.status), `pill ${tone}`));

    const unit = shortUnit(reading.unit).trim();
    const value = node('p', undefined, 'reading-value');
    if (reading.value === null || reading.value === undefined) value.textContent = '—';
    else value.append(formatNumber(reading.value, reading.name), node('small', unit));

    const bar = node('div', undefined, 'range-bar');
    bar.setAttribute('aria-hidden', 'true');
    const foot = node('div', undefined, 'reading-foot');
    const range = reading.referenceRange;
    const v = reading.value;
    let note = 'Within range';

    if (range && Number.isFinite(range.minimum) && Number.isFinite(range.maximum)) {
        const span = Math.max(range.maximum - range.minimum, Math.abs(range.maximum) * 0.1, 0.01);
        let lo = range.minimum - span * 0.6;
        let hi = range.maximum + span * 0.6;
        if (Number.isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        const pct = (x) => `${(100 * (x - lo)) / (hi - lo)}%`;
        const band = node('span', undefined, 'band');
        band.style.left = pct(range.minimum);
        band.style.width = `${(100 * (range.maximum - range.minimum)) / (hi - lo)}%`;
        bar.append(band);
        if (Number.isFinite(v)) {
            const fill = node('span', undefined, 'fill');
            fill.style.width = pct(v);
            bar.append(fill);
        }
        const sep = unit === '°C' || unit === 'h' || unit === '%' ? '' : ' ';
        const fmt = (x) => formatNumber(x, reading.name);
        foot.append(node('span', `Optimal ${fmt(range.minimum)}–${fmt(range.maximum)}${sep}${unit}`));
        if (!Number.isFinite(v)) note = 'No data';
        else if (v < range.minimum) note = `${fmt(range.minimum - v)}${sep}${unit} below optimal`;
        else if (v > range.maximum) note = `${fmt(v - range.maximum)}${sep}${unit} above optimal`;
    } else {
        foot.append(node('span', 'No reference range'));
        note = Number.isFinite(v) ? 'Not assessed' : 'No data';
    }
    foot.append(node('b', note));
    card.append(top, node('p', readingShortName(reading.name), 'reading-label'), value, bar, foot);
    return card;
}

function renderReadings(data) {
    $('readings').replaceChildren(...data.readings.map(readingCard));
    if (!data.readings.length) $('readings').replaceChildren(node('p', 'No readings yet. Weather data is needed.', 'panel-muted'));
    $('weather-caption').textContent = weatherCaption(data.weather, data.weatherSource, data.weatherMessage);
    $('weather-updated').hidden = !data.weather?.date;
    if (data.weather?.date) $('weather-updated-text').textContent = `Updated ${formatDay(data.weather.date)}`;
}

/* ---------- Assessment + actions ---------- */
function renderAssessment(data) {
    $('assess-title').textContent = data.needsAttention ? 'Action recommended' : 'Looking healthy';
    $('detail-note').textContent = data.note || '';
    $('detail-note').hidden = !data.note;
    $('issues').replaceChildren(...(data.issues || []).map((issue) => {
        const item = node('li');
        item.append(icon('i-alert'), node('span', issue));
        return item;
    }));
    $('issues').hidden = !(data.issues || []).length;
}

function renderActions(data) {
    const list = $('actions');
    const actions = data.recommendedActions || [];
    list.replaceChildren(...actions.map((text) => {
        const row = node('li', undefined, 'action-row');
        const toggle = node('button', undefined, 'check-toggle');
        toggle.type = 'button';
        const done = doneActions.has(text);
        row.classList.toggle('is-done', done);
        toggle.setAttribute('aria-pressed', String(done));
        toggle.setAttribute('aria-label', `Mark “${text}” as done`);
        toggle.append(icon('i-check'));
        toggle.addEventListener('click', () => {
            const nowDone = !doneActions.has(text);
            if (nowDone) doneActions.add(text); else doneActions.delete(text);
            row.classList.toggle('is-done', nowDone);
            toggle.setAttribute('aria-pressed', String(nowDone));
        });
        row.append(node('p', text), toggle);
        return row;
    }));
    if (!actions.length) list.replaceChildren(node('li', 'No actions needed from the current readings.', 'empty-line'));
}

async function runAssessment(cycleId) {
    const button = $('ai-button');
    const result = $('ai-result');
    setBusy(button, true, 'Asking the crop advisor…');
    try {
        const response = await api(`/api/crops/${encodeURIComponent(cycleId)}/assessment`, {
            method: 'POST', signal: AbortSignal.timeout(60000)
        });
        const row = node('div', undefined, 'ai-row');
        const tag = node('span', undefined, 'ai-tag');
        tag.append(icon('i-sparkle', 'icon-sm'), 'AI recommendation');
        row.append(tag, node('p', response.recommendation),
            node('p', response.confidencePercent == null
                ? 'Confidence unavailable'
                : `Confidence ${response.confidencePercent.toFixed(2)}%`, 'confidence'));
        result.replaceChildren(row);
    } catch (error) {
        if (error.status === 401) return showPageError(error);
        const row = node('div', undefined, 'ai-row is-error');
        row.append(node('p', 'We couldn’t generate a recommendation right now. Please try again in a moment.'));
        result.replaceChildren(row);
    } finally {
        setBusy(button, false);
    }
}

/* ---------- Growth outlook (yield + biomass) ---------- */
// Logistic growth shape normalised to 0 → 1 (illustrative curve between known end points).
function growthShape(x) {
    const s = (t) => 1 / (1 + Math.exp(-9 * (t - 0.5)));
    return (s(x) - s(0)) / (s(1) - s(0));
}

let chartCount = 0;
// series: [{ value, cls: 'optimal' | 'current' | 'normal', area? }], each drawn from 0 at planting to value at endDate.
function buildChart(plantedAt, endDate, series, label) {
    const W = 600;
    const H = 200;
    const total = Math.max(1, daysBetween(plantedAt, endDate));
    const elapsed = clamp(daysBetween(plantedAt, new Date()), 0, total);
    const yMax = Math.max(...series.map((line) => line.value)) * 1.1 || 1;
    const points = (end) => Array.from({ length: 61 }, (_, i) => {
        const x = i / 60;
        return [x * W, H - (growthShape(x) * end / yMax) * H];
    });
    const d = (pts) => pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');

    const chart = node('div', undefined, 'chart');
    const graph = svg('svg', { viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: 'none', role: 'img', 'aria-label': label });
    const fillId = `chart-fill-${++chartCount}`;
    const defs = svg('defs');
    const gradient = svg('linearGradient', { id: fillId, x1: '0', y1: '0', x2: '0', y2: '1' });
    gradient.append(svg('stop', { offset: '0', 'stop-color': '#5E9466', 'stop-opacity': '.22' }),
        svg('stop', { offset: '1', 'stop-color': '#5E9466', 'stop-opacity': '0' }));
    defs.append(gradient);
    graph.append(defs);
    for (const f of [0.25, 0.5, 0.75]) graph.append(svg('line', { class: 'grid-line', x1: 0, x2: W, y1: H * f, y2: H * f }));
    for (const line of series) {
        const pts = points(line.value);
        if (line.area) graph.append(svg('path', { d: `${d(pts)} L${W} ${H} L0 ${H} Z`, fill: `url(#${fillId})` }));
        graph.append(svg('path', { class: `line-${line.cls}`, d: d(pts) }));
    }
    const todayX = (elapsed / total) * W;
    graph.append(svg('line', { class: 'today-line', x1: todayX, x2: todayX, y1: 0, y2: H }));
    chart.append(graph);

    const axis = node('div', undefined, 'chart-axis');
    axis.append(node('span', `Planted · ${shortDay(plantedAt)}`));
    const share = elapsed / total;
    if (share > 0.26 && share < 0.74) { // keep clear of the end labels
        const today = node('span', 'Today', 'today');
        today.style.left = `${share * 100}%`;
        axis.append(today);
    }
    axis.append(node('span', `Full growth · ${shortDay(endDate)}`));

    const wrap = node('div');
    wrap.append(chart, axis);
    return wrap;
}

function legendItem(label, value, unit, keyClass, extraClass = '') {
    const item = node('div', undefined, 'legend-item');
    const key = node('span', undefined, 'legend-key');
    if (keyClass !== null) key.append(node('i', undefined, keyClass));
    key.append(label);
    const number = node('p', undefined, `legend-value ${extraClass}`.trim());
    number.append(value);
    if (unit) number.append(node('small', unit));
    item.append(key, number);
    return item;
}

// Machine-learning yield outlook: expected vs a normal year, projected to the full-growth date.
function yieldSection(prediction, plantedAt) {
    const outlook = prediction.yieldOutlook;
    const head = node('h3', 'Yield outlook · machine learning', 'subhead');
    if (!outlook) return [head, node('p', 'Yield outlook unavailable for this crop.', 'panel-muted')];
    const usable = outlook.status === 'ML_ESTIMATE' && Number.isFinite(outlook.expectedYield)
        && Number.isFinite(outlook.normalYield) && prediction.projectionDate;
    if (!usable) return [head, node('p', outlook.message || 'Yield outlook unavailable.', 'panel-muted')];

    const layout = node('div', undefined, 'biomass-layout');
    const legend = node('div', undefined, 'legend');
    const change = Math.round(100 * (outlook.ratio - 1));
    legend.append(
        legendItem('Expected yield', outlook.expectedYield.toFixed(1), outlook.unit, 'opt'),
        legendItem('Normal year', outlook.normalYield.toFixed(1), outlook.unit, 'normal'),
        legendItem('vs normal year', change === 0 ? 'Same' : `${change > 0 ? '+' : '−'}${Math.abs(change)}%`, '', null, change < 0 ? 'loss' : 'gain')
    );
    layout.append(buildChart(plantedAt, prediction.projectionDate, [
        { value: outlook.expectedYield, cls: 'optimal', area: true },
        { value: outlook.normalYield, cls: 'normal' }
    ], `Projected yield to ${formatDay(prediction.projectionDate)}: expected ${outlook.expectedYield.toFixed(1)}, normal ${outlook.normalYield.toFixed(1)} ${outlook.unit}`), legend);
    const note = node('p', undefined, 'note-bar');
    note.append(icon('i-sparkle'), node('span',
        `Model: ${outlook.model}, nearest reference region ${outlook.region}. End-of-season yields come from the model; the curve between planting and full growth is illustrative.`));
    return [head, layout, note];
}

function biomassSection(prediction, plantedAt) {
    const head = node('h3', 'Biomass · current vs optimal conditions', 'subhead');
    const unit = prediction.unit || '';
    if (!(Number.isFinite(prediction.referenceEstimate) && prediction.projectionDate)) {
        return [head, node('p', prediction.message || 'Biomass estimate unavailable.', 'panel-muted')];
    }
    const cur = Number.isFinite(prediction.value) ? prediction.value : null;
    const ref = prediction.referenceEstimate;
    const layout = node('div', undefined, 'biomass-layout');
    const legend = node('div', undefined, 'legend');
    legend.append(
        legendItem('Current conditions', cur === null ? '—' : cur.toFixed(1), cur === null ? '' : unit, ''),
        legendItem('Optimal conditions', ref.toFixed(1), unit, 'opt')
    );
    if (cur !== null && cur > 0) {
        const gap = 100 * (ref - cur) / cur;
        legend.append(legendItem('Potential difference', gap < 0.5 ? 'On target' : `+${gap.toFixed(0)}%`, '', null, 'gain'));
    }
    if (Number.isFinite(prediction.currentEstimate)) {
        legend.append(legendItem('Estimated today', prediction.currentEstimate.toFixed(1), unit, null));
    }
    const series = [{ value: ref, cls: 'optimal', area: true }];
    if (cur !== null) series.push({ value: cur, cls: 'current' });
    layout.append(buildChart(plantedAt, prediction.projectionDate, series,
        `Projected biomass from planting to ${formatDay(prediction.projectionDate)}`), legend);

    const parts = [head, layout];
    const causes = (prediction.causes || []).filter((cause) => cause.shortfallPercent >= 0.5);
    if (cur !== null && causes.length) {
        parts.push(node('p', 'What’s holding growth back', 'subhead subhead-sm'));
        const list = node('ul', undefined, 'causes');
        for (const cause of causes) {
            const item = node('li');
            const bar = node('span', undefined, 'cause-bar');
            const fill = node('i');
            fill.style.width = `${clamp(cause.shortfallPercent, 0, 100)}%`;
            bar.append(fill);
            item.append(
                node('span', `${cause.factor} ${cause.direction} · ${formatReading(cause.actual)} vs ${formatReading(cause.optimal)} ${cause.unit}`),
                node('b', `−${cause.shortfallPercent.toFixed(0)}%`),
                bar
            );
            list.append(item);
        }
        parts.push(list);
    }
    if (prediction.message) {
        const note = node('p', undefined, 'note-bar');
        note.append(icon('i-leaf'), node('span', `Projected curves; the shape between planting and full growth is illustrative. ${prediction.message}`));
        parts.push(note);
    }
    return parts;
}

function renderBiomass(data) {
    const prediction = data.prediction;
    if (!prediction) {
        $('biomass-body').replaceChildren(node('p', 'Run a prediction to see the machine-learning yield outlook and the biomass projection.', 'panel-muted'));
        return;
    }
    $('biomass-body').replaceChildren(
        ...yieldSection(prediction, data.entry.plantedAt),
        ...biomassSection(prediction, data.entry.plantedAt)
    );
}

/* ---------- Timeline ---------- */
function renderTimeline(data) {
    const planted = data.entry.plantedAt;
    const elapsed = Math.max(0, daysBetween(planted, new Date()));
    const end = data.prediction?.projectionDate;
    $('timeline-bar').hidden = !end;
    $('timeline-pct').hidden = !end;
    if (!end) {
        $('timeline-title').textContent = `Day ${elapsed} since planting`;
        $('timeline-desc').textContent = 'Run a biomass prediction to see the projected full-growth date.';
        return;
    }
    const total = Math.max(1, daysBetween(planted, end));
    const remaining = daysBetween(new Date(), end);
    const pct = Math.round(clamp(elapsed / total, 0, 1) * 100);
    $('timeline-title').textContent = remaining > 0
        ? `About ${remaining} ${remaining === 1 ? 'day' : 'days'} to projection date`
        : 'Projection date reached';
    $('timeline-desc').textContent = `Projected full growth on ${formatDay(end, true)}, from the biomass model.`;
    $('timeline-pct').textContent = `${pct}%`;
    $('progress').setAttribute('aria-valuenow', String(pct));
    $('progress-fill').style.width = `${pct}%`;
    $('tl-start').textContent = `Planted · ${shortDay(planted)}`;
    $('tl-today').textContent = `Today · Day ${elapsed}`;
    $('tl-end').textContent = `Projection · ${shortDay(end)}`;
}

/* ---------- Location ---------- */
function renderLocation(data) {
    const { entry } = data;
    $('location-title').textContent = entry.farmName || 'Field location';
    $('location-coords').textContent = `${formatCoord(entry.latitude)}, ${formatCoord(entry.longitude)}`;
    const point = [entry.latitude, entry.longitude];
    if (!window.L) return;
    if (!miniMap) {
        miniMap = window.L.map('mini-map', {
            dragging: false, zoomControl: false, scrollWheelZoom: false, doubleClickZoom: false,
            boxZoom: false, keyboard: false, touchZoom: false
        }).setView(point, 13);
        tileLayer().addTo(miniMap);
        window.L.marker(point, { icon: pinIcon(), keyboard: false, interactive: false }).addTo(miniMap);
    }
}

/* ---------- Page ---------- */
function render(data) {
    details = data;
    const name = cropDisplayName(data.entry.cropType, cropTypes);
    renderHero(data, name);
    renderReadings(data);
    renderAssessment(data);
    renderActions(data);
    renderBiomass(data);
    renderTimeline(data);
    renderLocation(data);
}

async function start() {
    try {
        await requireFarmer();
        const cycleId = new URLSearchParams(window.location.search).get('cycleId');
        if (!cycleId) throw new Error('That crop link is incomplete. Go back to the dashboard and choose a crop.');
        const detailsUrl = '/api/crops/' + encodeURIComponent(cycleId);
        const [data, types] = await Promise.all([
            api(detailsUrl, { signal: AbortSignal.timeout(15000) }),
            api('/api/crop-types', { signal: AbortSignal.timeout(15000) })
        ]);
        cropTypes = types;
        $('loading').hidden = true;
        $('detail-page').hidden = false; // visible before Leaflet measures its container
        render(data);

        let predicting = false;
        $('run-prediction').addEventListener('click', async () => {
            if (predicting) return;
            predicting = true;
            const button = $('run-prediction');
            const status = $('prediction-run-status');
            setBusy(button, true, 'Predicting…');
            status.textContent = 'Checking saved weather, refreshing it if needed, and running your crop model…';
            try {
                // The endpoint checks ownership, refreshes stale averages and runs this planting's model.
                const prediction = await api(detailsUrl + '/prediction', { method: 'POST', signal: AbortSignal.timeout(60000) });
                render({ ...details, prediction });
                status.textContent = prediction?.status === 'ESTIMATE'
                    ? 'Prediction updated.'
                    : 'Prediction unavailable. See the explanation below.';
            } catch (error) {
                if (error.status === 401) return showPageError(error);
                status.textContent = 'Could not run the prediction. Previously displayed values have not changed. Please try again.';
            } finally {
                predicting = false;
                setBusy(button, false);
            }
        });

        $('ai-button').addEventListener('click', () => runAssessment(cycleId));

        $('delete-crop').addEventListener('click', async () => {
            const name = cropDisplayName(details.entry.cropType, cropTypes);
            const ok = await confirmDialog({
                title: `Delete ${name}?`,
                message: 'This removes the planting and its weather history. This cannot be undone.',
                confirm: 'Delete crop',
                danger: true
            });
            if (!ok) return;
            const button = $('delete-crop');
            button.disabled = true;
            try {
                await api(detailsUrl, { method: 'DELETE' });
                window.location.assign('/dashboard.html');
            } catch (error) {
                button.disabled = false;
                if (error.status === 401) showPageError(error); else toast(error.message, 'error');
            }
        });

        // Keep conditions current; a manually run prediction survives refreshes that omit it.
        refreshPeriodically(async () => {
            const fresh = await api(detailsUrl, { signal: AbortSignal.timeout(15000) });
            render({ ...fresh, prediction: fresh.prediction ?? details.prediction });
        });
    } catch (error) {
        $('loading').hidden = true;
        showPageError(error);
    }
}

start();

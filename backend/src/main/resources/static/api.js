// Shared by all pages. Requests use the login cookie automatically.
export async function api(path, options = {}) {
    const headers = { ...options.headers };
    if (options.method && options.method !== 'GET') {
        const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal: options.signal });
        if (!response.ok) throw new Error('Unable to prepare your request. Please refresh.');
        const token = await response.json();
        headers[token.headerName] = token.token;
    }

    const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
    if (response.status === 204) return null;
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
        const error = new Error(body.message || 'Something went wrong. Please try again.');
        error.status = response.status;
        throw error;
    }
    return body;
}

export function jsonPost(body) {
    return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

let logoutBound = false;

// Used by every signed-in page before loading data. Fills the shared top bar.
export async function requireFarmer() {
    const farmer = await api('/api/auth/me');
    const admin = farmer.role === 'ADMIN';
    const home = admin ? '/admin.html' : '/dashboard.html';

    const welcome = $('welcome');
    if (welcome) {
        welcome.replaceChildren('Welcome, ', node('b', admin ? `${farmer.username} · Admin` : farmer.username));
    }
    document.querySelectorAll('[data-home]').forEach((link) => { link.href = home; });
    const here = window.location.pathname;
    document.querySelectorAll('.nav-link[href]').forEach((link) => {
        if (link.getAttribute('href') === here) link.setAttribute('aria-current', 'page');
    });

    if (!logoutBound && $('logout')) {
        logoutBound = true;
        $('logout').addEventListener('click', async () => {
            $('logout').disabled = true;
            try {
                await api('/api/auth/logout', { method: 'POST' });
                window.location.replace('/login.html');
            } catch (error) {
                showPageError(error);
            } finally {
                $('logout').disabled = false;
            }
        });
    }
    return farmer;
}

export function showPageError(error) {
    if (error.status === 401) {
        // Return to the originally requested page after logging in.
        const next = window.location.pathname + window.location.search;
        window.location.replace(`/login.html?${new URLSearchParams({ next })}`);
        return;
    }
    if ($('loading')) $('loading').hidden = true;
    showMessage('global-message', error.message);
}

// Small display helpers shared by the page scripts.
export const $ = (id) => document.getElementById(id);

export function showMessage(id, message = '') {
    $(id).textContent = message;
    $(id).hidden = !message;
}

export function node(tag, text, className) {
    const element = document.createElement(tag);
    if (text !== undefined && text !== null) element.textContent = text;
    if (className) element.className = className;
    return element;
}

const SVG_NS = 'http://www.w3.org/2000/svg';

// <svg class="icon"><use href="/icons.svg#i-name"></svg>
export function icon(name, className = '') {
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('class', `icon ${className}`.trim());
    svg.setAttribute('aria-hidden', 'true');
    const use = document.createElementNS(SVG_NS, 'use');
    use.setAttribute('href', `/icons.svg#${name}`);
    svg.append(use);
    return svg;
}

export function formatDate(value) {
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium', timeStyle: 'medium'
    }).format(new Date(value));
}

// "18 Mar 2025" (or "18 March 2025" with long = true)
export function formatDay(value, long = false) {
    if (!value) return '—';
    const date = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
        ? new Date(`${value}T00:00`) : new Date(value);
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: long ? 'long' : 'short', year: 'numeric' }).format(date);
}

export function cropDisplayName(code, cropTypes = []) {
    return cropTypes.find((type) => type.code === code)?.name
        || String(code || '').toLowerCase().replaceAll('_', ' ').replace(/^\w/, (c) => c.toUpperCase());
}

export const cropImage = (code) => `/images/crops/${String(code).toLowerCase()}.jpg`;

/* ---------- Readings ---------- */
const TONES = {
    WITHIN_RANGE: ['ok', 'Optimal'],
    BELOW_RANGE: ['warn', 'Low'],
    ABOVE_RANGE: ['warn', 'High'],
    BELOW_RANGE_SEVERE: ['bad', 'Very low'],
    ABOVE_RANGE_SEVERE: ['bad', 'Very high'],
    NOT_ASSESSED: ['none', 'Not assessed']
};
export const readingTone = (status) => (TONES[status] || TONES.NOT_ASSESSED)[0];
export const readingStatusLabel = (status) => (TONES[status] || TONES.NOT_ASSESSED)[1];

const READING_META = {
    'Temperature': { icon: 'i-thermometer', short: 'Temperature' },
    'Sunlight': { icon: 'i-sun', short: 'Sunlight' },
    'Vapour pressure deficit': { icon: 'i-trend', short: 'VPD' },
    'Soil moisture': { icon: 'i-droplet', short: 'Soil moisture' },
    'Humidity': { icon: 'i-droplets', short: 'Humidity' }
};
export const readingIcon = (name) => READING_META[name]?.icon || 'i-leaf';
export const readingShortName = (name) => READING_META[name]?.short || name;

const UNIT_SHORT = { 'hours/day': 'h', '% by volume': '%', '°C': '°C', '%': '%' };
export const shortUnit = (unit) => UNIT_SHORT[unit] ?? (unit ? ` ${unit}` : '');

export function formatNumber(value, name) {
    if (value === null || value === undefined || !Number.isFinite(Number(value))) return '—';
    const digits = name === 'Vapour pressure deficit' ? 2 : 1;
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(value);
}

// "27°C", "8.4h", "1.4 kPa", "—"
export function readingText(reading) {
    if (reading.value === null || reading.value === undefined) return '—';
    return formatNumber(reading.value, reading.name) + shortUnit(reading.unit);
}

export function weatherCaption(weather, source, message) {
    const provider = source === 'OPEN_METEO' ? 'Open-Meteo weather model' : 'Manually supplied weather';
    const caption = weather
        ? `${provider} · ${weather.date} (${weather.timezone}). Daily averages and sunshine total; not field sensor measurements.`
        : 'No weather readings available.';
    return message ? `${caption} ${message}` : caption;
}

/* ---------- Maps (Leaflet is loaded globally by the page) ---------- */
export function pinIcon() {
    return window.L.divIcon({
        className: 'dome-pin',
        html: '<span class="pin-body"><svg class="icon" aria-hidden="true"><use href="/icons.svg#i-pin"></use></svg></span>',
        iconSize: [40, 40],
        iconAnchor: [20, 48] // tip of the rotated teardrop
    });
}

export function tileLayer() {
    return window.L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        noWrap: true,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    });
}

export const formatCoord = (value) => Number(value).toFixed(4);

/* ---------- Feedback ---------- */
export function toast(message, tone = 'ok') {
    let region = document.querySelector('.toast-region');
    if (!region) {
        region = node('div', undefined, 'toast-region');
        region.setAttribute('role', 'status');
        region.setAttribute('aria-live', 'polite');
        document.body.append(region);
    }
    const item = node('div', undefined, `toast${tone === 'error' ? ' is-error' : ''}`);
    item.append(icon(tone === 'error' ? 'i-alert' : 'i-check-circle'), node('span', message));
    region.append(item);
    setTimeout(() => {
        item.classList.add('is-leaving');
        setTimeout(() => item.remove(), 320);
    }, 4200);
}

// Promise<boolean>. A native <dialog> keeps focus trapped and Escape working.
export function confirmDialog({ title, message, confirm = 'Confirm', danger = false }) {
    return new Promise((resolve) => {
        const dialog = node('dialog', undefined, 'dialog');
        dialog.setAttribute('aria-labelledby', 'dialog-title');
        const body = node('div', undefined, 'dialog-body');
        const chip = node('span', undefined, `chip lg ${danger ? 'bad' : ''}`);
        chip.append(icon(danger ? 'trash-icon' : 'i-alert'));
        const heading = node('h2', title, 'serif-sm');
        heading.id = 'dialog-title';
        body.append(chip, heading, node('p', message));
        const actions = node('div', undefined, 'dialog-actions');
        const cancel = node('button', 'Cancel', 'btn btn-outline');
        cancel.type = 'button';
        const ok = node('button', confirm, `btn ${danger ? 'btn-danger' : 'btn-primary'}`);
        ok.type = 'button';
        actions.append(cancel, ok);
        dialog.append(body, actions);
        document.body.append(dialog);

        let result = false;
        cancel.addEventListener('click', () => dialog.close());
        ok.addEventListener('click', () => { result = true; dialog.close(); });
        dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
        dialog.addEventListener('close', () => { dialog.remove(); resolve(result); });
        dialog.showModal();
        cancel.focus();
    });
}

// Toggle a button's loading state without losing its original contents.
export function setBusy(button, busy, busyLabel) {
    if (busy) {
        button.dataset.idleHtml = button.innerHTML;
        button.disabled = true;
        button.classList.add('is-busy');
        if (busyLabel) button.textContent = busyLabel;
    } else {
        button.disabled = false;
        button.classList.remove('is-busy');
        if (button.dataset.idleHtml !== undefined) button.innerHTML = button.dataset.idleHtml;
        delete button.dataset.idleHtml;
    }
}

// Refresh assessments while visible. The backend reuses API results for up to 15 minutes.
export function refreshPeriodically(refresh) {
    let loading = false;
    const timer = setInterval(async () => {
        if (document.hidden || loading) return;
        loading = true;
        try {
            await refresh();
            showMessage('global-message');
        } catch (error) {
            showPageError(error);
        } finally {
            loading = false;
        }
    }, 60000);
    window.addEventListener('pagehide', () => clearInterval(timer), { once: true });
}

// Mobile navigation toggle (pages without a top bar simply skip this).
const navToggle = $('nav-toggle');
if (navToggle) {
    navToggle.addEventListener('click', () => {
        const open = navToggle.closest('.topbar').classList.toggle('is-open');
        navToggle.setAttribute('aria-expanded', String(open));
        navToggle.querySelector('use').setAttribute('href', `/icons.svg#${open ? 'i-x' : 'i-menu'}`);
    });
}

// Recheck login when Back restores a page from the browser's memory cache.
window.addEventListener('pageshow', (event) => {
    if (event.persisted) window.location.reload();
});

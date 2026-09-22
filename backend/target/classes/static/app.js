// All requests go to the Spring Boot server. Database credentials stay on the server.
const $ = (id) => document.getElementById(id);
let farmer = null;
let cropTypes = [];
let registering = false;
let routeVersion = 0;

async function api(path, options = {}) {
    const headers = { ...options.headers };
    if (options.method && options.method !== 'GET') {
        const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
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

function jsonPost(body) {
    return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

function node(tag, text, className) {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
}

function showMessage(id, message = '') {
    $(id).textContent = message;
    $(id).hidden = !message;
}

function showPage(id) {
    document.querySelectorAll('.page').forEach((page) => { page.hidden = page.id !== id; });
    $('navigation').hidden = !farmer;
    $('logout').hidden = !farmer;
    $('brand-caption').hidden = Boolean(farmer);
    $('welcome').textContent = farmer ? `Welcome, ${farmer.displayName}` : '';
    $('loading').hidden = true;
}

function cropName(code) {
    return cropTypes.find((crop) => crop.code === code)?.name || code;
}

function formatDate(value) {
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium', timeStyle: 'medium'
    }).format(new Date(value));
}

function localDateTime() {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 19);
}

function metric(name, value = 'Not measured') {
    const row = node('div', undefined, 'metric');
    const term = node('dt', name);
    const description = node('dd');
    const dot = node('span', undefined, 'status-dot');
    dot.setAttribute('aria-hidden', 'true');
    description.append(dot, document.createTextNode(value));
    row.append(term, description);
    return row;
}

function renderDashboard(data) {
    $('summary').textContent = data.summary;
    $('crop-grid').replaceChildren();
    $('empty-state').hidden = data.crops.length !== 0;

    for (const crop of data.crops) {
        const card = node('a', undefined, 'crop-card');
        const params = new URLSearchParams({ type: crop.cropType, plantedAt: crop.plantedAt });
        card.href = `#crop?${params}`;
        card.append(node('h2', cropName(crop.cropType)));
        const date = node('time', `Planted ${formatDate(crop.plantedAt)}`);
        date.dateTime = crop.plantedAt;
        card.append(date);
        const metrics = node('dl');
        ['Water', 'Soil moisture', 'UV exposure', 'Fertilizer'].forEach((name) => metrics.append(metric(name)));
        card.append(metrics, node('span', 'View planting details →', 'card-footer'));
        $('crop-grid').append(card);
    }
}

function renderDetails(data) {
    $('detail-title').textContent = cropName(data.entry.cropType);
    $('detail-date').textContent = `Planted ${formatDate(data.entry.plantedAt)}`;
    $('detail-latitude').textContent = data.entry.latitude;
    $('detail-longitude').textContent = data.entry.longitude;
    $('detail-note').textContent = data.note;
    $('readings').replaceChildren();
    for (const reading of data.readings) {
        const value = reading.value === null ? 'Not measured' : `${reading.value} ${reading.unit || ''}`;
        $('readings').append(metric(reading.name, value));
    }
    $('actions').replaceChildren(...data.recommendedActions.map((action) => node('li', action)));
}

async function loadAccount() {
    farmer = await api('/api/auth/me');
    cropTypes = await api('/api/crop-types');
    const placeholder = node('option', 'Choose a crop');
    placeholder.value = '';
    $('crop-type').replaceChildren(placeholder);
    for (const crop of cropTypes) {
        const option = node('option', crop.name);
        option.value = crop.code;
        $('crop-type').append(option);
    }
}

async function route() {
    const currentVersion = ++routeVersion;
    showMessage('global-message');
    if (!farmer) {
        showPage('login-page');
        return;
    }

    const hash = window.location.hash;
    document.querySelectorAll('.page').forEach((page) => { page.hidden = true; });
    $('loading').textContent = 'Loading your crop records…';
    $('loading').hidden = false;

    try {
        if (hash === '#new') {
            $('planted-at').max = localDateTime();
            if (!$('planted-at').value) $('planted-at').value = localDateTime();
            showMessage('entry-error');
            showPage('entry-page');
            $('entry-title').focus();
        } else if (hash.startsWith('#crop?')) {
            const params = new URLSearchParams(hash.split('?')[1]);
            const type = params.get('type');
            const plantedAt = params.get('plantedAt');
            if (!type || !plantedAt) throw new Error('That crop link is incomplete. Return to Overview.');
            const data = await api(`/api/crops/${encodeURIComponent(type)}?${new URLSearchParams({ plantedAt })}`);
            if (currentVersion !== routeVersion) return;
            renderDetails(data);
            showPage('detail-page');
            $('detail-title').focus();
        } else {
            const data = await api('/api/dashboard');
            if (currentVersion !== routeVersion) return;
            renderDashboard(data);
            showPage('dashboard-page');
            $('dashboard-title').focus();
        }
    } catch (error) {
        if (currentVersion !== routeVersion) return;
        $('loading').hidden = true;
        if (error.status === 401) {
            farmer = null;
            showPage('login-page');
        }
        showMessage('global-message', error.message);
    }
}

$('auth-toggle').addEventListener('click', () => {
    registering = !registering;
    $('login-title').textContent = registering ? 'Start your growing season.' : 'Let’s get you back in the field.';
    $('auth-description').textContent = registering ? 'Create an account for your farm.' : 'Log in to your farm overview.';
    $('display-name-field').hidden = !registering;
    $('display-name').required = registering;
    $('password').minLength = registering ? 10 : 1;
    $('password').autocomplete = registering ? 'new-password' : 'current-password';
    $('password-hint').hidden = !registering;
    $('auth-submit').textContent = registering ? 'Create account' : 'Log in';
    $('auth-toggle').textContent = registering ? 'Already registered? Log in' : 'Create an account';
    showMessage('auth-error');
});

$('auth-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    showMessage('auth-error');
    const username = $('username').value;
    const password = $('password').value;
    $('auth-submit').disabled = true;
    $('auth-toggle').disabled = true;
    $('auth-submit').textContent = registering ? 'Creating account…' : 'Logging in…';

    try {
        if (registering) {
            await api('/api/auth/register', jsonPost({ username, password, displayName: $('display-name').value.trim() }));
            // Registration succeeded. A failed login should not try to register the same account again.
            registering = false;
            $('display-name-field').hidden = true;
            $('display-name').required = false;
            $('password').autocomplete = 'current-password';
            $('password-hint').hidden = true;
            $('auth-toggle').textContent = 'Create an account';
            $('login-title').textContent = 'Let’s get you back in the field.';
            $('auth-description').textContent = 'Your account is ready. Log in to continue.';
        }
        await api('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ username, password })
        });
        await loadAccount();
        $('auth-form').reset();
        history.replaceState(null, '', '#dashboard');
        await route();
    } catch (error) {
        showMessage('auth-error', error.message);
    } finally {
        $('auth-submit').disabled = false;
        $('auth-toggle').disabled = false;
        $('auth-submit').textContent = registering ? 'Create account' : 'Log in';
    }
});

$('entry-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    showMessage('entry-error');
    $('entry-submit').disabled = true;
    $('entry-submit').textContent = 'Saving…';
    try {
        await api('/api/crops', jsonPost({
            cropType: $('crop-type').value,
            plantedAt: new Date($('planted-at').value).toISOString(),
            latitude: Number($('latitude').value),
            longitude: Number($('longitude').value)
        }));
        $('entry-form').reset();
        window.location.hash = '#dashboard';
    } catch (error) {
        if (error.status === 401) {
            farmer = null;
            showPage('login-page');
            showMessage('auth-error', 'Your session expired. Log in to save your crop entry.');
        } else {
            showMessage('entry-error', error.message);
        }
    } finally {
        $('entry-submit').disabled = false;
        $('entry-submit').textContent = 'Save crop entry';
    }
});

$('logout').addEventListener('click', async () => {
    $('logout').disabled = true;
    try {
        await api('/api/auth/logout', { method: 'POST' });
        farmer = null;
        cropTypes = [];
        $('crop-grid').replaceChildren();
        $('entry-form').reset();
        history.replaceState(null, '', '#login');
        await route();
    } catch (error) {
        showMessage('global-message', error.message);
    } finally {
        $('logout').disabled = false;
    }
});

window.addEventListener('hashchange', route);

async function start() {
    try {
        await loadAccount();
    } catch (error) {
        farmer = null;
        if (error.status !== 401) {
            showPage('login-page');
            showMessage('global-message', 'Cannot reach the server. Check the connection and refresh to try again.');
            return;
        }
    }
    await route();
}

start();

// Shared by all four pages. Requests use the login cookie automatically.
export async function api(path, options = {}) {
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

export function jsonPost(body) {
    return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

// Used by dashboard.js, crop-details.js, and new-entry.js before loading data.
export async function requireFarmer() {
    const farmer = await api('/api/auth/me');
    $('welcome').textContent = `Welcome, ${farmer.displayName}`;
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
    return farmer;
}

export function showPageError(error) {
    if (error.status === 401) {
        // Return to the originally requested page after logging in.
        const next = window.location.pathname + window.location.search;
        window.location.replace(`/login.html?${new URLSearchParams({ next })}`);
        return;
    }
    $('loading').hidden = true;
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
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
}

export function formatDate(value) {
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium', timeStyle: 'medium'
    }).format(new Date(value));
}

export function metric(name, value = 'Not measured') {
    const row = node('div', undefined, 'metric');
    const description = node('dd');
    const dot = node('span', undefined, 'status-dot');
    dot.setAttribute('aria-hidden', 'true');
    description.append(dot, document.createTextNode(value));
    row.append(node('dt', name), description);
    return row;
}

// Recheck login when Back restores a page from the browser's memory cache.
window.addEventListener('pageshow', (event) => {
    if (event.persisted) window.location.reload();
});

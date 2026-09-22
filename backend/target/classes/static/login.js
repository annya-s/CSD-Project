import { $, api, jsonPost, showMessage } from './api.js';

let registering = false;

function loginDestination() {
    const next = new URLSearchParams(window.location.search).get('next');
    const allowedPages = ['/dashboard.html', '/crop-details.html', '/new-entry.html'];
    try {
        const destination = new URL(next || '/dashboard.html', window.location.origin);
        if (destination.origin === window.location.origin && allowedPages.includes(destination.pathname)) {
            return destination.pathname + destination.search;
        }
    } catch {
        // Invalid links return to the dashboard instead of leaving this website.
    }
    return '/dashboard.html';
}

function setRegistrationMode(enabled) {
    registering = enabled;
    $('login-title').textContent = enabled ? 'Start your growing season.' : 'Let’s get you back in the field.';
    $('auth-description').textContent = enabled ? 'Create an account for your farm.' : 'Log in to your farm overview.';
    $('display-name-field').hidden = !enabled;
    $('display-name').required = enabled;
    $('password').minLength = enabled ? 10 : 1;
    $('password').autocomplete = enabled ? 'new-password' : 'current-password';
    $('password-hint').hidden = !enabled;
    $('auth-submit').textContent = enabled ? 'Create account' : 'Log in';
    $('auth-toggle').textContent = enabled ? 'Already registered? Log in' : 'Create an account';
    showMessage('auth-error');
}

$('auth-toggle').addEventListener('click', () => setRegistrationMode(!registering));

$('auth-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    showMessage('auth-error');
    showMessage('global-message');
    const username = $('username').value;
    const password = $('password').value;
    $('auth-submit').disabled = true;
    $('auth-toggle').disabled = true;
    $('auth-submit').textContent = registering ? 'Creating account…' : 'Logging in…';

    try {
        if (registering) {
            await api('/api/auth/register', jsonPost({ username, password, displayName: $('display-name').value.trim() }));
            // If login fails, retry login rather than creating the account again.
            setRegistrationMode(false);
            $('auth-description').textContent = 'Your account is ready. Log in to continue.';
        }
        await api('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ username, password })
        });
        window.location.replace(loginDestination());
    } catch (error) {
        showMessage('auth-error', error.message);
    } finally {
        $('auth-submit').disabled = false;
        $('auth-toggle').disabled = false;
        $('auth-submit').textContent = registering ? 'Create account' : 'Log in';
    }
});

async function start() {
    try {
        await api('/api/auth/me');
        window.location.replace(loginDestination());
    } catch (error) {
        $('loading').hidden = true;
        $('login-page').hidden = false;
        if (error.status !== 401) {
            showMessage('global-message', 'Cannot reach the server. Check the connection and refresh to try again.');
        }
    }
}

start();

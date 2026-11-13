import { $, api, jsonPost, showMessage } from './api.js';

let authMode = 'login';

function loginDestination(role) {
    const next = new URLSearchParams(window.location.search).get('next');
    const farmerPages = ['/dashboard.html', '/crop-details.html', '/new-entry.html', '/account.html'];
    const adminPages = ['/admin.html', '/account.html'];
    const allowedPages = role === 'ADMIN' ? adminPages : farmerPages;
    const defaultPage = role === 'ADMIN' ? '/admin.html' : '/dashboard.html';
    try {
        const destination = new URL(next || defaultPage, window.location.origin);
        if (destination.origin === window.location.origin && allowedPages.includes(destination.pathname)) {
            return destination.pathname + destination.search;
        }
    } catch {
        // Invalid links return to the dashboard instead of leaving this website.
    }
    return defaultPage;
}

// Info notices are green, problems red; both share #global-message.
function notice(message, info = true) {
    showMessage('global-message', message);
    $('global-message').classList.toggle('is-info', info);
}

const COPY = {
    login: ['Welcome back', 'Sign in to Dome', '', 'Log in'],
    register: ['New to Dome', 'Create your account', 'Track every planting, its weather and its growth in one place.', 'Create account'],
    forgot: ['Account recovery', 'Reset your password', 'Enter your account email and we’ll send a 6-digit reset code.', 'Send reset code'],
    reset: ['Account recovery', 'Choose a new password', 'Enter the code from your email and a new password.', 'Reset password'],
    twofactor: ['Two-step sign in', 'Check your email', 'Enter the 6-digit code we emailed you.', 'Verify and sign in']
};

function setAuthMode(mode) {
    authMode = mode;
    const login = mode === 'login';
    const registering = mode === 'register';
    const forgot = mode === 'forgot';
    const resetting = mode === 'reset';
    const twoFactor = mode === 'twofactor';
    const [eyebrow, title, description, submit] = COPY[mode];

    $('auth-eyebrow').textContent = eyebrow;
    $('login-title').textContent = title;
    $('auth-description').textContent = description;
    $('auth-description').hidden = !description;
    $('auth-submit').textContent = submit;

    $('username-field').hidden = forgot || resetting || twoFactor;
    $('password-field').hidden = forgot || resetting || twoFactor;
    $('username-hint').hidden = !registering;
    $('password-hint').hidden = !registering;
    $('email-field').hidden = login || twoFactor;
    $('reset-token-field').hidden = !resetting;
    $('new-password-field').hidden = !resetting;
    $('confirm-password-field').hidden = !resetting;
    $('two-factor-field').hidden = !twoFactor;

    $('username').required = login || registering;
    $('password').required = login || registering;
    $('email').required = !(login || twoFactor);
    $('reset-token').required = resetting;
    $('new-password').required = resetting;
    $('confirm-password').required = resetting;
    $('two-factor-code').required = twoFactor;

    $('password').minLength = registering ? 10 : 1;
    $('password').autocomplete = registering ? 'new-password' : 'current-password';
    $('password').placeholder = registering ? 'At least 10 characters' : 'Your password';
    $('username').placeholder = registering ? 'e.g. river_farm' : 'Your username';

    $('auth-toggle').hidden = !(login || registering);
    $('auth-toggle').textContent = registering ? 'Already have an account? Sign in' : 'Sign up';
    $('forgot-password-link').hidden = !login;
    $('back-to-login').hidden = login || registering;

    showMessage('auth-error');
    if (twoFactor) $('two-factor-code').focus();
}

$('auth-toggle').addEventListener('click', () => {
    showMessage('global-message');
    setAuthMode(authMode === 'register' ? 'login' : 'register');
});
$('forgot-password-link').addEventListener('click', () => {
    showMessage('global-message');
    setAuthMode('forgot');
});
$('back-to-login').addEventListener('click', () => {
    showMessage('global-message');
    $('two-factor-code').value = '';
    setAuthMode('login');
});

// Show / hide password toggles.
document.querySelectorAll('[data-reveal]').forEach((button) => {
    button.addEventListener('click', () => {
        const input = $(button.dataset.reveal);
        const show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(show));
        button.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
        button.querySelector('use').setAttribute('href', `/icons.svg#${show ? 'i-eye-off' : 'i-eye'}`);
    });
});

function setBusy(busy) {
    for (const id of ['auth-submit', 'auth-toggle', 'forgot-password-link', 'back-to-login']) $(id).disabled = busy;
    $('auth-submit').classList.toggle('is-busy', busy);
}

$('auth-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    showMessage('auth-error');
    showMessage('global-message');
    const username = $('username').value.trim();
    const password = $('password').value;
    setBusy(true);

    try {
        if (authMode === 'register') {
            const email = $('email').value.trim();
            await api('/api/auth/register', jsonPost({ username, email, password }));
            setAuthMode('login');
            $('password').value = '';
            notice('Your account is ready. Sign in to continue.');
            return;
        }
        if (authMode === 'forgot') {
            const email = $('email').value.trim();
            await api('/api/auth/forgot-password', jsonPost({ email }));
            setAuthMode('reset');
            notice('If that email belongs to an account, a reset code is on its way.');
            return;
        }
        if (authMode === 'reset') {
            const email = $('email').value.trim();
            const resetToken = $('reset-token').value.trim();
            const newPassword = $('new-password').value;
            if (newPassword !== $('confirm-password').value) throw new Error('The passwords do not match.');
            await api('/api/auth/reset-password', jsonPost({ email, code: resetToken, password: newPassword }));
            $('auth-form').reset();
            setAuthMode('login');
            notice('Your password was reset. Sign in with the new password.');
            return;
        }

        const result = await api('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ username, password, code: $('two-factor-code').value.trim() })
        });
        if (result && result.twoFactorRequired) {
            setAuthMode('twofactor');
            return;
        }
        const me = await api('/api/auth/me');
        window.location.replace(loginDestination(me.role));
    } catch (error) {
        showMessage('auth-error', error.message);
    } finally {
        setBusy(false);
    }
});

async function start() {
    try {
        const me = await api('/api/auth/me');
        window.location.replace(loginDestination(me.role));
    } catch (error) {
        $('loading').hidden = true;
        $('login-page').hidden = false;
        if (error.status !== 401) {
            notice('Cannot reach the server. Check the connection and refresh to try again.', false);
        }
    }
}

setAuthMode('login');
start();

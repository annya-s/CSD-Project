import { $, api, jsonPost, showMessage } from './api.js';

let authMode = 'login';

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

function setAuthMode(mode) {
    authMode = mode;
    const login = mode === 'login';
    const registering = mode === 'register';
    const forgot = mode === 'forgot';
    const resetting = mode === 'reset';
    const twoFactor = mode === 'twofactor';

    $('login-title').textContent = 
        login ? 'Start your growing season.' :
        registering ? 'Let’s get you back in the field.' :
        forgot ? 'Find your way back in.' :
        twoFactor ? 'One more step.' :
        "Choose a new password.";
    $('auth-description').textContent =
        login ? 'Log in to your farm overview.' :
        registering ? 'Create an account for your farm.' :
        forgot ? 'Enter your email to receive a reset code.' :
        twoFactor ? 'Enter the 6-digit code emailed to you.' :
        'Enter the reset code and choose a new password.';    
    
    $('username-field').hidden = forgot || resetting || twoFactor;
    $('password-field').hidden = forgot || resetting || twoFactor;
    $('username-hint').hidden = forgot || resetting || twoFactor;
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
    
    $('auth-submit').textContent = login ? 'Log in': 
                                   registering ? 'Create account' : 
                                   forgot ? 'Send reset code' : 
                                   twoFactor ? 'Submit codw' :
                                   'Reset Password';

    $('auth-toggle').hidden = !(login || registering);
    $('forgot-password-link').hidden = !login;
    $('back-to-login').hidden = login;

    $('auth-toggle').textContent = registering ? 'Already registered? Log in' : 'Create an account';

    showMessage('auth-error');
}

$('auth-toggle').addEventListener('click', () => {
    setAuthMode(authMode == 'register' ? 'login' : 'register');});
$('forgot-password-link').addEventListener('click', () => {
    setAuthMode('forgot');});
$('back-to-login').addEventListener('click', () => {
    setAuthMode('login');});

$('auth-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    showMessage('auth-error');
    showMessage('global-message');
    const username = $('username').value.trim();
    const password = $('password').value;
    $('auth-submit').disabled = true;
    $('auth-toggle').disabled = true;
    $('forgot-password-link').disabled = true;
    $('back-to-login').disabled = true;

    try {
        if (authMode == 'register') {
            const email = $('email').value.trim();
            await api('/api/auth/register', jsonPost({
                username, email, password}));
            setAuthMode('login');
            showMessage('global-message', 'Your account is ready. Log in to continue.');
            return;
        }
        if (authMode === 'forgot') {
            const email = $('email').value.trim();
            await api('/api/auth/forgot-password', jsonPost({ email }));
            setAuthMode('reset');
            showMessage('global-message', 'If that email belongs to an account, a reset code was sent.');
            return;
        }
        if (authMode === 'reset') {
            const email = $('email').value.trim();
            const resetToken = $('reset-token').value.trim();
            const newPassword = $('new-password').value;
            const confirmPassword = $('confirm-password').value;
            if (newPassword !== confirmPassword)
                throw new Error('The passwords do not match.');
            await api('/api/auth/reset-password', jsonPost({
                email, code: resetToken, password: newPassword}));
            showMessage('global-message', 'Your password was reset. You can now log in.');
            return;
        }

        const result = await api('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ username, password, 
                                        code: $('two-factor-code').value.trim() })
        });
        if (result && result.twoFactorRequired) {
            setAuthMode('twofactor');
            showMessage('global-message', 'We emailed a 6-digit code to your address.');
            return;
        }
        window.location.replace(loginDestination());
    } catch (error) {
        showMessage('auth-error', error.message);
    } finally {
        $('auth-submit').disabled = false;
        $('auth-toggle').disabled = false;
        $('forgot-password-link').disabled = false;
        $('back-to-login').disabled = false;
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

setAuthMode('login');
start();

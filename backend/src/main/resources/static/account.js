import { $, api, jsonPost, requireFarmer, showMessage, showPageError, setBusy, toast } from './api.js';

let account = null;

function updateAccountPage() {
    $('username').value = account.username;
    $('new-email').value = account.email;

    const verified = Boolean(account.emailVerified);
    $('email-status').textContent = verified ? 'Verified' : 'Unverified';
    $('email-status').className = `pill email-pill ${verified ? 'ok' : 'warn'}`;
    $('verify-email-section').hidden = verified;
    if (!verified) {
        $('verify-email-description').textContent =
            `Enter the 6-digit code sent to ${account.email}. Need a new one? Send another code.`;
    }

    const twoFactor = Boolean(account.twoFactorEnabled);
    $('two-factor-state').textContent = twoFactor ? 'Enabled' : 'Disabled';
    $('two-factor-dot').classList.toggle('ok', twoFactor);
    $('two-factor-status').textContent = !verified
        ? 'Verify your email to turn on two-factor authentication.'
        : twoFactor
            ? 'A one-time code is emailed to you each time you sign in.'
            : 'Turn on to require an emailed code when you sign in.';
    const toggle = $('two-factor-toggle');
    toggle.textContent = twoFactor ? 'Disable 2FA' : 'Enable 2FA';
    toggle.className = `btn btn-sm ${twoFactor ? 'btn-outline' : 'btn-primary'}`;
    toggle.dataset.enabled = String(twoFactor);
    toggle.disabled = !verified && !twoFactor; // the backend rejects enabling while unverified
}

async function reload() {
    account = await requireFarmer();
    updateAccountPage();
}

// Runs one card's action with busy state, inline error and a success toast.
async function run(button, errorId, action, success) {
    showMessage(errorId);
    setBusy(button, true);
    try {
        await action();
    } catch (error) {
        setBusy(button, false);
        if (error.status === 401) showPageError(error);
        else showMessage(errorId, error.message);
        return;
    }
    setBusy(button, false); // before reload, so re-rendered labels are not overwritten
    toast(success);
    try { await reload(); } catch (error) { showPageError(error); }
}

$('username-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const newUsername = $('username').value.trim();
    if (newUsername === account.username) {
        showMessage('username-error', 'That’s already your username.');
        return;
    }
    if (!/^[a-z0-9_]{3,40}$/.test(newUsername)) {
        showMessage('username-error', 'Use 3–40 lowercase letters, numbers or underscores.');
        return;
    }
    run($('update-username'), 'username-error',
        () => api('/api/auth/username', jsonPost({ newUsername })), 'Your username was updated.');
});

$('email-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const newEmail = $('new-email').value.trim();
    if (newEmail.toLowerCase() === account.email.toLowerCase()) {
        showMessage('email-error', 'That’s already your email address.');
        return;
    }
    run($('update-email'), 'email-error',
        () => api('/api/auth/email', jsonPost({ newEmail })), 'Email updated. Check your inbox for a verification code.');
});

$('verify-email').addEventListener('click', () => {
    const code = $('verification-code').value.trim();
    if (!/^\d{6}$/.test(code)) {
        showMessage('email-error', 'Enter the 6-digit code from your email.');
        return;
    }
    run($('verify-email'), 'email-error', async () => {
        await api('/api/auth/verify-email', jsonPost({ email: account.email, code }));
        $('verification-code').value = '';
    }, 'Your email is verified.');
});

$('resend-code').addEventListener('click', async () => {
    const button = $('resend-code');
    showMessage('email-error');
    setBusy(button, true, 'Sending…');
    try {
        await api('/api/auth/verification-code', jsonPost({}));
        toast('A new code is on its way. Earlier codes no longer work.');
        setBusy(button, false);
        // Prevent accidental repeat sends.
        button.disabled = true;
        setTimeout(() => { button.disabled = false; }, 30000);
    } catch (error) {
        setBusy(button, false);
        if (error.status === 401) showPageError(error); else showMessage('email-error', error.message);
    }
});

$('password-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const currentPassword = $('current-password').value;
    const newPassword = $('new-password').value;
    if (!currentPassword) return showMessage('password-error', 'Enter your current password.');
    if (newPassword.length < 10) return showMessage('password-error', 'The new password needs at least 10 characters.');
    if (newPassword !== $('confirm-password').value) return showMessage('password-error', 'The new passwords do not match.');
    run($('update-password'), 'password-error', async () => {
        try {
            await api('/api/auth/password', jsonPost({ currentPassword, newPassword }));
        } catch (error) {
            // The backend answers 400 with no message when the current password is wrong.
            if (error.status === 400) error.message = 'Your current password is incorrect.';
            throw error;
        }
        $('password-form').reset();
    }, 'Your password was updated.');
});

$('two-factor-toggle').addEventListener('click', () => {
    const enable = $('two-factor-toggle').dataset.enabled !== 'true';
    run($('two-factor-toggle'), 'two-factor-error',
        () => api('/api/auth/two-factor', jsonPost({ enabled: enable })),
        `Two-factor authentication is ${enable ? 'on' : 'off'}.`);
});

async function start() {
    try {
        await reload();
        $('loading').hidden = true;
        $('account-page').hidden = false;
    } catch (error) {
        showPageError(error);
    }
}

start();

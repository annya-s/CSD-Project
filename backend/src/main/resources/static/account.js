import { $, api, jsonPost, requireFarmer, showMessage } from './api.js';

let account = null;

function updateAccountPage() {
    $('username').value = account.username;
    $('email').textContent = account.email;

    const verified = Boolean(account.emailVerified);
    $('email-status').textContent = verified ? 'Verified' : 'Unverified';
    $('email-status').className = `status ${verified ? 'verified' : 'unverified'}`;
    $('verify-email-section').hidden = verified;
    if (!verified) {
        $('verify-email-description').textContent = `A 6-digit verification code was sent to ${account.email}.`;
    }
    $('password-section').hidden = false;

    const twoFactor = Boolean(account.twoFactorEnabled);
    $('two-factor-section').hidden = false;
    $('two-factor-status').textContent = 
        !verified ? 
            'Verify your email to enable two-factor authentication.'
            : twoFactor
                ? 'Two-factor authentication is on. A code is emailed when you log in.'
                : 'Two-factor authentication is off.';

    const toggle = $('two-factor-toggle');
    toggle.textContent = twoFactor ? 'Turn off' : 'Turn on';
    toggle.dataset.enabled = twoFactor;
    toggle.disabled = !verified; // greyed out until the email is verified
}

$('update-username').addEventListener('click', async () => {
    showMessage('username-error');
    showMessage('global-message');
    const newUsername = $('username').value.trim();
    if (newUsername === account.username) {
        showMessage('username-error', 'New username cannot be the same as your current username.' );
        return;
    }
    const button = $('update-username');
    button.disabled = true;
    try {
        await api('/api/auth/username', jsonPost({ newUsername }));
        showMessage('global-message', 'Your username was updated.');
        await start();
    } catch (error) {
        showMessage('username-error', error.message);
    } finally {
        button.disabled = false;
    }
});

$('update-email').addEventListener('click', async () => {
    showMessage('email-error');
    showMessage('global-message');
    const newEmail = $('new-email').value.trim();
    if (newEmail === account.email) {
        showMessage('email-error', 'New email cannot be the same as your current email.');
        return;
    }
    const button = $('update-email');
    button.disabled = true;
    try {
        await api('/api/auth/email', jsonPost({ newEmail }));
        showMessage('global-message', 'Your email was updated.');
        await start();
    } catch (error) {
        showMessage('email-error', error.message);
    } finally {
        button.disabled = false;
    }
});

$('verify-email').addEventListener('click', async () => {
    showMessage('global-message');
    try {
        await api('/api/auth/verify-email', jsonPost({ email: account.email, code: $('verification-code').value.trim() }));
        showMessage('global-message', 'Your email was verified.');
        await start();
    } catch (error) {
        showMessage('global-message', error.message);
    }
});

let resendInProgress = false;
$('resend-code').addEventListener('click', async () => {
    if (resendInProgress) return;
    const confirmed = confirm('Resend verification code? Any previous code will no longer work.');
    if (!confirmed) return;

    const button = $('resend-code');
    showMessage('global-message');
    resendInProgress = true;
    button.disabled = true;
    try {
        await api('/api/auth/verification-code', jsonPost({}));
        showMessage('global-message', 'A new verification code was sent. Check your email; it may take a moment.');
        // prevent accidental spam clicks
        setTimeout(() => { button.disabled = false; }, 30000);
    } catch (error) {
        showMessage('global-message', error.message);
        resendInProgress = false;
        button.disabled = false;
    }
});

$('password-form').addEventListener('submit', async event => {
    event.preventDefault();

    showMessage('password-error');
    showMessage('global-message');

    const currentPassword = $('current-password').value;
    const newPassword = $('new-password').value;
    const confirmPassword = $('confirm-password').value;

    if (newPassword !== confirmPassword) {
        showMessage('password-error', 'The new passwords do not match.');
        return;
    }
    $('update-password').disabled = true;
    try {
        await api('/api/auth/password', jsonPost({ currentPassword, newPassword }));
        showMessage('global-message', 'Your password was updated.');
        $('password-form').reset();
    } catch (error) {
        showMessage('password-error', error.message);
    } finally {
        $('update-password').disabled = false;
    }
});

$('two-factor-toggle').addEventListener('click', async () => {
    showMessage('global-message');

    const toggle = $('two-factor-toggle');
    const enable = toggle.dataset.enabled !== 'true';

    toggle.disabled = true;
    try {
        await api('/api/auth/two-factor', jsonPost({ enabled: enable }));
        showMessage('global-message', 'Two-factor authentication is ' + (enable ? 'on.' : 'off.'));
        await start(); // reloads account, which re-renders the section
    } catch (error) {
        showMessage('global-message', error.message);
        toggle.disabled = false;
    }
});

async function start() {
    try {
        account = await requireFarmer();
        updateAccountPage();

        $('loading').hidden = true;
        $('account-page').hidden = false;
    } catch (error) {
        $('loading').hidden = true;
        showMessage('global-message', 'Cannot reach the server. Check the connection and refresh to try again.');
    }
}

start();
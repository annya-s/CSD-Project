import { $, api, requireFarmer, showMessage, showPageError, jsonPost, node, icon, toast, confirmDialog, setBusy } from './api.js';

let users = [];
const filters = { q: '', verified: '', twoFactor: '' };

const initials = (user) => (user.username || '?').split(/[_\s]+/).filter(Boolean).slice(0, 2)
    .map((part) => part[0]).join('').toUpperCase() || '?';

function cell(label, ...children) {
    const td = node('td');
    td.dataset.label = label;
    td.append(...children);
    return td;
}

function textAction(label, handler) {
    const button = node('button', label, 'text-link');
    button.type = 'button';
    button.addEventListener('click', () => handler(button));
    return button;
}

function renderUsers() {
    const q = filters.q.toLowerCase();
    const visible = users.filter((user) =>
        (!q || user.username.toLowerCase().includes(q) || (user.email || '').toLowerCase().includes(q)) &&
        (!filters.verified || (filters.verified === 'yes') === Boolean(user.emailVerified)) &&
        (!filters.twoFactor || (filters.twoFactor === 'yes') === Boolean(user.twoFactorEnabled))
    );

    $('admin-status').textContent = `${users.length} active ${users.length === 1 ? 'account' : 'accounts'} across your Dome workspace.`;
    $('users-empty').hidden = visible.length !== 0;
    $('users-empty').textContent = users.length ? 'No accounts match these filters.' : 'No farmer accounts yet.';

    $('users-body').replaceChildren(...visible.map((user) => {
        const row = node('tr');

        const who = node('div', undefined, 'user-cell');
        const text = node('div');
        text.append(node('b', user.username), node('span', user.email));
        who.append(node('span', initials(user), 'avatar'), text);

        const verification = node('div', undefined, 'status-cell');
        verification.append(node('span', user.emailVerified ? 'Verified' : 'Unverified', `pill ${user.emailVerified ? 'ok' : 'warn'}`));
        if (user.emailVerified) verification.append(textAction('Unverify', (button) => unverifyEmail(user, button)));

        const twoFactor = node('div', undefined, 'status-cell');
        twoFactor.append(node('span', user.twoFactorEnabled ? 'Enabled' : 'Disabled', `pill ${user.twoFactorEnabled ? 'ok' : ''}`));
        if (user.twoFactorEnabled) twoFactor.append(textAction('Disable', (button) => disable2fa(user, button)));

        const password = node('input', undefined, 'input pw-input');
        password.type = 'password';
        password.placeholder = 'New password';
        password.autocomplete = 'new-password';
        password.minLength = 10;
        password.maxLength = 128;
        password.setAttribute('aria-label', `New password for ${user.username}`);

        const actions = node('div', undefined, 'row-actions');
        const change = node('button', 'Change password', 'btn btn-outline');
        change.type = 'button';
        change.addEventListener('click', () => changePassword(user, password, change));
        password.addEventListener('keydown', (event) => { if (event.key === 'Enter') change.click(); });
        const remove = node('button', undefined, 'icon-btn danger');
        remove.type = 'button';
        remove.setAttribute('aria-label', `Delete ${user.username}`);
        remove.title = 'Delete user';
        remove.append(icon('trash-icon'));
        remove.addEventListener('click', () => deleteUser(user, remove));
        actions.append(change, remove);

        row.append(cell('User', who), cell('Verification', verification), cell('2FA', twoFactor),
            cell('New password', password), cell('Actions', actions));
        return row;
    }));
}

async function loadUsers() {
    users = await api('/api/admin/users');
    renderUsers();
}

// Confirm, call the API, re-fetch the list and report the outcome.
async function act(button, confirmation, request, success) {
    showMessage('global-message');
    if (!(await confirmDialog(confirmation))) return false;
    setBusy(button, true);
    try {
        await request();
        await loadUsers();
        toast(success);
        return true;
    } catch (error) {
        if (error.status === 401) showPageError(error); else toast(error.message, 'error');
        if (button.isConnected) setBusy(button, false);
        return false;
    }
}

const unverifyEmail = (user, button) => act(button,
    { title: `Unverify ${user.username}?`, message: 'Their email will need verifying again, and two-factor authentication will also be turned off.', confirm: 'Unverify' },
    () => api(`/api/admin/users/${user.id}/unverify`, { method: 'POST' }),
    `Email verification removed for ${user.username}.`);

const disable2fa = (user, button) => act(button,
    { title: `Disable 2FA for ${user.username}?`, message: 'They will sign in with just their password until they turn it back on.', confirm: 'Disable 2FA' },
    () => api(`/api/admin/users/${user.id}/disable2fa`, { method: 'POST' }),
    `Two-factor authentication disabled for ${user.username}.`);

function changePassword(user, input, button) {
    if (input.value.length < 10) {
        input.setAttribute('aria-invalid', 'true');
        input.focus();
        toast('Passwords need at least 10 characters.', 'error');
        return;
    }
    input.removeAttribute('aria-invalid');
    const password = input.value;
    act(button,
        { title: `Change ${user.username}’s password?`, message: 'Their current password will stop working immediately.', confirm: 'Change password' },
        () => api(`/api/admin/users/${user.id}/password`, jsonPost({ password })),
        `Password updated for ${user.username}.`);
}

const deleteUser = (user, button) => act(button,
    { title: `Delete ${user.username}?`, message: 'Their account and all of their crop records will be permanently deleted. This cannot be undone.', confirm: 'Delete user', danger: true },
    () => api(`/api/admin/users/${user.id}`, { method: 'DELETE' }),
    `${user.username} was deleted.`);

$('user-search').addEventListener('input', (event) => { filters.q = event.target.value.trim(); renderUsers(); });
$('filter-verified').addEventListener('change', (event) => { filters.verified = event.target.value; renderUsers(); });
$('filter-2fa').addEventListener('change', (event) => { filters.twoFactor = event.target.value; renderUsers(); });

async function start() {
    try {
        await requireFarmer();
        await api('/api/admin/check');
        await loadUsers();
        $('loading').hidden = true;
        $('admin-page').hidden = false;
    } catch (error) {
        $('loading').hidden = true;
        if (error.status === 403) {
            showMessage('global-message', 'You do not have permission to access administration.');
            return;
        }
        showPageError(error);
    }
}

start();

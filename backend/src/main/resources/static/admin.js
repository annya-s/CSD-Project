import { $, api, requireFarmer, showMessage, jsonPost, node } from './api.js';

let users = [];

$('logout').addEventListener('click', async () => {
    const button = $('logout');
    button.disabled = true;
    showMessage('global-message');
    try {
        await api('/api/auth/logout', { method: 'POST' });
        window.location.replace('/login.html');
    } catch (error) {
        showMessage('global-message', error.message);
    } finally {
        button.disabled = false;
    }
});

function renderUsers() {
    const body = $('users-body');
    body.replaceChildren();

    for (const user of users) {
        const row = node('tr');

        const username = node('td', user.username);
        
        // email verif - admin can only disable
        const email = node('td', user.email);
        const emailCell = node('td');
        if (user.emailVerified) {
            emailCell.append(node('span', 'Verified'));
            const unverifyButton = node('button', 'Unverify');
            unverifyButton.type = 'button';
            unverifyButton.addEventListener('click', () => unverifyEmail(user, unverifyButton));
            emailCell.append(unverifyButton);
        } else {
            emailCell.append(node('span', 'Unverified'));
        }

        // 2fa - admin can only disable
        const twoFactorCell = node('td');
        if (user.twoFactorEnabled) {
            const turnOffButton = node('button', 'Turn off');
            turnOffButton.type = 'button';
            turnOffButton.addEventListener('click', () => disable2fa(user, turnOffButton));
            twoFactorCell.append(turnOffButton);
        } else if (user.emailVerified) {
            twoFactorCell.append(node('span', '2fa disabled'));
        } else {
            twoFactorCell.append(node('span', 'N.A.'));
        }

        // reset password
        const passwordCell = node('td');
        const passwordInput = node('input');
        passwordInput.type = 'password';
        passwordInput.placeholder = 'New password';
        passwordInput.minLength = 10;
        passwordCell.append(passwordInput);

        const actionCell = node('td');
        const passwordButton = node('button', 'Change password');
        passwordButton.type = 'button';
        passwordButton.addEventListener('click', () => changePassword(user, passwordInput, passwordButton));

        const deleteButton = node('button');
        deleteButton.type = 'button';
        deleteButton.className = 'icon-button danger';
        deleteButton.title = 'Delete user';
        deleteButton.setAttribute('aria-label', `Delete ${user.username}`);
        deleteButton.innerHTML = `<svg width="18" height="18"> <use href="/icons.svg#trash-icon"></use> </svg>`;
        deleteButton.addEventListener('click', () => deleteUser(user, deleteButton));
        actionCell.append(passwordButton, deleteButton);

        row.append(username, email, emailCell, twoFactorCell, passwordCell, actionCell);

        body.append(row);
    }
}

async function loadUsers() {
    users = await api('/api/admin/users');
    renderUsers();
}
async function unverifyEmail(user, button) {
    showMessage('global-message');
    const confirmed = confirm(`Unverify ${user.username}? 2fa will also be disabled.`);
    if (!confirmed) return;

    button.disabled = true;
    try {
        await api(`/api/admin/users/${user.id}/unverify`, { method: 'POST' });
        user.emailVerified = false;
        user.twoFactorEnabled = false;
        renderUsers();
        showMessage('global-message', `Email verification disabled for ${user.username}.`);
    } catch (error) {
        showMessage('global-message', error.message);
        button.disabled = false;
    }
}
async function disable2fa(user, button) {
    showMessage('global-message');
    const confirmed = confirm(`Disable 2fa for ${user.username}?`);
    if (!confirmed) return;

    button.disabled = true;
    try {
        await api(`/api/admin/users/${user.id}/disable2fa`, { method: 'POST' });
        user.twoFactorEnabled = false;
        renderUsers();
        showMessage('global-message', `Two-factor authentication disabled for ${user.username}.`);
    } catch (error) {
        showMessage('global-message', error.message);
        button.disabled = false;
    }
}
async function changePassword(user, input, button) {
    showMessage('global-message');
    const password = input.value;
    if (password.length < 10) {
        showMessage('global-message', 'Password must be at least 10 characters.');
        return;
    }
    const confirmed = confirm(`Change password for ${user.username}?`);
    if (!confirmed) return;

    button.disabled = true;
    try {
        await api(`/api/admin/users/${user.id}/password`, jsonPost({ password }));
        input.value = '';
        showMessage('global-message', `Password updated for ${user.username}.`);
    } catch (error) {
        showMessage('global-message', error.message);
    } finally {
        button.disabled = false;
    }
}
async function deleteUser(user, button) {
    showMessage('global-message');
    const confirmed = confirm(`Delete ${user.username}? All related data will also be deleted.`);
    if (!confirmed) return;
    button.disabled = true;
    try {
        await api(`/api/admin/users/${user.id}`, { method: 'DELETE' });
        users = users.filter(existing => existing.id !== user.id);
        renderUsers();
        showMessage('global-message', `${user.username} was deleted.`);
    } catch (error) {
        showMessage('global-message', error.message);
        button.disabled = false;
    }
}

async function start() {
    try {
        await requireFarmer();
        const admin = await api('/api/admin/check');
        $('welcome').textContent = `Welcome, ${admin.username}`;
        $('admin-status').textContent = 'Administrator access confirmed.';
        $('admin-page').hidden = false;

        await loadUsers();
        $('users-section').hidden = false;

    } catch (error) {
        if (error.status === 403) {
            showMessage('global-message', 'You do not have permission to access administration.');
            return;
        }
        showMessage('global-message', error.message);
    } finally {
        $('loading').hidden = true;
    }
}

start();
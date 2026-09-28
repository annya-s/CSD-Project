import { $, api, jsonPost, showMessage } from './api.js';

$('verify-form').addEventListener('submit', async event => {
    event.preventDefault();

    showMessage('verify-error');

    const identifier = $('identifier').value.trim();
    const code = $('code').value.trim();

    const body = identifier.includes('@')
        ? { username: null, email: identifier, code }
        : { username: identifier, email: null, code };

    $('verify-submit').disabled = true;
    $('verify-submit').textContent = 'Verifying…';

    try {
        await api(
            '/api/auth/verify-email',
            jsonPost(body));

        window.location.replace('/login.html');
    } catch (error) {
        showMessage(
            'verify-error',
            error.message || 'Verification failed.');
    } finally {
        $('verify-submit').disabled = false;
        $('verify-submit').textContent = 'Verify email';
    }
});
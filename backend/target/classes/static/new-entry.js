import { $, api, jsonPost, requireFarmer, showPageError, showMessage, node } from './api.js';

function localDateTime() {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 19);
}

$('planted-at').addEventListener('focus', () => {
    $('planted-at').max = localDateTime();
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
        window.location.assign('/dashboard.html');
    } catch (error) {
        if (error.status === 401) {
            showPageError(error);
        } else {
            showMessage('entry-error', error.message);
        }
    } finally {
        $('entry-submit').disabled = false;
        $('entry-submit').textContent = 'Save crop entry';
    }
});

async function start() {
    try {
        await requireFarmer();
        const cropTypes = await api('/api/crop-types');
        for (const crop of cropTypes) {
            const option = node('option', crop.name);
            option.value = crop.code;
            $('crop-type').append(option);
        }
        $('planted-at').max = localDateTime();
        $('planted-at').value = localDateTime();
        $('loading').hidden = true;
        $('entry-page').hidden = false;
    } catch (error) {
        showPageError(error);
    }
}

start();

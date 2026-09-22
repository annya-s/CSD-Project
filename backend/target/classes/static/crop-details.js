import { $, api, requireFarmer, showPageError, node, formatDate, metric } from './api.js';

// Only this file handles the crop-details screen.
function renderDetails(data, cropTypes) {
    const name = cropTypes.find((type) => type.code === data.entry.cropType)?.name || data.entry.cropType;
    document.title = `Dome · ${name}`;
    $('detail-title').textContent = name;
    $('detail-date').textContent = `Planted ${formatDate(data.entry.plantedAt)}`;
    $('detail-latitude').textContent = data.entry.latitude;
    $('detail-longitude').textContent = data.entry.longitude;
    $('detail-note').textContent = data.note;
    $('readings').replaceChildren();
    for (const reading of data.readings) {
        const value = reading.value === null ? 'Not measured' : `${reading.value} ${reading.unit || ''}`;
        $('readings').append(metric(reading.name, value));
    }
    $('actions').replaceChildren(...data.recommendedActions.map((action) => node('li', action)));
}

async function start() {
    try {
        await requireFarmer();
        // The dashboard puts this planting's identity in the page URL.
        const params = new URLSearchParams(window.location.search);
        const type = params.get('type');
        const plantedAt = params.get('plantedAt');
        if (!type || !plantedAt) {
            throw new Error('That crop link is incomplete. Use Overview to select a crop.');
        }
        const [details, cropTypes] = await Promise.all([
            api(`/api/crops/${encodeURIComponent(type)}?${new URLSearchParams({ plantedAt })}`),
            api('/api/crop-types')
        ]);
        renderDetails(details, cropTypes);
        $('loading').hidden = true;
        $('detail-page').hidden = false;
    } catch (error) {
        showPageError(error);
    }
}

start();

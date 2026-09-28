import { $, api, requireFarmer, showPageError, node, formatDate, readingMetric, weatherCaption, refreshPeriodically } from './api.js';

// Only this file handles the crop-details screen.
function renderDetails(data, cropTypes) {
    const name = cropTypes.find((type) => type.code === data.entry.cropType)?.name || data.entry.cropType;
    document.title = `Dome · ${name}`;
    $('detail-title').textContent = name;
    $('detail-date').textContent = `Planted ${formatDate(data.entry.plantedAt)}`;
    $('detail-latitude').textContent = data.entry.latitude;
    $('detail-longitude').textContent = data.entry.longitude;
    $('detail-note').textContent = data.note;
    $('weather-date').textContent = weatherCaption(data.weather, data.weatherSource, data.weatherMessage);
    $('attention').hidden = !data.needsAttention;
    $('readings').replaceChildren();
    for (const reading of data.readings) {
        $('readings').append(readingMetric(reading, true));
    }
    $('actions').replaceChildren(...data.recommendedActions.map((action) => node('li', action)));
    $('issues').replaceChildren(...data.issues.map((issue) => node('li', issue)));
    $('no-issues').hidden = data.issues.length !== 0;
    $('no-issues').textContent = data.readings.some((reading) => reading.referenceRange && reading.value !== null)
        ? 'No attention flags in the available readings. This is not a complete crop health assessment.'
        : 'Not assessed. Daily weather readings are needed.';
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
        const detailsUrl = `/api/crops/${encodeURIComponent(type)}?${new URLSearchParams({ plantedAt })}`;
        const [details, cropTypes] = await Promise.all([
            api(detailsUrl),
            api('/api/crop-types')
        ]);
        renderDetails(details, cropTypes);
        $('loading').hidden = true;
        $('detail-page').hidden = false;
        refreshPeriodically(async () => renderDetails(await api(detailsUrl), cropTypes));
    } catch (error) {
        showPageError(error);
    }
}

start();

import { $, api, requireFarmer, showPageError, node, formatDate, metric } from './api.js';

function renderDashboard(data, cropTypes) {
    $('summary').textContent = data.summary;
    $('crop-grid').replaceChildren();
    $('empty-state').hidden = data.crops.length !== 0;

    for (const crop of data.crops) {
        const card = node('a', undefined, 'crop-card');
        const params = new URLSearchParams({ type: crop.cropType, plantedAt: crop.plantedAt });
        card.href = `/crop-details.html?${params}`;
        const name = cropTypes.find((type) => type.code === crop.cropType)?.name || crop.cropType;
        card.append(node('h2', name));
        const date = node('time', `Planted ${formatDate(crop.plantedAt)}`);
        date.dateTime = crop.plantedAt;
        card.append(date);
        const readings = node('dl');
        ['Water', 'Soil moisture', 'UV exposure', 'Fertilizer'].forEach((name) => readings.append(metric(name)));
        card.append(readings, node('span', 'View planting details →', 'card-footer'));
        $('crop-grid').append(card);
    }
}

async function start() {
    try {
        await requireFarmer();
        const [dashboard, cropTypes] = await Promise.all([
            api('/api/dashboard'),
            api('/api/crop-types')
        ]);
        renderDashboard(dashboard, cropTypes);
        $('loading').hidden = true;
        $('dashboard-page').hidden = false;
    } catch (error) {
        showPageError(error);
    }
}

start();

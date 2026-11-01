import { $, api, requireFarmer, showPageError, node, formatDate, readingMetric, weatherCaption, refreshPeriodically } from './api.js';

function renderDashboard(data, cropTypes) {
    $('summary').textContent = data.summary;
    $('crop-grid').replaceChildren();
    $('empty-state').hidden = data.crops.length !== 0;

    for (const crop of data.crops) {
        // [ERD-ALIGNMENT CHANGE] Use a container for details and delete controls.
        const card = node('article', undefined, 'crop-card');

        const detailsUrl = '/crop-details.html?' + new URLSearchParams({ cycleId: crop.cropCycleId });
        const name = cropTypes.find((type) => type.code === crop.cropType)?.name || crop.cropType;
        card.append(node('h2', name));
        const date = node('time', `Planted ${formatDate(crop.plantedAt)}`);
        date.dateTime = crop.plantedAt;
        card.append(date);
        // [ERD-ALIGNMENT CHANGE] Show which parent FARMS row owns this crop cycle.
        card.append(node('p', crop.farmName, 'field-hint'));
        if (crop.needsAttention) card.append(node('p', 'Needs attention', 'attention-label'));
        card.append(node('p', weatherCaption(crop.weather, crop.weatherSource, crop.weatherMessage), 'field-hint'));
        const readings = node('dl');
        crop.readings.forEach((reading) => readings.append(readingMetric(reading)));
        card.append(readings);
        const details = node('a', 'View planting details →', 'card-footer');
        details.href = detailsUrl;
        card.append(details);
        // [ERD-ALIGNMENT ADDITION] Delete one cycle; the backend verifies farm ownership.
        const remove = node('button', 'Delete entry', 'delete-entry');
        remove.type = 'button';
        remove.addEventListener('click', async () => {
            if (!window.confirm('Delete this ' + name + ' entry? This cannot be undone.')) return;
            remove.disabled = true;
            try {
                await api('/api/crops/' + encodeURIComponent(crop.cropCycleId), { method: 'DELETE' });
                renderDashboard(await api('/api/dashboard'), cropTypes);
            } catch (error) {
                showPageError(error);
                remove.disabled = false;
            }
        });
        card.append(remove);
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
        refreshPeriodically(async () => renderDashboard(await api('/api/dashboard'), cropTypes));
    } catch (error) {
        showPageError(error);
    }
}

start();

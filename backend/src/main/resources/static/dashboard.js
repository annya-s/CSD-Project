import { $, api, requireFarmer, showPageError, node, formatDate, readingMetric, weatherCaption, refreshPeriodically } from './api.js';

const filters = { cropType: '', farm: '', health: '', date: '' };
let cropTypes = [];
let dashboardData;

function populate(select, values, label = (value) => value) {
    const placeholder = select.options[0];
    select.replaceChildren(placeholder);
    for (const value of values) {
        const option = node('option', label(value));
        option.value = value;
        select.append(option);
    }
}

function renderDashboard(data) {
    dashboardData = data;
    $('summary').textContent = data.summary;
    const crops = data.crops.filter((crop) =>
        (!filters.cropType || crop.cropType === filters.cropType) &&
        (!filters.farm || crop.farmName === filters.farm) &&
        (!filters.health || (filters.health === 'attention') === Boolean(crop.needsAttention))
    );
    if (filters.date) crops.sort((a, b) => String(a.plantedAt).localeCompare(String(b.plantedAt)) * (filters.date === 'earliest' ? 1 : -1));
    $('crop-grid').replaceChildren();
    $('empty-state').hidden = data.crops.length !== 0;
    $('filter-empty').hidden = crops.length !== 0 || data.crops.length === 0;

    for (const crop of crops) {
        const card = node('article', undefined, 'crop-card');
        const detailsUrl = '/crop-details.html?' + new URLSearchParams({ cycleId: crop.cropCycleId });
        const name = cropTypes.find((type) => type.code === crop.cropType)?.name || crop.cropType;
        card.append(node('h2', name));
        const date = node('time', `Planted ${formatDate(crop.plantedAt)}`);
        date.dateTime = crop.plantedAt;
        card.append(date, node('p', crop.farmName, 'field-hint'));
        if (crop.needsAttention) card.append(node('p', 'Needs attention', 'attention-label'));
        const readings = node('dl');
        crop.readings.forEach((reading) => readings.append(readingMetric(reading)));
        card.append(readings);
        const details = node('a', '', 'card-link');
        details.href = detailsUrl;
        card.append(details);
        const remove = node('button', 'Delete entry', 'delete-entry');
        remove.type = 'button';
        remove.addEventListener('click', async () => {
            if (!window.confirm('Delete this ' + name + ' entry? This cannot be undone.')) return;
            remove.disabled = true;
            try {
                await api('/api/crops/' + encodeURIComponent(crop.cropCycleId), { method: 'DELETE' });
                renderDashboard(await api('/api/dashboard'));
            } catch (error) { showPageError(error); remove.disabled = false; }
        });
        card.append(remove);
        $('crop-grid').append(card);
    }
}

async function start() {
    try {
        await requireFarmer();
        const [dashboard, types] = await Promise.all([api('/api/dashboard'), api('/api/crop-types')]);
        cropTypes = types;
        populate($('filter-crop-type'), types.map((type) => type.code), (code) => types.find((type) => type.code === code)?.name || code);
        populate($('filter-farm'), [...new Set(dashboard.crops.map((crop) => crop.farmName).filter(Boolean))].sort());
        for (const [id, key] of [['filter-crop-type', 'cropType'], ['filter-farm', 'farm'], ['filter-health', 'health'], ['filter-date', 'date']]) {
            $(id).addEventListener('change', (event) => { filters[key] = event.target.value; renderDashboard(dashboardData); });
        }
        $('clear-filters').addEventListener('click', () => {
            Object.keys(filters).forEach((key) => { filters[key] = ''; });
            ['filter-crop-type', 'filter-farm', 'filter-health', 'filter-date'].forEach((id) => { $(id).value = ''; });
            renderDashboard(dashboardData);
        });
        renderDashboard(dashboard);
        $('loading').hidden = true;
        $('dashboard-page').hidden = false;
        refreshPeriodically(async () => renderDashboard(await api('/api/dashboard')));
    } catch (error) { showPageError(error); }
}

start();

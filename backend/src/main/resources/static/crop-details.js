import { $, api, requireFarmer, showPageError, node, formatDate, readingMetric, weatherCaption } from './api.js';

// One reference photo for each supported crop.
const cropPhotos = {
    POTATO: {
        title: 'Potato plant .jpg',
        author: 'Arg12730',
        license: 'by-sa/4.0'
    },
    SUGAR_CANE: {
        title: 'Sugar Cane (2858291490).jpg',
        author: 'Cliff',
        license: 'by/2.0'
    },
    APPLE: {
        title: 'Apple tree 1.jpg',
        author: 'GerFes',
        license: null
    },
    RICE: {
        title: 'Rice plant.jpg',
        author: 'Douglas Paul Perkins',
        license: 'by/4.0'
    },
    WHEAT: {
        title: 'Field of wheat.jpg',
        author: 'Depressedlymotivated',
        license: 'by/4.0'
    },
    MAIZE: {
        title: 'Maize plant.jpg',
        author: 'Kenogenic',
        license: 'by-sa/4.0'
    },
    TOMATO: {
        title: 'Tomato plant (1).jpg',
        author: 'Russell Yarwood',
        license: 'by-sa/2.0'
    },
    CARROT: {
        title: 'Carrot harvest.jpg',
        author: 'woodleywonderworks',
        license: 'by/2.0'
    },
    LETTUCE: {
        title: 'Lettuce (390008184).jpg',
        author: 'Clinton & Charles Robertson',
        license: 'by/2.0'
    },
    SOYBEAN: {
        title: 'Soybean Field (9620817061).jpg',
        author: 'United Soybean Board',
        license: 'by/2.0'
    }
};

function renderCropPhoto(cropType, cropName) {
    const photo = cropPhotos[cropType];
    const image = $('crop-photo');
    const unavailable = $('crop-photo-unavailable');
    const credit = $('crop-photo-credit');

    if (!photo) {
        image.hidden = true;
        image.removeAttribute('src');
        unavailable.hidden = false;
        credit.replaceChildren();
        return;
    }

    const imagePath = `/images/crops/${cropType.toLowerCase()}.jpg`;

    // Weather refreshes should not reload the same photo.
    if (image.getAttribute('src') === imagePath) {
        return;
    }

    image.hidden = true;
    unavailable.hidden = true;
    image.alt = `Reference photograph of ${cropName}`;

    image.onload = () => {
        image.hidden = false;
        unavailable.hidden = true;
    };

    image.onerror = () => {
        image.hidden = true;
        unavailable.hidden = false;
    };

    const source = document.createElement('a');
    source.href = 'https://commons.wikimedia.org/wiki/File:'
        + encodeURIComponent(photo.title.replaceAll(' ', '_'));
    source.textContent = `${photo.title} — ${photo.author}`;
    source.target = '_blank';
    source.rel = 'noopener noreferrer';

    credit.replaceChildren(source);

    if (photo.license) {
        const license = document.createElement('a');
        license.href = `https://creativecommons.org/licenses/${photo.license}/`;
        license.textContent = `CC ${photo.license.replace('/', ' ').toUpperCase()}`;
        license.target = '_blank';
        license.rel = 'noopener noreferrer';
        credit.append(' · ', license);
    } else {
        credit.append(' · Public domain');
    }

    image.src = imagePath;
}

// Two significant figures for small readings (0.15 m³/m³), one decimal for larger ones (18.3 °C).
function formatReading(value) {
    return Math.abs(value) >= 10 ? value.toFixed(1) : String(Number(value.toPrecision(2)));
}

// The ML yield outlook: expected yield for the nearest training region, as t/ha and vs a normal year.
function renderYield(outlook) {
    $('yield-outlook').hidden = !outlook;
    if (!outlook) return;
    if (outlook.status === 'ML_ESTIMATE' && Number.isFinite(outlook.expectedYield)) {
        const change = Math.round(100 * (outlook.ratio - 1));
        const comparison = change === 0 ? 'about the same as a normal year'
            : `${Math.abs(change)}% ${change > 0 ? 'above' : 'below'} a normal year`;
        $('yield-result').textContent = `Expected yield: ${outlook.expectedYield.toFixed(1)} ${outlook.unit}, `
            + `${comparison}.`;
        $('yield-normal').textContent = `Normal yield for the nearest reference region (${outlook.region}): `
            + `${outlook.normalYield.toFixed(1)} ${outlook.unit}. Model: ${outlook.model}.`;
    } else {
        $('yield-result').textContent = outlook.message || 'Yield outlook unavailable.';
        $('yield-normal').textContent = '';
    }
    $('yield-note').textContent = outlook.status === 'ML_ESTIMATE' ? outlook.message : '';
}

// Only this file handles the crop-details screen.
function renderDetails(data, cropTypes) {
    const name = cropTypes.find((type) => type.code === data.entry.cropType)?.name || data.entry.cropType;
    $('run-prediction').textContent = `Run ${name.toLowerCase()} yield and biomass prediction`;
    const prediction = data.prediction;
    renderYield(prediction?.yieldOutlook);
    const comparison = $('biomass-comparison');
    const available = prediction?.status === 'ESTIMATE'
        && Number.isFinite(prediction.value) && Number.isFinite(prediction.referenceEstimate)
        && prediction.projectionDate;
    const hasTarget = Number.isFinite(prediction?.referenceEstimate) && prediction.projectionDate;
    comparison.hidden = !hasTarget;
    $('biomass-reference').textContent = hasTarget
        ? 'Full-growth target under assumed optimal conditions: ' + prediction.referenceEstimate.toFixed(1)
            + ' ' + prediction.unit + ' on ' + prediction.projectionDate + '.'
        : '';
    if (available) {
        const unit = prediction.unit;
        $('biomass-result').textContent = 'Predicted biomass at full growth if recent conditions continue: '
            + prediction.value.toFixed(1) + ' ' + unit + ' on ' + prediction.projectionDate + '.';
        const shortfall = prediction.referenceEstimate > 0
            ? 100 * (1 - prediction.value / prediction.referenceEstimate) : 0;
        $('biomass-gap').textContent = shortfall < 0.5
            ? 'This matches the full-growth target: recent weather is close to optimal.'
            : `This is ${shortfall.toFixed(0)}% below the full-growth target. What causes the gap:`;
        $('biomass-causes').replaceChildren(...(prediction.causes || [])
            .filter((cause) => cause.shortfallPercent >= 0.5)
            .map((cause) => node('li', `${cause.shortfallPercent.toFixed(0)}% from ${cause.factor.toLowerCase()} `
                + `${cause.direction} (${formatReading(cause.actual)} vs optimal `
                + `${formatReading(cause.optimal)} ${cause.unit})`)));
    } else {
        $('biomass-result').textContent = prediction?.message || 'Biomass estimate unavailable.';
        $('biomass-gap').textContent = '';
        $('biomass-causes').replaceChildren();
    }
    document.title = `Dome · ${name}`;
    $('detail-title').textContent = name;
    renderCropPhoto(data.entry.cropType, name);
    $('detail-date').textContent = `Planted ${formatDate(data.entry.plantedAt)}`;
    $('detail-latitude').textContent = data.entry.latitude;
    $('detail-longitude').textContent = data.entry.longitude;
    $('detail-note').textContent = [data.note,
        available ? prediction.message : null].filter(Boolean).join(' ');
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
        // [ERD-ALIGNMENT CHANGE] Detail lookup uses CROP_CYCLES primary key.
        const cycleId = params.get('cycleId');
        if (!cycleId) {
            throw new Error('That crop link is incomplete. Use Overview to select a crop.');
        }
        const detailsUrl = '/api/crops/' + encodeURIComponent(cycleId);
        const [details, cropTypes] = await Promise.all([
            api(detailsUrl, { signal: AbortSignal.timeout(15000) }),
            api('/api/crop-types', { signal: AbortSignal.timeout(15000) })
        ]);
        renderDetails(details, cropTypes);
        let refreshing = false;
        async function refreshPrediction(manual = false) {
            if (refreshing) return;
            refreshing = true;
            const button = $('run-prediction');
            const status = $('prediction-run-status');
            button.disabled = true;
            if (manual) status.textContent = 'Checking saved weather, refreshing it if needed, and running your crop model…';
            try {
                // The prediction endpoint checks ownership, refreshes stale averages,
                // reads them from the database and runs this planting's model.
                const prediction = await api(detailsUrl + '/prediction', {
                    method: 'POST', signal: AbortSignal.timeout(60000)
                });
                const updated = { ...details, prediction };
                renderDetails(updated, cropTypes);
                if (manual) status.textContent = updated.prediction?.status === 'ESTIMATE'
                    ? 'Prediction updated. Compare the estimate and target below for the same date.'
                    : 'Prediction unavailable. See the explanation below.';
            } catch (error) {
                if (!manual) throw error;
                status.textContent = 'Could not run the prediction. Previously displayed values have not been updated. Please try again.';
            } finally {
                refreshing = false;
                button.disabled = false;
            }
        }
        $('run-prediction').addEventListener('click', () => refreshPrediction(true));
        $('loading').hidden = true;
        $('detail-page').hidden = false;
    } catch (error) {
        $('loading').hidden = true;
        showPageError(error);
    }
}

start();

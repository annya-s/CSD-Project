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

// Only this file handles the crop-details screen.
function renderDetails(data, cropTypes) {
    const name = cropTypes.find((type) => type.code === data.entry.cropType)?.name || data.entry.cropType;
    $('run-prediction').textContent = `Run ${name.toLowerCase()} biomass prediction`;
    const prediction = data.prediction;
    const comparison = $('biomass-comparison');
    const available = prediction?.status === 'SYNTHETIC_DEMO'
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
        const difference = prediction.referenceEstimate - prediction.value;
        $('biomass-gap').textContent = 'Difference from full-growth target: '
            + (difference > 0 ? '+' : '') + difference.toFixed(1) + ' ' + unit + '.';
    } else {
        $('biomass-result').textContent = prediction?.message || 'Biomass estimate unavailable.';
        $('biomass-gap').textContent = '';
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
                if (manual) status.textContent = updated.prediction?.status === 'SYNTHETIC_DEMO'
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

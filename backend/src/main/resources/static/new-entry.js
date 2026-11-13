import { $, api, jsonPost, requireFarmer, showPageError, showMessage, node, setBusy, pinIcon, tileLayer, formatCoord } from './api.js';

let coords = null; // chosen on the map
let map;
let marker;

// Local calendar date as YYYY-MM-DD (the date input's format).
function today() {
    const now = new Date();
    return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function setLocation(latlng, pan = false) {
    coords = latlng;
    if (!marker) marker = window.L.marker(latlng, { icon: pinIcon(), keyboard: false }).addTo(map);
    else marker.setLatLng(latlng);
    if (pan) map.setView(latlng, Math.max(map.getZoom(), 14));
    $('coords-text').textContent = `${formatCoord(latlng.lat)}, ${formatCoord(latlng.lng)}`;
    $('coords').classList.add('is-set');
    if ($('entry-error').dataset.field === 'map') showMessage('entry-error');
}

function loadMap() {
    const world = window.L.latLngBounds(window.L.latLng(-90, -180), window.L.latLng(90, 180));
    map = window.L.map('map', { maxBounds: world, zoomControl: true }).setView([1.37, 103.83], 11);
    tileLayer().addTo(map);
    map.on('click', (event) => setLocation(event.latlng));
}

function fail(message, field, element) {
    showMessage('entry-error', message);
    $('entry-error').dataset.field = field;
    if (element) {
        element.setAttribute('aria-invalid', 'true');
        element.focus();
    }
}

document.querySelectorAll('#entry-form .input, #entry-form .select').forEach((element) => {
    element.addEventListener('input', () => element.removeAttribute('aria-invalid'));
});

$('locate').addEventListener('click', () => {
    if (!navigator.geolocation) {
        fail('Your browser cannot share its location. Click the map instead.', 'map');
        return;
    }
    const button = $('locate');
    setBusy(button, true, 'Locating…');
    navigator.geolocation.getCurrentPosition(
        (position) => {
            setBusy(button, false);
            setLocation(window.L.latLng(position.coords.latitude, position.coords.longitude), true);
        },
        () => {
            setBusy(button, false);
            fail('We couldn’t get your location. Allow location access, or click the map instead.', 'map');
        },
        { enableHighAccuracy: true, timeout: 10000 }
    );
});

$('entry-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    showMessage('entry-error');
    const farmName = $('farm-name').value.trim();
    const cropType = $('crop-type').value;
    const planted = $('planted-at').value;

    if (!farmName) return fail('Give this farm or field a name.', 'farm', $('farm-name'));
    if (!cropType) return fail('Choose which crop is planted.', 'crop', $('crop-type'));
    if (!planted) return fail('Choose the date it was planted.', 'date', $('planted-at'));
    if (planted > today()) return fail('The planting date can’t be in the future.', 'date', $('planted-at'));
    if (!coords) return fail('Drop a pin on the map to set the field location.', 'map');

    const button = $('entry-submit');
    setBusy(button, true, 'Saving…');
    try {
        await api('/api/crops', jsonPost({
            farmName,
            cropType,
            // Local midnight of the chosen day, sent with its UTC offset.
            plantedAt: new Date(`${planted}T00:00`).toISOString(),
            latitude: Number(coords.lat.toFixed(6)),
            longitude: Number(window.L.Util.wrapNum(coords.lng, [-180, 180], true).toFixed(6))
        }));
        window.location.assign('/dashboard.html');
    } catch (error) {
        setBusy(button, false);
        if (error.status === 401) showPageError(error);
        else fail(error.message, 'api');
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
        $('planted-at').max = today();
        $('planted-at').value = today();
        $('loading').hidden = true;
        $('entry-page').hidden = false;
        loadMap();
    } catch (error) {
        showPageError(error);
    }
}

start();

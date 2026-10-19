import { $, api, jsonPost, requireFarmer, showPageError, showMessage, node } from './api.js';

let coords = null; // For interactive map

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

    if (!coords) {
        showMessage("entry-error", "Please click on the map to select the location of your planted crops");
        return;
    }

    showMessage('entry-error');
    $('entry-submit').disabled = true;
    $('entry-submit').textContent = 'Saving…';
    try {
        await api('/api/crops', jsonPost({
            cropType: $('crop-type').value,
            plantedAt: new Date($('planted-at').value).toISOString(),
            latitude: Number((coords.lat).toFixed(5)), // Round to 5 d.p
            longitude: Number((coords.lng).toFixed(5))
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

function loadMap() {
    var mapMarker;

    var map = L.map("map").setView([40.19, -96.64], 3);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { // Base map
        maxZoom: 19,
        attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(map);

    L.tileLayer('https://stadiamaps.com/{z}/{x}/{y}{r}.png', { // Add country, city, small town names etc.
        maxZoom: 19,
        attribution: '&copy; <a href="https://stadiamaps.com" target="_blank">Stadia Maps</a> &copy; <a href="https://stamen.com" target="_blank">Stamen Design</a> &copy; <a href="https://openmaptiles.org" target="_blank">OpenMapTiles</a> &copy; <a href="https://openstreetmap.org">OpenStreetMap</a> contributors'
    }).addTo(map);
    
    map.on("click", function(e) {
        coords = e.latlng;

        if (!mapMarker) {
            mapMarker = L.marker(coords).addTo(map);
        } else {
            mapMarker.setLatLng(coords);
        }
    })
}

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
        loadMap();
    } catch (error) {
        showPageError(error);
    }
}

start();

// Expense map with Leaflet + OpenStreetMap

const MAP_CENTER = [-12.0464, -77.0428]; // [latitud, longitud]
const MAP_ZOOM = 11;
const OSM_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

let map;
let clusterGroup;
let mapReady = false;
let pendingData = null;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    const hoy = todayProfileISO();
    document.getElementById('mapFrom').max = hoy;
    document.getElementById('mapTo').max = hoy;

    await loadCategories();
    initMap();
    document.getElementById('mapFilterForm').addEventListener('submit', (event) => {
        event.preventDefault();
        loadMapData();
    });
});

async function loadCategories() {
    try {
        const categories = await api.get('/categories');
        const select = document.getElementById('mapCategory');
        select.innerHTML = '<option value="">Todas las categorías</option>' +
            categories.map(c => `<option value="${c.id}">${escapeHtml(c.nombre)}</option>`).join('');
    } catch (err) {
        showMapError('No se pudieron cargar las categorías: ' + err.message);
    }
}

function initMap() {
    if (typeof L === 'undefined') {
        document.getElementById('mapLoading').classList.add('d-none');
        showMapError('No se pudo cargar Leaflet. Revisa tu conexión y recarga la página.');
        return;
    }

    map = L.map('expenseMap', {
        center: MAP_CENTER,
        zoom: MAP_ZOOM,
        zoomControl: true
    });

    const tiles = L.tileLayer(OSM_URL, {
        maxZoom: 19,
        attribution: OSM_ATTRIBUTION
    }).addTo(map);

    tiles.on('load', () => {
        document.getElementById('mapLoading').classList.add('d-none');
    });

    let tileErrors = 0;
    tiles.on('tileerror', () => {
        tileErrors++;
        document.getElementById('mapLoading').classList.add('d-none');
        if (tileErrors === 6) {
            showMapError('No se pudieron cargar los tiles del mapa. Revisa tu conexión.');
        }
    });

    if (typeof L.markerClusterGroup === 'function') {
        clusterGroup = L.markerClusterGroup({
            maxClusterRadius: 55,
            showCoverageOnHover: false,
            spiderfyOnMaxZoom: true
        });
        map.addLayer(clusterGroup);
    } else {
        clusterGroup = L.layerGroup().addTo(map);
    }

    map.whenReady(() => {
        mapReady = true;
        document.getElementById('mapLoading').classList.add('d-none');
        setTimeout(() => map.invalidateSize(), 200);
        if (pendingData) {
            applyData(pendingData);
            pendingData = null;
        } else {
            loadMapData();
        }
    });
}

async function loadMapData() {
    const errorBox = document.getElementById('mapError');
    errorBox.classList.add('d-none');
    document.getElementById('mapLoading').classList.remove('d-none');

    const params = new URLSearchParams();
    const from = document.getElementById('mapFrom').value;
    const to = document.getElementById('mapTo').value;
    const categoryId = document.getElementById('mapCategory').value;
    const q = document.getElementById('mapText').value.trim();

    if (from) params.set('desde', from);
    if (to) params.set('hasta', to);
    if (categoryId) params.set('categoryId', categoryId);
    if (q) params.set('q', q);

    try {
        const items = await api.get('/expenses/map?' + params.toString());
        if (!mapReady) {
            pendingData = items;
            return;
        }
        applyData(items);
    } catch (err) {
        showMapError('No se pudieron cargar los gastos: ' + err.message);
    } finally {
        document.getElementById('mapLoading').classList.add('d-none');
    }
}

function applyData(items) {
    const gastos = (items || []).filter(e => e.latitud != null && e.longitud != null);
    clusterGroup.clearLayers();

    gastos.forEach(e => {
        const marker = L.marker([e.latitud, e.longitud], {
            icon: expenseIcon(e.categoryColor || '#6f6a61')
        });

        marker.bindPopup(`
            <div class="map-popup">
                <div class="fw-semibold mb-1">${escapeHtml(e.descripcion || 'Gasto')}</div>
                <div class="fs-5 fw-bold text-primary mb-1">${formatMoney(e.monto)}</div>
                <div class="small text-muted mb-1">
                    <i class="bi bi-calendar3"></i> ${formatDate(e.fecha)}
                    ${e.categoryNombre ? ' · <i class="bi bi-tag"></i> ' + escapeHtml(e.categoryNombre) : ''}
                </div>
                <div class="small">
                    <i class="bi bi-geo-alt"></i> ${escapeHtml(e.direccion || 'Sin dirección')}
                </div>
            </div>
        `, { maxWidth: 290 });

        clusterGroup.addLayer(marker);
    });

    renderSummary(items, gastos);
    fitToData(gastos);
}

function expenseIcon(color) {
    return L.divIcon({
        className: 'expense-map-marker',
        html: `<span style="background-color:${escapeHtml(color)}"></span>`,
        iconSize: [18, 18],
        iconAnchor: [9, 9],
        popupAnchor: [0, -12]
    });
}

function renderSummary(items, gastos) {
    const total = (items || []).reduce((sum, e) => sum + Number(e.monto || 0), 0);
    document.getElementById('mapTotal').textContent = formatMoney(total);
    document.getElementById('mapCount').textContent = gastos.length === 1
        ? '1 gasto con ubicación'
        : gastos.length + ' gastos con ubicación';
    document.getElementById('mapSummary').classList.toggle('d-none', gastos.length === 0);
    document.getElementById('mapEmpty').classList.toggle('d-none', gastos.length > 0);
    document.getElementById('mapLimitNote').classList.toggle('d-none', gastos.length < 5000);
}

function fitToData(gastos) {
    if (!gastos.length) return;

    if (gastos.length === 1) {
        map.setView([gastos[0].latitud, gastos[0].longitud], 14);
        return;
    }

    if (typeof clusterGroup.getBounds === 'function') {
        map.fitBounds(clusterGroup.getBounds().pad(0.15));
    } else {
        const bounds = L.latLngBounds(gastos.map(e => [e.latitud, e.longitud]));
        map.fitBounds(bounds.pad(0.15));
    }
}

function clearMapFilters() {
    document.getElementById('mapFilterForm').reset();
    loadMapData();
}

function showMapError(message) {
    const box = document.getElementById('mapError');
    box.textContent = message;
    box.classList.remove('d-none');
}

// Expense map

const MAP_STYLE = 'https://tiles.openfreemap.org/styles/liberty';
const MAP_CENTER = [-77.0428, -12.0464];
const MAP_ZOOM = 11;
const FALLBACK_STYLE = {
    version: 8,
    sources: {
        osm: {
            type: 'raster',
            tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
            tileSize: 256,
            attribution: '© OpenStreetMap contributors'
        }
    },
    layers: [
        { id: 'osm', type: 'raster', source: 'osm' }
    ]
};

let map;
let mapReady = false;
let pendingData = null;
let layersAdded = false;
let fallbackUsed = false;
let fallbackTimer;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    const hoy = todayLocalISO();
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
    if (typeof maplibregl === 'undefined') {
        showMapError('No se pudo cargar el motor del mapa. Revisa tu conexión y recarga la página.');
        document.getElementById('mapLoading').classList.add('d-none');
        return;
    }

    map = new maplibregl.Map({
        container: 'expenseMap',
        style: MAP_STYLE,
        center: MAP_CENTER,
        zoom: MAP_ZOOM
    });

    map.addControl(new maplibregl.NavigationControl(), 'top-right');
    map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-right');

    fallbackTimer = setTimeout(() => {
        if (!map.isStyleLoaded()) {
            usarMapaAlternativo();
        }
    }, 8000);

    map.on('load', () => {
        clearTimeout(fallbackTimer);
        document.getElementById('mapLoading').classList.add('d-none');
        mapReady = true;
        if (!layersAdded) {
            addLayers();
            layersAdded = true;
        }
        if (pendingData) {
            applyData(pendingData);
            pendingData = null;
        } else {
            loadMapData();
        }
    });

    map.on('error', (ev) => {
        console.error('Error en el mapa:', ev.error);
        if (!map.isStyleLoaded()) {
            usarMapaAlternativo();
        }
    });
}

function usarMapaAlternativo() {
    if (fallbackUsed || !map) return;
    fallbackUsed = true;
    console.warn('OpenFreeMap no cargó; usando OpenStreetMap como respaldo.');
    try {
        map.setStyle(FALLBACK_STYLE, { diff: false });
    } catch (err) {
        console.error('Tampoco se pudo cargar el mapa alternativo:', err);
        showMapError('No se pudo cargar el mapa. Revisa tu conexión e inténtalo de nuevo.');
        document.getElementById('mapLoading').classList.add('d-none');
    }
}

function addLayers() {
    map.addSource('expenses', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] },
        cluster: true,
        clusterMaxZoom: 14,
        clusterRadius: 55
    });

    map.addLayer({
        id: 'clusters',
        type: 'circle',
        source: 'expenses',
        filter: ['has', 'point_count'],
        paint: {
            'circle-color': [
                'step', ['get', 'point_count'],
                '#93c5fd', 10,
                '#60a5fa', 30,
                '#2563eb'
            ],
            'circle-radius': ['step', ['get', 'point_count'], 18, 10, 24, 30, 32],
            'circle-stroke-width': 3,
            'circle-stroke-color': '#ffffff',
            'circle-opacity': 0.9
        }
    });

    map.addLayer({
        id: 'cluster-count',
        type: 'symbol',
        source: 'expenses',
        filter: ['has', 'point_count'],
        layout: {
            'text-field': ['get', 'point_count_abbreviated'],
            'text-font': ['Noto Sans Regular'],
            'text-size': 12
        },
        paint: {
            'text-color': '#ffffff'
        }
    });

    map.addLayer({
        id: 'unclustered',
        type: 'circle',
        source: 'expenses',
        filter: ['!', ['has', 'point_count']],
        paint: {
            'circle-color': ['coalesce', ['get', 'color'], '#2563eb'],
            'circle-radius': [
                'interpolate', ['linear'], ['get', 'monto'],
                0, 6,
                100, 9,
                500, 12,
                2000, 16
            ],
            'circle-stroke-width': 2,
            'circle-stroke-color': '#ffffff',
            'circle-opacity': 0.92
        }
    });

    map.on('click', 'clusters', async (event) => {
        const feature = map.queryRenderedFeatures(event.point, { layers: ['clusters'] })[0];
        if (!feature) return;
        const clusterId = feature.properties.cluster_id;
        const zoom = await map.getSource('expenses').getClusterExpansionZoom(clusterId);
        map.easeTo({ center: feature.geometry.coordinates, zoom });
    });

    map.on('click', 'unclustered', (event) => {
        const feature = event.features[0];
        const p = feature.properties;
        const html = `
            <div class="map-popup">
                <div class="fw-semibold mb-1">${escapeHtml(p.descripcion || 'Gasto')}</div>
                <div class="fs-5 fw-bold text-primary mb-1">${formatMoney(p.monto)}</div>
                <div class="small text-muted mb-1">
                    <i class="bi bi-calendar3"></i> ${formatDate(p.fecha)}
                    ${p.categoria ? ' · <i class="bi bi-tag"></i> ' + escapeHtml(p.categoria) : ''}
                </div>
                <div class="small">
                    <i class="bi bi-geo-alt"></i> ${escapeHtml(p.direccion || 'Sin dirección')}
                </div>
            </div>
        `;
        new maplibregl.Popup({ maxWidth: '290px' })
            .setLngLat(feature.geometry.coordinates)
            .setHTML(html)
            .addTo(map);
    });

    map.on('mouseenter', 'clusters', () => { map.getCanvas().style.cursor = 'pointer'; });
    map.on('mouseleave', 'clusters', () => { map.getCanvas().style.cursor = ''; });
    map.on('mouseenter', 'unclustered', () => { map.getCanvas().style.cursor = 'pointer'; });
    map.on('mouseleave', 'unclustered', () => { map.getCanvas().style.cursor = ''; });
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
    const features = (items || [])
        .filter(e => e.latitud != null && e.longitud != null)
        .map(e => ({
            type: 'Feature',
            geometry: {
                type: 'Point',
                coordinates: [e.longitud, e.latitud]
            },
            properties: {
                id: e.id,
                monto: Number(e.monto),
                fecha: e.fecha,
                descripcion: e.descripcion || 'Gasto',
                categoria: e.categoryNombre || '',
                color: e.categoryColor || '#2563eb',
                direccion: e.direccion || '',
                ciudad: e.ciudad || '',
                pais: e.pais || ''
            }
        }));

    const source = map.getSource('expenses');
    if (source) {
        source.setData({ type: 'FeatureCollection', features });
    }

    renderSummary(items, features);
    fitToData(features);
}

function renderSummary(items, features) {
    const total = (items || []).reduce((sum, e) => sum + Number(e.monto || 0), 0);
    document.getElementById('mapTotal').textContent = formatMoney(total);
    document.getElementById('mapCount').textContent = features.length === 1
        ? '1 gasto con ubicación'
        : features.length + ' gastos con ubicación';
    document.getElementById('mapSummary').classList.toggle('d-none', features.length === 0);
    document.getElementById('mapEmpty').classList.toggle('d-none', features.length > 0);
    document.getElementById('mapLimitNote').classList.toggle('d-none', features.length < 5000);
}

function fitToData(features) {
    if (!features.length) return;

    if (features.length === 1) {
        map.easeTo({ center: features[0].geometry.coordinates, zoom: 14 });
        return;
    }

    const bounds = new maplibregl.LngLatBounds();
    features.forEach(f => bounds.extend(f.geometry.coordinates));
    map.fitBounds(bounds, { padding: 70, maxZoom: 14 });
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

function formatDate(iso) {
    if (!iso) return '-';
    const d = new Date(iso + 'T00:00:00');
    return d.toLocaleDateString('es-PE', { year: 'numeric', month: 'short', day: 'numeric' });
}

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

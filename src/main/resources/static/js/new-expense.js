// Create / edit expense with map

const MAP_CENTER = [-12.0464, -77.0428]; // [latitud, longitud]
const MAP_ZOOM = 12;
const OSM_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

let map;
let marker;
let selectedLocation = null;
let searchTimeout;
let searchResultsCache = [];
let savedPlaces = [];
let submitting = false;
let editingId = null;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    editingId = new URLSearchParams(window.location.search).get('id');
    const hoy = todayProfileISO();
    document.getElementById('fecha').value = hoy;
    document.getElementById('fecha').max = hoy;

    await loadCategories();
    await loadSavedPlaces();
    initMap();
    initSearch();

    if (editingId) {
        await loadExpense(editingId);
    }
});

// ===== Load categories =====
async function loadCategories() {
    try {
        const categories = await api.get('/categories');
        const select = document.getElementById('categoryId');
        select.innerHTML = '<option value="">Sin categoría</option>' +
            categories.map(c => `<option value="${c.id}">${escapeHtml(c.nombre)}</option>`).join('');
    } catch (err) {
        showError('No se pudieron cargar las categorías: ' + err.message);
    }
}

// ===== Load saved places =====
async function loadSavedPlaces() {
    const container = document.getElementById('savedPlacesChips');
    try {
        savedPlaces = await api.get('/saved-places');

        if (!savedPlaces || savedPlaces.length === 0) {
            container.innerHTML = '<span class="text-muted small">No tienes lugares guardados aún.</span>';
            return;
        }

        container.innerHTML = savedPlaces.map(p => `
            <button type="button" class="btn btn-sm btn-outline-primary"
                    onclick="useSavedPlace(${p.id})"
                    title="${escapeHtml(p.direccion || p.alias)}">
                <i class="bi bi-bookmark-star"></i> ${escapeHtml(p.alias)}
            </button>
        `).join('');
    } catch (err) {
        console.error('Error loading saved places:', err);
        container.innerHTML = '<span class="text-danger small">Error al cargar lugares guardados.</span>';
    }
}

async function loadExpense(id) {
    try {
        const expense = await api.get('/expenses/' + id);
        document.getElementById('pageTitle').textContent = 'Editar gasto';
        document.getElementById('pageSubtitle').textContent = 'Actualiza los datos y la ubicación del gasto.';
        document.getElementById('saveExpenseBtn').innerHTML = '<i class="bi bi-check2-circle"></i> Guardar cambios';

        document.getElementById('monto').value = expense.monto;
        document.getElementById('fecha').value = expense.fecha;
        document.getElementById('descripcion').value = expense.descripcion || '';
        document.getElementById('categoryId').value = expense.categoryId || '';

        if (expense.locationId) {
            selectedLocation = {
                locationId: expense.locationId,
                direccion: expense.locationDireccion,
                latitud: expense.locationLatitud,
                longitud: expense.locationLongitud,
                ciudad: expense.locationCiudad,
                pais: expense.locationPais,
                placeId: null
            };
            if (expense.locationLatitud != null && expense.locationLongitud != null) {
                setMarker(expense.locationLatitud, expense.locationLongitud);
            }
            updateLocationDisplay();
        }
    } catch (err) {
        showError('No se pudo cargar el gasto: ' + err.message);
        setTimeout(() => window.location.href = '/expenses.html', 1500);
    }
}

async function useSavedPlace(id) {
    const place = savedPlaces.find(p => p.id === id);
    if (!place) return;

    selectedLocation = {
        locationId: place.locationId,
        direccion: place.direccion,
        latitud: place.latitud,
        longitud: place.longitud,
        ciudad: place.ciudad,
        pais: place.pais,
        placeId: place.placeId
    };

    if (place.latitud != null && place.longitud != null) {
        setMarker(place.latitud, place.longitud);
    }
    updateLocationDisplay();

    try {
        await api.post('/saved-places/' + id + '/use', {});
    } catch (e) {
        // no hacer nada
    }
}

async function saveAsFavorite() {
    if (!selectedLocation) return;

    const alias = prompt('Nombre para este lugar (ej: Casa, Trabajo, Súper):');
    if (!alias || !alias.trim()) return;

    try {
        let locationId = selectedLocation.locationId;

        if (!locationId) {
            const loc = await api.post('/locations', {
                direccion: selectedLocation.direccion,
                latitud: selectedLocation.latitud,
                longitud: selectedLocation.longitud,
                ciudad: selectedLocation.ciudad,
                pais: selectedLocation.pais,
                placeId: selectedLocation.placeId
            });
            locationId = loc.id;
            selectedLocation.locationId = loc.id;
        }

        await api.post('/saved-places', { alias: alias.trim(), locationId });
        showSuccess('Lugar guardado como "' + alias.trim() + '"');
        await loadSavedPlaces();
    } catch (err) {
        showError(err.message);
    }
}

// ===== Map =====
function initMap() {
    if (typeof L === 'undefined') {
        document.getElementById('mapLoading').classList.add('d-none');
        showError('No se pudo cargar Leaflet. Revisa tu conexión y recarga la página.');
        return;
    }

    map = L.map('map', {
        center: MAP_CENTER,
        zoom: MAP_ZOOM,
        zoomControl: true
    });

    const tiles = L.tileLayer(OSM_URL, {
        maxZoom: 19,
        attribution: OSM_ATTRIBUTION
    }).addTo(map);

    tiles.on('load', () => document.getElementById('mapLoading').classList.add('d-none'));
    let tileErrors = 0;
    tiles.on('tileerror', () => {
        tileErrors++;
        document.getElementById('mapLoading').classList.add('d-none');
        if (tileErrors === 6) {
            showError('No se pudieron cargar los tiles del mapa. Revisa tu conexión.');
        }
    });

    map.whenReady(() => {
        document.getElementById('mapLoading').classList.add('d-none');
        setTimeout(() => map.invalidateSize(), 200);
    });

    map.on('click', async (ev) => {
        await reverseGeocode(ev.latlng.lat, ev.latlng.lng);
    });
}

function setMarker(lat, lon) {
    if (lat == null || lon == null || !map) return;

    const icon = L.divIcon({
        className: 'location-map-marker',
        html: '<i class="bi bi-geo-alt-fill"></i>',
        iconSize: [30, 30],
        iconAnchor: [15, 30]
    });

    if (marker) {
        marker.setLatLng([lat, lon]);
    } else {
        marker = L.marker([lat, lon], { draggable: true, icon }).addTo(map);
        marker.on('dragend', async () => {
            const pos = marker.getLatLng();
            await reverseGeocode(pos.lat, pos.lng);
        });
    }

    map.flyTo([lat, lon], 16, { duration: 0.7 });
}

// ===== Current location =====
async function useCurrentLocation() {
    if (!navigator.geolocation) {
        showError('Tu navegador no soporta geolocalización');
        return;
    }

    navigator.geolocation.getCurrentPosition(
        async (position) => {
            const { latitude, longitude } = position.coords;
            await reverseGeocode(latitude, longitude);
        },
        (error) => {
            showError('No se pudo obtener tu ubicación: ' + error.message);
        },
        { enableHighAccuracy: true, timeout: 10000 }
    );
}

// ===== Reverse geocoding =====
async function reverseGeocode(lat, lon) {
    try {
        const result = await api.get(`/geocode/reverse?lat=${lat}&lon=${lon}`);
        selectedLocation = {
            locationId: null,
            direccion: result.displayName,
            latitud: result.latitud,
            longitud: result.longitud,
            ciudad: result.ciudad,
            pais: result.pais,
            placeId: result.placeId
        };
        setMarker(result.latitud, result.longitud);
        updateLocationDisplay();
    } catch (err) {
        showError('No se pudo obtener la dirección. Puedes guardar el gasto sin ubicación.');
    }
}

// ===== Search =====
function initSearch() {
    const input = document.getElementById('searchPlace');
    const results = document.getElementById('searchResults');

    input.addEventListener('input', () => {
        clearTimeout(searchTimeout);
        const q = input.value.trim();
        if (q.length < 3) {
            results.classList.add('d-none');
            return;
        }
        searchTimeout = setTimeout(() => doSearch(q), 400);
    });

    document.addEventListener('click', (e) => {
        if (!results.contains(e.target) && e.target !== input) {
            results.classList.add('d-none');
        }
    });
}

async function doSearch(q) {
    const results = document.getElementById('searchResults');
    results.innerHTML = '<div class="list-group-item text-muted small">Buscando...</div>';
    results.classList.remove('d-none');

    try {
        const items = await api.get('/geocode/search?q=' + encodeURIComponent(q));
        searchResultsCache = items || [];

        if (searchResultsCache.length === 0) {
            results.innerHTML = '<div class="list-group-item text-muted small">Sin resultados</div>';
            return;
        }

        results.innerHTML = searchResultsCache.map((r, i) => `
            <button type="button" class="list-group-item list-group-item-action"
                    onclick="selectSearchResult(${i})">
                <div class="small fw-semibold">${escapeHtml(r.displayName)}</div>
                <div class="text-muted" style="font-size: 0.75rem;">
                    ${escapeHtml(r.ciudad || '')} ${r.pais ? '- ' + escapeHtml(r.pais) : ''}
                </div>
            </button>
        `).join('');
    } catch (err) {
        results.innerHTML = `<div class="list-group-item text-danger small">${escapeHtml(err.message)}</div>`;
    }
}

function selectSearchResult(index) {
    const result = searchResultsCache[index];
    if (!result) return;

    selectedLocation = {
        locationId: null,
        direccion: result.displayName,
        latitud: result.latitud,
        longitud: result.longitud,
        ciudad: result.ciudad,
        pais: result.pais,
        placeId: result.placeId
    };
    document.getElementById('searchResults').classList.add('d-none');
    document.getElementById('searchPlace').value = '';
    setMarker(result.latitud, result.longitud);
    updateLocationDisplay();
}

// ===== Location display =====
function updateLocationDisplay() {
    const box = document.getElementById('selectedLocation');
    const display = document.getElementById('locationDisplay');

    if (selectedLocation) {
        display.textContent = selectedLocation.direccion || 'Sin dirección';
        box.classList.remove('d-none');
    } else {
        box.classList.add('d-none');
    }
}

function clearLocation() {
    selectedLocation = null;
    if (marker) {
        marker.remove();
        marker = null;
    }
    updateLocationDisplay();
}

// ===== Submit =====
async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    const categoryId = document.getElementById('categoryId').value;
    const monto = parseFloat(document.getElementById('monto').value);

    if (!Number.isFinite(monto) || monto <= 0) {
        showError('El monto debe ser mayor a 0');
        return;
    }

    const base = {
        monto,
        fecha: document.getElementById('fecha').value,
        descripcion: document.getElementById('descripcion').value.trim() || null,
        categoryId: categoryId ? parseInt(categoryId) : null
    };

    const btn = document.getElementById('saveExpenseBtn');
    const original = btn.innerHTML;
    submitting = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        if (selectedLocation && selectedLocation.locationId) {
            base.locationId = selectedLocation.locationId;
            if (editingId) {
                await api.put('/expenses/' + editingId, base);
            } else {
                await api.post('/expenses', base);
            }
        } else if (selectedLocation) {
            const body = {
                ...base,
                locationDireccion: selectedLocation.direccion,
                locationLatitud: selectedLocation.latitud,
                locationLongitud: selectedLocation.longitud,
                locationCiudad: selectedLocation.ciudad,
                locationPais: selectedLocation.pais,
                locationPlaceId: selectedLocation.placeId
            };
            if (editingId) {
                await api.put('/expenses/' + editingId + '/with-location', body);
            } else {
                await api.post('/expenses/with-location', body);
            }
        } else {
            base.locationId = null;
            if (editingId) {
                await api.put('/expenses/' + editingId, base);
            } else {
                await api.post('/expenses', base);
            }
        }

        showSuccess(editingId ? 'Gasto actualizado' : 'Gasto registrado');
        setTimeout(() => window.location.href = '/expenses.html', 700);
    } catch (err) {
        showError(err.message);
        submitting = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

// New expense with location

const MAP_STYLE = 'https://tiles.openfreemap.org/styles/liberty';
const MAP_CENTER = [-77.0428, -12.0464]; // [longitud, latitud]
const MAP_ZOOM = 12;

let map;
let marker;
let selectedLocation = null;
let searchTimeout;
let searchResultsCache = [];
let savedPlaces = [];
let submitting = false;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    const hoy = todayLocalISO();
    document.getElementById('fecha').value = hoy;
    document.getElementById('fecha').max = hoy;

    await loadCategories();
    await loadSavedPlaces();
    initMap();
    initSearch();
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

    setMarker(place.latitud, place.longitud);
    updateLocationDisplay();

    // Registrar uso (silencioso si falla)
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

        await api.post('/saved-places', {
            alias: alias.trim(),
            locationId
        });

        showSuccess('Lugar guardado como "' + alias.trim() + '"');
        await loadSavedPlaces();
    } catch (err) {
        showError(err.message);
    }
}

// ===== Map =====
function initMap() {
    map = new maplibregl.Map({
        container: 'map',
        style: MAP_STYLE,
        center: MAP_CENTER,
        zoom: MAP_ZOOM
    });

    map.addControl(new maplibregl.NavigationControl(), 'top-right');

    map.on('click', async (ev) => {
        await reverseGeocode(ev.lngLat.lat, ev.lngLat.lng);
    });

    map.on('error', (ev) => {
        console.error('Error en el mapa:', ev.error);
    });
}

function setMarker(lat, lon) {
    if (marker) {
        marker.setLngLat([lon, lat]);
    } else {
        marker = new maplibregl.Marker({ draggable: true })
            .setLngLat([lon, lat])
            .addTo(map);

        marker.on('dragend', async () => {
            const pos = marker.getLngLat();
            await reverseGeocode(pos.lat, pos.lng);
        });
    }

    map.flyTo({ center: [lon, lat], zoom: 16 });
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

    const body = {
        monto,
        fecha: document.getElementById('fecha').value,
        descripcion: document.getElementById('descripcion').value.trim() || null,
        categoryId: categoryId ? parseInt(categoryId) : null
    };

    if (selectedLocation && selectedLocation.locationId) {
        body.locationId = selectedLocation.locationId;
    } else if (selectedLocation) {
        body.locationDireccion = selectedLocation.direccion;
        body.locationLatitud = selectedLocation.latitud;
        body.locationLongitud = selectedLocation.longitud;
        body.locationCiudad = selectedLocation.ciudad;
        body.locationPais = selectedLocation.pais;
        body.locationPlaceId = selectedLocation.placeId;
    } else {
        body.locationId = null;
    }

    const btn = document.getElementById('saveExpenseBtn');
    const original = btn.innerHTML;
    submitting = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        if (selectedLocation && !selectedLocation.locationId) {
            await api.post('/expenses/with-location', body);
        } else {
            await api.post('/expenses', body);
        }
        showSuccess('Gasto registrado');
        setTimeout(() => window.location.href = '/expenses.html', 800);
    } catch (err) {
        showError(err.message);
        submitting = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

// ===== Helpers =====
function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function showSuccess(msg) {
    const box = document.getElementById('successBox');
    box.textContent = msg;
    box.classList.remove('d-none');
    setTimeout(() => box.classList.add('d-none'), 3000);
}

function showError(msg) {
    const box = document.getElementById('errorBox');
    box.textContent = msg;
    box.classList.remove('d-none');
    setTimeout(() => box.classList.add('d-none'), 5000);
}

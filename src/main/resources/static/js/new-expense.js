// New expense with location

let map;
let marker;
let selectedLocation = null;
let searchTimeout;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    const email = getEmail();
    if (email) document.getElementById('userEmail').textContent = email;

    // Default date = today
    document.getElementById('fecha').value = new Date().toISOString().split('T')[0];

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
        showError('Error al cargar categorías');
    }
}

// ===== Load saved places =====
async function loadSavedPlaces() {
    const container = document.getElementById('savedPlacesChips');
    try {
        const places = await api.get('/saved-places');

        if (!places || places.length === 0) {
            container.innerHTML = '<span class="text-muted small">No tienes lugares guardados aún.</span>';
            return;
        }

        container.innerHTML = places.map(p => `
            <button type="button" class="btn btn-sm btn-outline-primary"
                    onclick="useSavedPlace(${p.id})"
                    data-lat="${p.latitud}"
                    data-lon="${p.longitud}"
                    data-dir="${escapeHtml(p.direccion || '')}"
                    data-ciudad="${escapeHtml(p.ciudad || '')}"
                    data-pais="${escapeHtml(p.pais || '')}"
                    data-placeid="${escapeHtml(p.placeId || '')}">
                <i class="bi bi-bookmark-star"></i> ${escapeHtml(p.alias)}
            </button>
        `).join('');
    } catch (err) {
        console.error('Error loading saved places:', err);
        container.innerHTML = '<span class="text-danger small">Error al cargar lugares guardados.</span>';
    }
}

async function useSavedPlace(id) {
    const btn = document.querySelector(`button[onclick="useSavedPlace(${id})"]`);
    if (!btn) return;

    selectedLocation = {
        direccion: btn.dataset.dir,
        latitud: parseFloat(btn.dataset.lat),
        longitud: parseFloat(btn.dataset.lon),
        ciudad: btn.dataset.ciudad,
        pais: btn.dataset.pais,
        placeId: btn.dataset.placeid
    };

    setMarker(selectedLocation.latitud, selectedLocation.longitud);
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
        // 1. Crear la location
        const loc = await api.post('/locations', {
            direccion: selectedLocation.direccion,
            latitud: selectedLocation.latitud,
            longitud: selectedLocation.longitud,
            ciudad: selectedLocation.ciudad,
            pais: selectedLocation.pais,
            placeId: selectedLocation.placeId
        });

        // 2. Crear el saved place
        await api.post('/saved-places', {
            alias: alias.trim(),
            locationId: loc.id
        });

        showSuccess('Lugar guardado como "' + alias.trim() + '"');
        await loadSavedPlaces();
    } catch (err) {
        showError(err.message);
    }
}

// ===== Map =====
function initMap() {
    map = L.map('map').setView([-12.0464, -77.0428], 12);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap'
    }).addTo(map);

    map.on('click', async (ev) => {
        const { lat, lng } = ev.latlng;
        await reverseGeocode(lat, lng);
    });
}

function setMarker(lat, lon) {
    if (marker) {
        marker.setLatLng([lat, lon]);
    } else {
        marker = L.marker([lat, lon], { draggable: true }).addTo(map);
        marker.on('dragend', async () => {
            const pos = marker.getLatLng();
            await reverseGeocode(pos.lat, pos.lng);
        });
    }
    map.setView([lat, lon], 16);
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
        showError('Error al obtener la dirección');
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
        if (!items || items.length === 0) {
            results.innerHTML = '<div class="list-group-item text-muted small">Sin resultados</div>';
            return;
        }

        results.innerHTML = items.map((r, i) => `
            <button type="button" class="list-group-item list-group-item-action"
                    onclick='selectSearchResult(${JSON.stringify(r).replace(/'/g, "&apos;")})'>
                <div class="small fw-semibold">${escapeHtml(r.displayName)}</div>
                <div class="text-muted" style="font-size: 0.75rem;">
                    ${escapeHtml(r.ciudad || '')} ${r.pais ? '- ' + escapeHtml(r.pais) : ''}
                </div>
            </button>
        `).join('');
    } catch (err) {
        results.innerHTML = '<div class="list-group-item text-danger small">Error al buscar</div>';
    }
}

function selectSearchResult(result) {
    selectedLocation = {
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
        map.removeLayer(marker);
        marker = null;
    }
    updateLocationDisplay();
}

// ===== Submit =====
async function handleSubmit(event) {
    event.preventDefault();

    const categoryId = document.getElementById('categoryId').value;

    const body = {
        monto: parseFloat(document.getElementById('monto').value),
        fecha: document.getElementById('fecha').value,
        descripcion: document.getElementById('descripcion').value.trim() || null,
        categoryId: categoryId ? parseInt(categoryId) : null
    };

    if (selectedLocation) {
        body.locationDireccion = selectedLocation.direccion;
        body.locationLatitud = selectedLocation.latitud;
        body.locationLongitud = selectedLocation.longitud;
        body.locationCiudad = selectedLocation.ciudad;
        body.locationPais = selectedLocation.pais;
        body.locationPlaceId = selectedLocation.placeId;

        try {
            await api.post('/expenses/with-location', body);
            showSuccess('Gasto registrado con ubicación');
            setTimeout(() => window.location.href = '/expenses.html', 800);
        } catch (err) {
            showError(err.message);
        }
    } else {
        body.locationId = null;
        try {
            await api.post('/expenses', body);
            showSuccess('Gasto registrado');
            setTimeout(() => window.location.href = '/expenses.html', 800);
        } catch (err) {
            showError(err.message);
        }
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
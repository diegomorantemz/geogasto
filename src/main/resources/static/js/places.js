// Saved places management

let placeModal;
let places = [];
let submitting = false;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();
    placeModal = new bootstrap.Modal(document.getElementById('placeModal'));
    await loadPlaces();
});

async function loadPlaces() {
    const tbody = document.getElementById('placesTable');
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted">
        <span class="spinner-border spinner-border-sm me-2"></span>Cargando lugares...
    </td></tr>`;

    try {
        places = await api.get('/saved-places');
        renderTable(places);
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-danger">
            Error al cargar lugares: ${escapeHtml(err.message)}
        </td></tr>`;
    }
}

function renderTable(items) {
    const tbody = document.getElementById('placesTable');

    if (!items || items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted">
            No tienes lugares guardados. Selecciona una ubicación en <a href="/new-expense.html">Nuevo gasto</a>
            y guárdala como favorita.
        </td></tr>`;
        return;
    }

    tbody.innerHTML = items.map(p => `
        <tr>
            <td data-label="Alias" class="fw-semibold">
                <i class="bi bi-bookmark-star text-warning"></i> ${escapeHtml(p.alias)}
            </td>
            <td data-label="Dirección" class="small">${escapeHtml(p.direccion || '-')}</td>
            <td data-label="Ciudad" class="small text-muted">${escapeHtml(p.ciudad || '-')}${p.pais ? ', ' + escapeHtml(p.pais) : ''}</td>
            <td data-label="Usos" class="text-center"><span class="badge bg-light text-dark">${p.usageCount || 0}</span></td>
            <td data-label="Acciones" class="text-end text-nowrap">
                <button class="btn btn-sm btn-outline-primary me-1" onclick="openEditPlace(${p.id})"
                        aria-label="Renombrar lugar" title="Renombrar">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="deletePlace(${p.id})"
                        aria-label="Eliminar lugar" title="Eliminar">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

function openEditPlace(id) {
    const place = places.find(p => p.id === id);
    if (!place) return;

    document.getElementById('placeId').value = place.id;
    document.getElementById('placeAlias').value = place.alias;
    document.getElementById('placeModalTitle').textContent = 'Renombrar lugar';
    placeModal.show();
}

async function handleSavePlace(event) {
    event.preventDefault();
    if (submitting) return;

    const id = document.getElementById('placeId').value;
    const alias = document.getElementById('placeAlias').value.trim();
    if (!alias) return;

    const btn = document.getElementById('placeSaveBtn');
    const original = btn.innerHTML;
    submitting = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        await api.put('/saved-places/' + id, { alias });
        placeModal.hide();
        showSuccess('Lugar renombrado');
        await loadPlaces();
    } catch (err) {
        showError(err.message);
    } finally {
        submitting = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function deletePlace(id) {
    const place = places.find(p => p.id === id);
    if (!place) return;

    if (!confirm(`¿Eliminar el lugar guardado "${place.alias}"?`)) return;

    try {
        await api.delete('/saved-places/' + id);
        showSuccess('Lugar eliminado');
        await loadPlaces();
    } catch (err) {
        showError('No se pudo eliminar: ' + err.message);
    }
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

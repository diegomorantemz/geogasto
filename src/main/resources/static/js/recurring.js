// Recurring expenses

let recurringModal;
let categories = [];
let places = [];
let recurrences = [];
let submitting = false;

const FREQUENCY_LABELS = {
    DAILY: 'Diario',
    WEEKLY: 'Semanal',
    MONTHLY: 'Mensual',
    YEARLY: 'Anual'
};

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    recurringModal = new bootstrap.Modal(document.getElementById('recurringModal'));
    await loadCategories();
    await loadPlaces();
    await loadRecurring();
});

async function loadCategories() {
    try {
        categories = await api.get('/categories');
        const select = document.getElementById('recurringCategory');
        select.innerHTML = '<option value="">Sin categoría</option>' +
            categories.map(c => `<option value="${c.id}">${escapeHtml(c.nombre)}</option>`).join('');
    } catch (err) {
        showError('No se pudieron cargar las categorías: ' + err.message);
    }
}

async function loadPlaces() {
    try {
        places = await api.get('/saved-places');
        const select = document.getElementById('recurringLocation');
        select.innerHTML = '<option value="">Sin ubicación</option>' +
            places.map(p => `<option value="${p.locationId}">${escapeHtml(p.alias)}</option>`).join('');
    } catch (err) {
        showError('No se pudieron cargar los lugares guardados: ' + err.message);
    }
}

async function loadRecurring() {
    const tbody = document.getElementById('recurringTable');
    tbody.innerHTML = `<tr><td colspan="8" class="text-center py-4 text-muted">
        <span class="spinner-border spinner-border-sm me-2"></span>Cargando recurrencias...
    </td></tr>`;

    try {
        recurrences = await api.get('/recurring-expenses');
        renderSummary();
        renderTable();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center py-4 text-danger">
            Error al cargar recurrencias: ${escapeHtml(err.message)}
        </td></tr>`;
    }
}

function renderSummary() {
    const activas = recurrences.filter(r => r.activo);
    document.getElementById('recurringActive').textContent = activas.length;

    const proxima = activas
        .map(r => r.proximaFecha)
        .filter(Boolean)
        .sort()[0];
    document.getElementById('recurringNext').textContent = proxima ? formatDate(proxima) : '—';

    const mensual = activas.reduce((total, r) => total + montoMensual(r), 0);
    document.getElementById('recurringMonthly').textContent = formatMoney(mensual);
}

function montoMensual(r) {
    const monto = Number(r.monto || 0);
    switch (r.frecuencia) {
        case 'DAILY': return monto * 30;
        case 'WEEKLY': return monto * 4.3333;
        case 'YEARLY': return monto / 12;
        default: return monto;
    }
}

function renderTable() {
    const tbody = document.getElementById('recurringTable');

    if (!recurrences.length) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center py-4 text-muted">
            No tienes gastos recurrentes.
            <button class="btn btn-sm btn-link p-0 align-baseline" onclick="openNewRecurringModal()">
                Crea el primero
            </button>.
        </td></tr>`;
        return;
    }

    tbody.innerHTML = recurrences.map(r => {
        const vencido = r.activo && r.proximaFecha < todayProfileISO();
        return `
            <tr>
                <td data-label="Descripción">
                    <div class="fw-semibold">${escapeHtml(r.descripcion || 'Sin descripción')}</div>
                    ${r.locationDireccion
                        ? `<div class="small text-muted"><i class="bi bi-geo-alt"></i> ${escapeHtml(r.locationDireccion)}</div>`
                        : ''}
                </td>
                <td data-label="Categoría">
                    ${r.categoryNombre
                        ? `<span class="badge-soft">
                               <span class="d-inline-block rounded-circle me-1"
                                     style="width: 8px; height: 8px; background-color: ${escapeHtml(r.categoryColor || '#c9c3b8')};"></span>
                               ${escapeHtml(r.categoryNombre)}
                           </span>`
                        : '<span class="text-muted small">Sin categoría</span>'}
                </td>
                <td data-label="Frecuencia"><span class="badge bg-secondary-subtle text-secondary-emphasis">${FREQUENCY_LABELS[r.frecuencia] || r.frecuencia}</span></td>
                <td data-label="Monto" class="text-end fw-bold text-nowrap">${formatMoney(r.monto)}</td>
                <td data-label="Próximo gasto" class="text-nowrap">
                    <span class="${vencido ? 'text-danger fw-semibold' : ''}">${formatDate(r.proximaFecha)}</span>
                    ${vencido ? '<span class="badge bg-danger-subtle text-danger-emphasis ms-1">Vencido</span>' : ''}
                </td>
                <td data-label="Fin" class="text-muted small text-nowrap">${r.fechaFin ? formatDate(r.fechaFin) : '—'}</td>
                <td data-label="Estado">
                    ${r.activo
                        ? '<span class="badge bg-success-subtle text-success-emphasis">Activa</span>'
                        : '<span class="badge bg-secondary-subtle text-secondary-emphasis">Pausada</span>'}
                </td>
                <td data-label="Acciones" class="text-end text-nowrap">
                    <button class="btn btn-sm btn-outline-secondary me-1" onclick="skipRecurring(${r.id})"
                            aria-label="Omitir próximo" title="Omitir próximo">
                        <i class="bi bi-skip-forward"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-primary me-1" onclick="editRecurring(${r.id})"
                            aria-label="Editar recurrencia" title="Editar">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger" onclick="deleteRecurring(${r.id})"
                            aria-label="Eliminar recurrencia" title="Eliminar">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

function openNewRecurringModal() {
    const form = document.getElementById('recurringForm');
    form.reset();

    document.getElementById('recurringId').value = '';
    document.getElementById('recurringFechaInicio').value = todayProfileISO();
    document.getElementById('recurringFechaInicio').disabled = false;
    document.getElementById('recurringFrecuencia').disabled = false;
    document.getElementById('recurringActivo').checked = true;
    document.getElementById('recurringEditHint').classList.add('d-none');
    document.getElementById('recurringModalTitle').textContent = 'Nueva recurrencia';
    recurringModal.show();
}

function editRecurring(id) {
    const r = recurrences.find(x => x.id === id);
    if (!r) return;
    const form = document.getElementById('recurringForm');
    form.reset();

    document.getElementById('recurringId').value = r.id;
    document.getElementById('recurringMonto').value = r.monto;
    document.getElementById('recurringDescripcion').value = r.descripcion || '';
    document.getElementById('recurringFrecuencia').value = r.frecuencia;
    document.getElementById('recurringFechaInicio').value = r.fechaInicio;
    document.getElementById('recurringFechaFin').value = r.fechaFin || '';
    document.getElementById('recurringCategory').value = r.categoryId || '';
    document.getElementById('recurringLocation').value = r.locationId || '';
    document.getElementById('recurringActivo').checked = !!r.activo;

    document.getElementById('recurringFechaInicio').disabled = true;
    document.getElementById('recurringFrecuencia').disabled = true;
    document.getElementById('recurringEditHint').classList.remove('d-none');
    document.getElementById('recurringModalTitle').textContent = 'Editar recurrencia';
    recurringModal.show();
}

async function handleSaveRecurring(event) {
    event.preventDefault();
    if (submitting) return;

    const id = document.getElementById('recurringId').value;
    const monto = parseFloat(document.getElementById('recurringMonto').value);
    const fechaInicio = document.getElementById('recurringFechaInicio').value;
    const fechaFin = document.getElementById('recurringFechaFin').value;
    const categoryId = document.getElementById('recurringCategory').value;
    const locationId = document.getElementById('recurringLocation').value;

    if (!Number.isFinite(monto) || monto <= 0) {
        showError('El monto debe ser mayor a 0');
        return;
    }
    if (!fechaInicio) {
        showError('La fecha de inicio es obligatoria');
        return;
    }
    if (fechaFin && fechaFin < fechaInicio) {
        showError('La fecha de fin no puede ser anterior a la fecha de inicio');
        return;
    }

    const body = {
        monto,
        descripcion: document.getElementById('recurringDescripcion').value.trim() || null,
        frecuencia: document.getElementById('recurringFrecuencia').value,
        fechaInicio,
        fechaFin: fechaFin || null,
        categoryId: categoryId ? parseInt(categoryId) : null,
        locationId: locationId ? parseInt(locationId) : null,
        activo: document.getElementById('recurringActivo').checked
    };

    const btn = document.getElementById('recurringSaveBtn');
    const original = btn.innerHTML;
    submitting = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        if (id) {
            await api.put('/recurring-expenses/' + id, body);
            showSuccess('Recurrencia actualizada');
        } else {
            await api.post('/recurring-expenses', body);
            showSuccess('Recurrencia creada. Los gastos vencidos ya se generaron.');
        }
        recurringModal.hide();
        await loadRecurring();
    } catch (err) {
        showError(err.message);
    } finally {
        submitting = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function skipRecurring(id) {
    const r = recurrences.find(x => x.id === id);
    if (!r) return;
    if (!confirm(`¿Omitir el próximo gasto de "${r.descripcion || 'Sin descripción'}"?`)) return;

    try {
        await api.post('/recurring-expenses/' + id + '/skip', {});
        showSuccess('Próximo gasto omitido');
        await loadRecurring();
    } catch (err) {
        showError('No se pudo omitir: ' + err.message);
    }
}

async function deleteRecurring(id) {
    const r = recurrences.find(x => x.id === id);
    if (!r) return;

    if (!confirm(`¿Eliminar la recurrencia "${r.descripcion || 'Sin descripción'}"?\n\nLos gastos ya generados se conservarán.`)) {
        return;
    }

    try {
        await api.delete('/recurring-expenses/' + id);
        showSuccess('Recurrencia eliminada');
        await loadRecurring();
    } catch (err) {
        showError('No se pudo eliminar: ' + err.message);
    }
}

async function generateNow() {
    const btn = document.getElementById('generateRecurringBtn');
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Generando...';

    try {
        const result = await api.post('/recurring-expenses/generate', {});
        showSuccess(result.generados > 0
            ? `Se generaron ${result.generados} gastos recurrentes`
            : 'No había gastos pendientes por generar');
        await loadRecurring();
    } catch (err) {
        showError(err.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

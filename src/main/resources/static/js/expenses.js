// Expenses page logic

let expenseModal;
let categories = [];
let currentPage = 0;
const PAGE_SIZE = 10;
let totalPages = 1;
let lastPageSize = 0;
let submitting = false;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    expenseModal = new bootstrap.Modal(document.getElementById('expenseModal'));

    const hoy = todayLocalISO();
    document.getElementById('expenseFecha').value = hoy;
    document.getElementById('expenseFecha').max = hoy;
    document.getElementById('filterFrom').max = hoy;
    document.getElementById('filterTo').max = hoy;

    await loadCategories();
    await loadExpenses();
});

// ===== Load categories for dropdowns =====
async function loadCategories() {
    try {
        categories = await api.get('/categories');

        const filterSelect = document.getElementById('filterCategory');
        const modalSelect = document.getElementById('expenseCategory');

        const options = categories
            .map(c => `<option value="${c.id}">${escapeHtml(c.nombre)}</option>`)
            .join('');

        filterSelect.innerHTML = '<option value="">Todas</option>' + options;
        modalSelect.innerHTML = '<option value="">Sin categoría</option>' + options;
    } catch (err) {
        showError('No se pudieron cargar las categorías: ' + err.message);
    }
}

// ===== Load expenses with filters and pagination =====
async function loadExpenses() {
    const tbody = document.getElementById('expensesTable');
    tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-muted">
        <span class="spinner-border spinner-border-sm me-2"></span>Cargando gastos...
    </td></tr>`;

    try {
        const page = await api.get('/expenses?' + buildFilterParams().toString());

        lastPageSize = (page.content || []).length;
        renderTable(page.content || []);

        totalPages = page.totalPages || 1;
        document.getElementById('pageInfo').textContent =
            `Página ${currentPage + 1} de ${totalPages} (${page.totalElements} gastos)`;

        document.getElementById('prevBtn').disabled = currentPage === 0;
        document.getElementById('nextBtn').disabled = currentPage >= totalPages - 1;

    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-danger">
            Error al cargar gastos: ${escapeHtml(err.message)}
        </td></tr>`;
    }
}

function buildFilterParams() {
    const params = new URLSearchParams();
    params.set('page', currentPage);
    params.set('size', PAGE_SIZE);
    params.set('sort', 'fecha,desc');

    const from = document.getElementById('filterFrom').value;
    const to = document.getElementById('filterTo').value;
    const categoryId = document.getElementById('filterCategory').value;
    const q = document.getElementById('filterText').value.trim();

    if (from) params.set('desde', from);
    if (to) params.set('hasta', to);
    if (categoryId) params.set('categoryId', categoryId);
    if (q) params.set('q', q);

    return params;
}

async function exportExpenses() {
    const params = buildFilterParams();
    params.delete('page');
    params.delete('size');
    params.delete('sort');

    try {
        const response = await fetch('/api/expenses/export?' + params.toString(), {
            headers: { Authorization: 'Bearer ' + getToken() }
        });

        if (response.status === 401) {
            logout();
            return;
        }
        if (!response.ok) {
            throw new Error('No se pudo exportar el archivo');
        }

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'gastos.csv';
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);

        showSuccess('Archivo exportado');
    } catch (err) {
        showError(err.message);
    }
}

function renderTable(items) {
    const tbody = document.getElementById('expensesTable');

    if (!items || items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-muted">
            No hay gastos para mostrar con estos filtros.
        </td></tr>`;
        return;
    }

    tbody.innerHTML = items.map(e => `
        <tr>
            <td class="text-muted small text-nowrap">${formatDate(e.fecha)}</td>
            <td class="fw-semibold">${escapeHtml(e.descripcion || '-')}</td>
            <td>
                ${e.categoryNombre
                    ? `<span class="badge bg-secondary">${escapeHtml(e.categoryNombre)}</span>`
                    : '<span class="text-muted small">Sin categoría</span>'}
            </td>
            <td class="small text-muted">
                ${e.locationDireccion
                    ? `<span class="d-inline-block text-truncate align-middle" style="max-width: 230px"
                             title="${escapeHtml(e.locationDireccion)}">
                           <i class="bi bi-geo-alt text-primary"></i> ${escapeHtml(e.locationDireccion)}
                       </span>`
                    : '<span class="text-muted">Sin ubicación</span>'}
            </td>
            <td class="text-end fw-bold text-nowrap">${formatMoney(e.monto)}</td>
            <td class="text-end text-nowrap">
                <button class="btn btn-sm btn-outline-primary me-1" onclick="editExpense(${e.id})"
                        aria-label="Editar gasto" title="Editar">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteExpense(${e.id})"
                        aria-label="Eliminar gasto" title="Eliminar">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

// ===== Create / Edit =====
function openNewExpenseModal() {
    document.getElementById('expenseForm').reset();
    document.getElementById('expenseId').value = '';
    document.getElementById('expenseFecha').value = todayLocalISO();
    document.getElementById('expenseModalTitle').textContent = 'Nuevo gasto';
    clearExpenseLocation();
    expenseModal.show();
}

async function editExpense(id) {
    try {
        const e = await api.get('/expenses/' + id);
        document.getElementById('expenseId').value = e.id;
        document.getElementById('expenseMonto').value = e.monto;
        document.getElementById('expenseFecha').value = e.fecha;
        document.getElementById('expenseDescripcion').value = e.descripcion || '';
        document.getElementById('expenseCategory').value = e.categoryId || '';
        document.getElementById('expenseModalTitle').textContent = 'Editar gasto';

        if (e.locationId) {
            setExpenseLocation(e.locationId, e.locationDireccion || 'Ubicación seleccionada');
        } else {
            clearExpenseLocation();
        }

        expenseModal.show();
    } catch (err) {
        showError(err.message);
    }
}

function setExpenseLocation(id, direccion) {
    document.getElementById('expenseLocationId').value = id;
    document.getElementById('expenseLocationText').textContent = direccion;
    document.getElementById('expenseLocationBox').classList.remove('d-none');
}

function clearExpenseLocation() {
    document.getElementById('expenseLocationId').value = '';
    document.getElementById('expenseLocationBox').classList.add('d-none');
}

async function handleSaveExpense(event) {
    event.preventDefault();
    if (submitting) return;

    const id = document.getElementById('expenseId').value;
    const categoryId = document.getElementById('expenseCategory').value;
    const locationId = document.getElementById('expenseLocationId').value;
    const monto = parseFloat(document.getElementById('expenseMonto').value);

    if (!Number.isFinite(monto) || monto <= 0) {
        showError('El monto debe ser mayor a 0');
        return;
    }

    const body = {
        monto,
        fecha: document.getElementById('expenseFecha').value,
        descripcion: document.getElementById('expenseDescripcion').value.trim() || null,
        categoryId: categoryId ? parseInt(categoryId) : null,
        locationId: locationId ? parseInt(locationId) : null
    };

    const btn = document.getElementById('expenseSaveBtn');
    const original = btn.innerHTML;
    submitting = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        if (id) {
            await api.put('/expenses/' + id, body);
            showSuccess('Gasto actualizado');
        } else {
            await api.post('/expenses', body);
            showSuccess('Gasto creado');
        }
        expenseModal.hide();
        await loadExpenses();
    } catch (err) {
        showError(err.message);
    } finally {
        submitting = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function deleteExpense(id) {
    if (!confirm('¿Eliminar este gasto?')) return;

    try {
        await api.delete('/expenses/' + id);
        if (lastPageSize === 1 && currentPage > 0) {
            currentPage--;
        }
        showSuccess('Gasto eliminado');
        await loadExpenses();
    } catch (err) {
        showError('No se pudo eliminar: ' + err.message);
    }
}

// ===== Pagination =====
function prevPage() {
    if (currentPage > 0) {
        currentPage--;
        loadExpenses();
    }
}

function nextPage() {
    if (currentPage < totalPages - 1) {
        currentPage++;
        loadExpenses();
    }
}

// ===== Filters =====
function applyFilters() {
    currentPage = 0;
    loadExpenses();
}

function clearFilters() {
    document.getElementById('filterFrom').value = '';
    document.getElementById('filterTo').value = '';
    document.getElementById('filterCategory').value = '';
    document.getElementById('filterText').value = '';
    currentPage = 0;
    loadExpenses();
}

// ===== Helpers =====
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

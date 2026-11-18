// Expenses list: filters, pagination, receipts and navigation to the unified form.

let receiptModal;
let currentPage = 0;
const PAGE_SIZE = 10;
let totalPages = 1;
let lastPageSize = 0;
let receiptExpenseId = null;
let receiptSubmitting = false;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    receiptModal = new bootstrap.Modal(document.getElementById('receiptModal'));

    const hoy = todayLocalISO();
    document.getElementById('filterFrom').max = hoy;
    document.getElementById('filterTo').max = hoy;

    await loadCategories();
    await loadExpenses();
});

// ===== Categories for filter =====
async function loadCategories() {
    try {
        const categories = await api.get('/categories');
        const filterSelect = document.getElementById('filterCategory');
        filterSelect.innerHTML = '<option value="">Todas</option>' +
            categories.map(c => `<option value="${c.id}">${escapeHtml(c.nombre)}</option>`).join('');
    } catch (err) {
        showError('No se pudieron cargar las categorías: ' + err.message);
    }
}

// ===== Load expenses =====
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
            <td data-label="Fecha" class="text-muted small text-nowrap">${formatDate(e.fecha)}</td>
            <td data-label="Descripción" class="fw-semibold">
                ${escapeHtml(e.descripcion || '-')}
                ${e.receiptId ? '<i class="bi bi-paperclip text-primary ms-1" title="Tiene comprobante"></i>' : ''}
            </td>
            <td data-label="Categoría">
                ${e.categoryNombre
                    ? `<span class="badge bg-secondary">${escapeHtml(e.categoryNombre)}</span>`
                    : '<span class="text-muted small">Sin categoría</span>'}
            </td>
            <td data-label="Ubicación" class="small text-muted">
                ${e.locationDireccion
                    ? `<span class="d-inline-block text-truncate align-middle" style="max-width: 230px"
                             title="${escapeHtml(e.locationDireccion)}">
                           <i class="bi bi-geo-alt text-primary"></i> ${escapeHtml(e.locationDireccion)}
                       </span>`
                    : '<span class="text-muted">Sin ubicación</span>'}
            </td>
            <td data-label="Monto" class="text-end fw-bold text-nowrap">${formatMoney(e.monto)}</td>
            <td data-label="Acciones" class="text-end text-nowrap">
                <button class="btn btn-sm btn-outline-secondary me-1" onclick="openReceipt(${e.id})"
                        aria-label="Comprobante" title="${e.receiptId ? 'Ver comprobante' : 'Adjuntar comprobante'}">
                    <i class="bi bi-paperclip"></i>
                </button>
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

// ===== Unified create/edit form =====
function editExpense(id) {
    window.location.href = '/new-expense.html?id=' + id;
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

// ===== Pagination and filters =====
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

// ===== Receipt =====
async function openReceipt(id) {
    try {
        const expense = await api.get('/expenses/' + id);
        receiptExpenseId = id;

        document.getElementById('receiptExpenseId').value = id;
        document.getElementById('receiptForm').reset();
        document.getElementById('receiptModalTitle').textContent =
            'Comprobante: ' + (expense.descripcion || 'Gasto');

        const existing = document.getElementById('receiptExisting');
        const empty = document.getElementById('receiptEmpty');
        if (expense.receiptId) {
            existing.classList.remove('d-none');
            empty.classList.add('d-none');
            document.getElementById('receiptNombre').textContent = expense.receiptNombre || 'comprobante';
            document.getElementById('receiptTamano').textContent = expense.receiptTamano
                ? '(' + formatBytes(expense.receiptTamano) + ')'
                : '';
        } else {
            existing.classList.add('d-none');
            empty.classList.remove('d-none');
        }

        receiptModal.show();
    } catch (err) {
        showError(err.message);
    }
}

async function viewReceipt() {
    if (!receiptExpenseId) return;

    try {
        const response = await fetch('/api/expenses/' + receiptExpenseId + '/receipt', {
            headers: { Authorization: 'Bearer ' + getToken() }
        });
        if (response.status === 401) {
            logout();
            return;
        }
        if (!response.ok) {
            throw new Error('No se pudo abrir el comprobante');
        }

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const tab = window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
        if (!tab) {
            showError('El navegador bloqueó la pestaña del comprobante');
        }
    } catch (err) {
        showError(err.message);
    }
}

async function deleteReceipt() {
    if (!receiptExpenseId) return;
    if (!confirm('¿Quitar el comprobante de este gasto?')) return;

    try {
        await api.delete('/expenses/' + receiptExpenseId + '/receipt');
        receiptModal.hide();
        showSuccess('Comprobante eliminado');
        await loadExpenses();
    } catch (err) {
        showError(err.message);
    }
}

async function handleUploadReceipt(event) {
    event.preventDefault();
    if (receiptSubmitting || !receiptExpenseId) return;

    const input = document.getElementById('receiptFile');
    if (!input.files || input.files.length === 0) {
        showError('Selecciona un archivo');
        return;
    }

    const file = input.files[0];
    if (file.size > 5 * 1024 * 1024) {
        showError('El comprobante no puede superar 5 MB');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);

    const btn = document.getElementById('receiptUploadBtn');
    const original = btn.innerHTML;
    receiptSubmitting = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Subiendo...';

    try {
        const response = await fetch('/api/expenses/' + receiptExpenseId + '/receipt', {
            method: 'POST',
            headers: { Authorization: 'Bearer ' + getToken() },
            body: formData
        });

        if (response.status === 401) {
            logout();
            return;
        }
        if (!response.ok) {
            throw new Error(await readErrorResponse(response));
        }

        receiptModal.hide();
        showSuccess('Comprobante adjuntado');
        await loadExpenses();
    } catch (err) {
        showError(err.message);
    } finally {
        receiptSubmitting = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function readErrorResponse(response) {
    try {
        const data = await response.json();
        if (data.errors) {
            const first = Object.values(data.errors)[0];
            if (first) return first;
        }
        if (data.message) return data.message;
    } catch (ignored) {
        // sin JSON
    }
    return 'No se pudo subir el comprobante (HTTP ' + response.status + ')';
}

function formatBytes(bytes) {
    const value = Number(bytes || 0);
    if (value < 1024) return value + ' B';
    if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB';
    return (value / (1024 * 1024)).toFixed(1) + ' MB';
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

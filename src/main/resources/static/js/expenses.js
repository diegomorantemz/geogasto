// Expenses page logic

let expenseModal;
let categories = [];
let currentPage = 0;
const PAGE_SIZE = 10;
let totalPages = 1;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    const email = getEmail();
    if (email) document.getElementById('userEmail').textContent = email;

    expenseModal = new bootstrap.Modal(document.getElementById('expenseModal'));

    // Default date = today
    document.getElementById('expenseFecha').value = new Date().toISOString().split('T')[0];

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
        console.error('Error loading categories:', err);
    }
}

// ===== Load expenses with filters and pagination =====
async function loadExpenses() {
    const tbody = document.getElementById('expensesTable');
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted">Cargando gastos...</td></tr>`;

    try {
        const params = new URLSearchParams();
        params.set('page', currentPage);
        params.set('size', PAGE_SIZE);
        params.set('sort', 'fecha,desc');

        // Filters (only apply locally for now; backend filter endpoint comes later)
        const page = await api.get('/expenses?' + params.toString());

        renderTable(applyLocalFilters(page.content || []));

        totalPages = page.totalPages || 1;
        document.getElementById('pageInfo').textContent =
            `Página ${currentPage + 1} de ${totalPages} (${page.totalElements} gastos)`;

        document.getElementById('prevBtn').disabled = currentPage === 0;
        document.getElementById('nextBtn').disabled = currentPage >= totalPages - 1;

    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-danger">
            Error al cargar gastos: ${escapeHtml(err.message)}
        </td></tr>`;
    }
}

function applyLocalFilters(items) {
    const from = document.getElementById('filterFrom').value;
    const to = document.getElementById('filterTo').value;
    const categoryId = document.getElementById('filterCategory').value;

    return items.filter(e => {
        if (from && e.fecha < from) return false;
        if (to && e.fecha > to) return false;
        if (categoryId && String(e.categoryId) !== String(categoryId)) return false;
        return true;
    });
}

function renderTable(items) {
    const tbody = document.getElementById('expensesTable');

    if (!items || items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted">
            No hay gastos para mostrar.
        </td></tr>`;
        return;
    }

    tbody.innerHTML = items.map(e => `
        <tr>
            <td class="text-muted small">${formatDate(e.fecha)}</td>
            <td class="fw-semibold">${escapeHtml(e.descripcion || '-')}</td>
            <td>
                ${e.categoryNombre
        ? `<span class="badge bg-secondary">${escapeHtml(e.categoryNombre)}</span>`
        : '<span class="text-muted small">Sin categoría</span>'}
            </td>
            <td class="text-end fw-bold">${formatMoney(e.monto)}</td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-primary me-1" onclick="editExpense(${e.id})">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteExpense(${e.id})">
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
    document.getElementById('expenseFecha').value = new Date().toISOString().split('T')[0];
    document.getElementById('expenseModalTitle').textContent = 'Nuevo gasto';
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
        expenseModal.show();
    } catch (err) {
        showError(err.message);
    }
}

async function handleSaveExpense(event) {
    event.preventDefault();

    const id = document.getElementById('expenseId').value;
    const categoryId = document.getElementById('expenseCategory').value;

    const body = {
        monto: parseFloat(document.getElementById('expenseMonto').value),
        fecha: document.getElementById('expenseFecha').value,
        descripcion: document.getElementById('expenseDescripcion').value.trim() || null,
        categoryId: categoryId ? parseInt(categoryId) : null,
        locationId: null
    };

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
    }
}

async function deleteExpense(id) {
    if (!confirm('¿Eliminar este gasto?')) return;

    try {
        await api.delete('/expenses/' + id);
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
    currentPage = 0;
    loadExpenses();
}

// ===== Helpers =====
function formatDate(iso) {
    if (!iso) return '-';
    const d = new Date(iso + 'T00:00:00');
    return d.toLocaleDateString('es-PE', { year: 'numeric', month: 'short', day: 'numeric' });
}

function formatMoney(amount) {
    return 'S/ ' + Number(amount).toFixed(2);
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
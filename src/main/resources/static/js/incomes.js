// Income page logic

let incomeModal;
let currentPage = 0;
const PAGE_SIZE = 10;
let totalPages = 1;
let lastPageSize = 0;
let submitting = false;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    incomeModal = new bootstrap.Modal(document.getElementById('incomeModal'));

    const hoy = todayProfileISO();
    document.getElementById('incomeFecha').value = hoy;
    document.getElementById('incomeFecha').max = hoy;
    document.getElementById('incomeFrom').max = hoy;
    document.getElementById('incomeTo').max = hoy;

    await loadMonthSummary();
    await loadIncomes();
});

async function loadMonthSummary() {
    try {
        const stats = await api.get('/stats');
        document.getElementById('incomeMonth').textContent = formatMoney(stats.totalIngresosMes);
        document.getElementById('expenseMonth').textContent = formatMoney(stats.totalMes);

        const balance = Number(stats.balanceMes || 0);
        const el = document.getElementById('balanceMonth');
        el.textContent = formatMoney(balance);
        el.className = 'stat-value ' + (balance >= 0 ? 'text-success' : 'text-danger');
    } catch (err) {
        // Si falla el resumen, la lista sigue funcionando.
    }
}

async function loadIncomes() {
    const tbody = document.getElementById('incomesTable');
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted">
        <span class="spinner-border spinner-border-sm me-2"></span>Cargando ingresos...
    </td></tr>`;

    try {
        const page = await api.get('/incomes?' + buildFilterParams().toString());

        lastPageSize = (page.content || []).length;
        renderTable(page.content || []);

        totalPages = page.totalPages || 1;
        document.getElementById('incomePageInfo').textContent =
            `Página ${currentPage + 1} de ${totalPages} (${page.totalElements} ingresos)`;

        document.getElementById('incomePrevBtn').disabled = currentPage === 0;
        document.getElementById('incomeNextBtn').disabled = currentPage >= totalPages - 1;
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-danger">
            Error al cargar ingresos: ${escapeHtml(err.message)}
        </td></tr>`;
    }
}

function buildFilterParams() {
    const params = new URLSearchParams();
    params.set('page', currentPage);
    params.set('size', PAGE_SIZE);
    params.set('sort', 'fecha,desc');

    const from = document.getElementById('incomeFrom').value;
    const to = document.getElementById('incomeTo').value;
    const q = document.getElementById('incomeText').value.trim();

    if (from) params.set('desde', from);
    if (to) params.set('hasta', to);
    if (q) params.set('q', q);

    return params;
}

function renderTable(items) {
    const tbody = document.getElementById('incomesTable');

    if (!items || items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted">
            No hay ingresos para mostrar con estos filtros.
        </td></tr>`;
        return;
    }

    tbody.innerHTML = items.map(i => `
        <tr>
            <td data-label="Fecha" class="text-muted small text-nowrap">${formatDate(i.fecha)}</td>
            <td data-label="Descripción" class="fw-semibold">${escapeHtml(i.descripcion || '-')}</td>
            <td data-label="Fuente">
                ${i.fuente
                    ? `<span class="badge bg-success-subtle text-success-emphasis">${escapeHtml(i.fuente)}</span>`
                    : '<span class="text-muted small">Sin fuente</span>'}
            </td>
            <td data-label="Monto" class="text-end fw-bold text-success text-nowrap">+ ${formatMoney(i.monto)}</td>
            <td data-label="Acciones" class="text-end text-nowrap">
                <button class="btn btn-sm btn-outline-primary me-1" onclick="editIncome(${i.id})"
                        aria-label="Editar ingreso" title="Editar">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteIncome(${i.id})"
                        aria-label="Eliminar ingreso" title="Eliminar">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

function openNewIncomeModal() {
    document.getElementById('incomeForm').reset();
    document.getElementById('incomeId').value = '';
    document.getElementById('incomeFecha').value = todayProfileISO();
    document.getElementById('incomeModalTitle').textContent = 'Nuevo ingreso';
    incomeModal.show();
}

async function editIncome(id) {
    try {
        const income = await api.get('/incomes/' + id);
        document.getElementById('incomeId').value = income.id;
        document.getElementById('incomeMonto').value = income.monto;
        document.getElementById('incomeFecha').value = income.fecha;
        document.getElementById('incomeDescripcion').value = income.descripcion || '';
        document.getElementById('incomeFuente').value = income.fuente || '';
        document.getElementById('incomeModalTitle').textContent = 'Editar ingreso';
        incomeModal.show();
    } catch (err) {
        showError(err.message);
    }
}

async function handleSaveIncome(event) {
    event.preventDefault();
    if (submitting) return;

    const id = document.getElementById('incomeId').value;
    const monto = parseFloat(document.getElementById('incomeMonto').value);

    if (!Number.isFinite(monto) || monto <= 0) {
        showError('El monto debe ser mayor a 0');
        return;
    }

    const body = {
        monto,
        fecha: document.getElementById('incomeFecha').value,
        descripcion: document.getElementById('incomeDescripcion').value.trim() || null,
        fuente: document.getElementById('incomeFuente').value.trim() || null
    };

    const btn = document.getElementById('incomeSaveBtn');
    const original = btn.innerHTML;
    submitting = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        if (id) {
            await api.put('/incomes/' + id, body);
            showSuccess('Ingreso actualizado');
        } else {
            await api.post('/incomes', body);
            showSuccess('Ingreso creado');
        }
        incomeModal.hide();
        await loadMonthSummary();
        await loadIncomes();
    } catch (err) {
        showError(err.message);
    } finally {
        submitting = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function deleteIncome(id) {
    if (!confirm('¿Eliminar este ingreso?')) return;

    try {
        await api.delete('/incomes/' + id);
        if (lastPageSize === 1 && currentPage > 0) {
            currentPage--;
        }
        showSuccess('Ingreso eliminado');
        await loadMonthSummary();
        await loadIncomes();
    } catch (err) {
        showError('No se pudo eliminar: ' + err.message);
    }
}

function prevIncomePage() {
    if (currentPage > 0) {
        currentPage--;
        loadIncomes();
    }
}

function nextIncomePage() {
    if (currentPage < totalPages - 1) {
        currentPage++;
        loadIncomes();
    }
}

function applyIncomeFilters() {
    currentPage = 0;
    loadIncomes();
}

function clearIncomeFilters() {
    document.getElementById('incomeFrom').value = '';
    document.getElementById('incomeTo').value = '';
    document.getElementById('incomeText').value = '';
    currentPage = 0;
    loadIncomes();
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

// Monthly budgets

let budgetModal;
let categories = [];
let budgets = [];
let summary = null;
let submitting = false;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    budgetModal = new bootstrap.Modal(document.getElementById('budgetModal'));
    document.getElementById('budgetMonth').value = localMonthISO();
    document.getElementById('budgetMonth').addEventListener('change', loadBudgets);

    await loadCategories();
    await loadBudgets();
});

async function loadCategories() {
    try {
        categories = await api.get('/categories');
        const select = document.getElementById('budgetCategory');
        select.innerHTML = '<option value="">Selecciona una categoría</option>' +
            categories.map(c => `<option value="${c.id}">${escapeHtml(c.nombre)}</option>`).join('');
    } catch (err) {
        showError('No se pudieron cargar las categorías: ' + err.message);
    }
}

async function loadBudgets() {
    const tbody = document.getElementById('budgetsTable');
    tbody.innerHTML = `<tr><td colspan="7" class="text-center py-4 text-muted">
        <span class="spinner-border spinner-border-sm me-2"></span>Cargando presupuestos...
    </td></tr>`;

    try {
        summary = await api.get('/budgets?mes=' + encodeURIComponent(getMonth()));
        budgets = summary.presupuestos || [];
        renderSummary(summary);
        renderTable();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center py-4 text-danger">
            Error al cargar presupuestos: ${escapeHtml(err.message)}
        </td></tr>`;
    }
}

function renderSummary(data) {
    document.getElementById('summaryPresupuesto').textContent = formatMoney(data.totalPresupuesto);
    document.getElementById('summaryGastado').textContent = formatMoney(data.totalGastado);
    document.getElementById('summaryDisponible').textContent = formatMoney(data.totalDisponible);
    document.getElementById('summaryPorcentaje').textContent =
        Number(data.porcentaje || 0).toFixed(1) + ' %';

    const progress = document.getElementById('summaryProgressBar');
    const pct = Math.min(Math.max(Number(data.porcentaje || 0), 0), 100);
    progress.style.width = pct + '%';
    progress.className = 'progress-bar ' + progressClass(Number(data.porcentaje || 0));
    progress.parentElement.setAttribute('aria-valuenow', String(pct));
}

function renderTable() {
    const tbody = document.getElementById('budgetsTable');

    if (!budgets.length) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center py-4 text-muted">
            No hay presupuestos para este mes.
            <button class="btn btn-sm btn-link p-0 align-baseline" onclick="openBudgetModal()">
                Crea el primero
            </button>.
        </td></tr>`;
        return;
    }

    tbody.innerHTML = budgets.map(b => {
        const pct = Number(b.porcentaje || 0);
        const width = Math.min(Math.max(pct, 0), 100);
        return `
            <tr>
                <td class="fw-semibold">
                    <span class="d-inline-block rounded-circle me-2"
                          style="width: 12px; height: 12px; background-color: ${escapeHtml(b.categoryColor || '#cbd5e1')};"></span>
                    ${escapeHtml(b.categoryNombre)}
                </td>
                <td class="text-end">${formatMoney(b.monto)}</td>
                <td class="text-end">${formatMoney(b.gastado)}</td>
                <td class="text-end ${Number(b.disponible) < 0 ? 'text-danger fw-semibold' : ''}">
                    ${formatMoney(b.disponible)}
                </td>
                <td>
                    <div class="d-flex align-items-center gap-2">
                        <div class="progress flex-grow-1" style="height: 8px;" role="progressbar"
                             aria-valuemin="0" aria-valuemax="100" aria-valuenow="${width}">
                            <div class="progress-bar ${progressClass(pct)}" style="width: ${width}%"></div>
                        </div>
                        <span class="small text-muted text-nowrap">${pct.toFixed(1)} %</span>
                    </div>
                </td>
                <td>${statusBadge(b.estado)}</td>
                <td class="text-end text-nowrap">
                    <button class="btn btn-sm btn-outline-primary me-1" onclick="editBudget(${b.id})"
                            aria-label="Editar presupuesto" title="Editar">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger" onclick="deleteBudget(${b.id})"
                            aria-label="Eliminar presupuesto" title="Eliminar">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

function statusBadge(estado) {
    if (estado === 'EXCEDIDO') {
        return '<span class="badge bg-danger-subtle text-danger-emphasis">Excedido</span>';
    }
    if (estado === 'ALERTA') {
        return '<span class="badge bg-warning-subtle text-warning-emphasis">Cerca del límite</span>';
    }
    return '<span class="badge bg-success-subtle text-success-emphasis">En orden</span>';
}

function progressClass(pct) {
    if (pct >= 100) return 'bg-danger';
    if (pct >= 80) return 'bg-warning';
    return 'bg-success';
}

function openBudgetModal(id = null) {
    const form = document.getElementById('budgetForm');
    form.reset();

    const budget = id != null ? budgets.find(b => b.id === id) : null;
    const select = document.getElementById('budgetCategory');

    if (budget) {
        select.value = budget.categoryId;
        select.disabled = true;
        document.getElementById('budgetMonto').value = budget.monto;
        document.getElementById('budgetModalTitle').textContent = 'Editar presupuesto';
    } else {
        select.disabled = false;
        document.getElementById('budgetModalTitle').textContent = 'Nuevo presupuesto';
    }

    document.getElementById('budgetModalMonth').textContent = getMonth();
    budgetModal.show();
}

function editBudget(id) {
    openBudgetModal(id);
}

async function handleSaveBudget(event) {
    event.preventDefault();
    if (submitting) return;

    const categoryId = document.getElementById('budgetCategory').value;
    const monto = parseFloat(document.getElementById('budgetMonto').value);

    if (!categoryId) {
        showError('Selecciona una categoría');
        return;
    }
    if (!Number.isFinite(monto) || monto <= 0) {
        showError('El presupuesto debe ser mayor a 0');
        return;
    }

    const btn = document.getElementById('budgetSaveBtn');
    const original = btn.innerHTML;
    submitting = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        await api.post('/budgets', {
            categoryId: parseInt(categoryId),
            mes: getMonth(),
            monto
        });
        budgetModal.hide();
        showSuccess('Presupuesto guardado');
        await loadBudgets();
    } catch (err) {
        showError(err.message);
    } finally {
        submitting = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function deleteBudget(id) {
    const budget = budgets.find(b => b.id === id);
    if (!budget) return;

    if (!confirm(`¿Eliminar el presupuesto de "${budget.categoryNombre}" para ${getMonth()}?`)) return;

    try {
        await api.delete('/budgets/' + id);
        showSuccess('Presupuesto eliminado');
        await loadBudgets();
    } catch (err) {
        showError('No se pudo eliminar: ' + err.message);
    }
}

function getMonth() {
    return document.getElementById('budgetMonth').value || localMonthISO();
}

function localMonthISO() {
    const now = new Date();
    return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
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

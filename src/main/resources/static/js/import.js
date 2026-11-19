// CSV import

let preview = null;
let importing = false;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;
    await ensureProfile();
    await loadCategories();
});

async function loadCategories() {
    try {
        const categories = await api.get('/categories');
        document.getElementById('importCategory').innerHTML =
            '<option value="">Sin categoría</option>' +
            categories.map(c => `<option value="${c.id}">${escapeHtml(c.nombre)}</option>`).join('');
    } catch (err) {
        showError('No se pudieron cargar las categorías: ' + err.message);
    }
}

function buildFormData() {
    const input = document.getElementById('importFile');
    if (!input.files || input.files.length === 0) {
        throw new Error('Selecciona un archivo CSV');
    }

    const formData = new FormData();
    formData.append('file', input.files[0]);
    const categoryId = document.getElementById('importCategory').value;
    if (categoryId) formData.append('categoryId', categoryId);
    formData.append('delimiter', document.getElementById('importDelimiter').value);
    return formData;
}

async function previewImport(event) {
    event.preventDefault();
    const btn = document.getElementById('previewBtn');
    const original = btn.innerHTML;

    try {
        const formData = buildFormData();
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Leyendo...';

        const response = await fetch('/api/expenses/import/preview', {
            method: 'POST',
            headers: { Authorization: 'Bearer ' + getToken() },
            body: formData
        });

        if (response.status === 401) {
            logout();
            return;
        }
        if (!response.ok) {
            throw new Error(await readError(response));
        }

        preview = await response.json();
        renderPreview(preview);
    } catch (err) {
        preview = null;
        document.getElementById('previewArea').classList.add('d-none');
        showError(err.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

function renderPreview(data) {
    document.getElementById('previewArea').classList.remove('d-none');
    document.getElementById('previewTotal').textContent = data.total;
    document.getElementById('previewValid').textContent = data.validas;
    document.getElementById('previewDuplicate').textContent = data.duplicadas;
    document.getElementById('previewError').textContent = data.errores;

    const tbody = document.getElementById('previewTable');
    tbody.innerHTML = (data.filas || []).map(f => {
        let estado = '<span class="badge bg-success-subtle text-success-emphasis">Lista</span>';
        if (f.duplicada) {
            estado = '<span class="badge bg-warning-subtle text-warning-emphasis">Duplicada</span>'
                + `<div class="small text-muted mt-1">${escapeHtml(f.error || '')}</div>`;
        } else if (!f.valida) {
            estado = '<span class="badge bg-danger-subtle text-danger-emphasis">Error</span>'
                + `<div class="small text-muted mt-1">${escapeHtml(f.error || '')}</div>`;
        }

        return `
            <tr>
                <td data-label="#" class="text-muted">${f.numero}</td>
                <td data-label="Fecha" class="text-nowrap">${escapeHtml(f.fecha || '-')}</td>
                <td data-label="Monto" class="text-end fw-semibold">${formatMoney(Number(f.monto || 0))}</td>
                <td data-label="Descripción">${escapeHtml(f.descripcion || '-')}</td>
                <td data-label="Categoría">${escapeHtml(f.categoria || 'Sin categoría')}</td>
                <td data-label="Estado">${estado}</td>
            </tr>
        `;
    }).join('');

    document.getElementById('previewNote').textContent = data.total > 100
        ? 'Mostrando las primeras 100 filas de ' + data.total
        : data.total + ' filas leídas';

    const importBtn = document.getElementById('importBtn');
    importBtn.disabled = data.validas === 0;
    importBtn.innerHTML = `<i class="bi bi-upload"></i> Importar ${data.validas} gastos`;
}

async function confirmImport() {
    if (!preview || importing) return;
    if (!confirm(`¿Importar ${preview.validas} gastos válidos?`)) return;

    const btn = document.getElementById('importBtn');
    const original = btn.innerHTML;
    importing = true;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Importando...';

    try {
        const formData = buildFormData();
        const response = await fetch('/api/expenses/import', {
            method: 'POST',
            headers: { Authorization: 'Bearer ' + getToken() },
            body: formData
        });

        if (response.status === 401) {
            logout();
            return;
        }
        if (!response.ok) {
            throw new Error(await readError(response));
        }

        const result = await response.json();
        const box = document.getElementById('successBox');
        box.innerHTML = `Se importaron <strong>${result.importados}</strong> gastos. `
            + `${result.omitidos} omitidos y ${result.errores} con errores. `
            + '<a href="/expenses.html" class="alert-link">Ver gastos</a>';
        box.classList.remove('d-none');
        document.getElementById('previewArea').classList.add('d-none');
        document.getElementById('importForm').reset();
        preview = null;
    } catch (err) {
        showError(err.message);
    } finally {
        importing = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function readError(response) {
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
    return 'No se pudo procesar la importación (HTTP ' + response.status + ')';
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

function showError(msg) {
    const box = document.getElementById('errorBox');
    box.textContent = msg;
    box.classList.remove('d-none');
    setTimeout(() => box.classList.add('d-none'), 6000);
}

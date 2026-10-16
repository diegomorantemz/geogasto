// Categories page logic

let categoryModal;
let categories = [];

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    const email = getEmail();
    if (email) {
        document.getElementById('userEmail').textContent = email;
    }

    categoryModal = new bootstrap.Modal(document.getElementById('categoryModal'));

    await loadCategories();
});

async function loadCategories() {
    const tbody = document.getElementById('categoriesTable');
    try {
        categories = await api.get('/categories');
        renderTable(categories);
    } catch (err) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center py-4 text-danger">
                    Error al cargar categorías: ${escapeHtml(err.message)}
                </td>
            </tr>`;
    }
}

function renderTable(items) {
    const tbody = document.getElementById('categoriesTable');

    if (!items || items.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center py-4 text-muted">
                    No tienes categorías todavía. Crea la primera con el botón de arriba.
                </td>
            </tr>`;
        return;
    }

    tbody.innerHTML = items.map(c => `
        <tr>
            <td>
                <span class="d-inline-block rounded-circle border"
                      style="width: 24px; height: 24px; background-color: ${escapeHtml(c.color || '#dee2e6')};"></span>
            </td>
            <td class="fw-semibold">${escapeHtml(c.nombre)}</td>
            <td>
                ${c.icono
        ? `<i class="bi ${escapeHtml(c.icono)} fs-5"></i>`
        : '<span class="text-muted">-</span>'}
            </td>
            <td class="text-muted small">${formatDate(c.createdAt)}</td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-primary me-1" onclick="editCategory(${c.id})">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteCategory(${c.id})">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

function openNewCategoryModal() {
    document.getElementById('categoryForm').reset();
    document.getElementById('categoryId').value = '';
    document.getElementById('categoryColor').value = '#0d6efd';
    document.getElementById('categoryModalTitle').textContent = 'Nueva categoría';
    categoryModal.show();
}

function editCategory(id) {
    const c = categories.find(x => x.id === id);
    if (!c) return;

    document.getElementById('categoryId').value = c.id;
    document.getElementById('categoryNombre').value = c.nombre || '';
    document.getElementById('categoryColor').value = c.color || '#0d6efd';
    document.getElementById('categoryIcono').value = c.icono || '';
    document.getElementById('categoryModalTitle').textContent = 'Editar categoría';
    categoryModal.show();
}

async function handleSaveCategory(event) {
    event.preventDefault();

    const id = document.getElementById('categoryId').value;
    const body = {
        nombre: document.getElementById('categoryNombre').value.trim(),
        color: document.getElementById('categoryColor').value,
        icono: document.getElementById('categoryIcono').value.trim() || null
    };

    try {
        if (id) {
            await api.put('/categories/' + id, body);
            showSuccess('Categoría actualizada');
        } else {
            await api.post('/categories', body);
            showSuccess('Categoría creada');
        }
        categoryModal.hide();
        await loadCategories();
    } catch (err) {
        showError(err.message);
    }
}

async function deleteCategory(id) {
    const c = categories.find(x => x.id === id);
    if (!c) return;

    if (!confirm(`¿Eliminar la categoría "${c.nombre}"?\n\nSi tiene gastos asociados, no se podrá eliminar.`)) {
        return;
    }

    try {
        await api.delete('/categories/' + id);
        showSuccess('Categoría eliminada');
        await loadCategories();
    } catch (err) {
        showError('No se pudo eliminar: ' + err.message);
    }
}

// ===== Helpers =====

function formatDate(iso) {
    if (!iso) return '-';
    const d = new Date(iso);
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
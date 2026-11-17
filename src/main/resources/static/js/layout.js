// Navegación compartida por todas las páginas autenticadas.

const NAV_ITEMS = [
    { key: 'inicio', href: '/index.html', icon: 'bi-house', label: 'Inicio' },
    { key: 'gastos', href: '/expenses.html', icon: 'bi-list-ul', label: 'Gastos' },
    { key: 'mapa', href: '/map.html', icon: 'bi-geo-alt', label: 'Mapa' },
    { key: 'categorias', href: '/categories.html', icon: 'bi-tags', label: 'Categorías' },
    { key: 'lugares', href: '/places.html', icon: 'bi-bookmark-star', label: 'Lugares' }
];

function renderAppNav() {
    const host = document.getElementById('appNav');
    if (!host) return;

    const active = host.dataset.active || '';
    const name = (typeof getNombre === 'function' && getNombre())
        || (typeof getEmail === 'function' && getEmail())
        || 'Mi cuenta';

    host.innerHTML = `
        <nav class="navbar navbar-expand-lg navbar-dark bg-primary sticky-top">
            <div class="container">
                <a class="navbar-brand" href="/index.html">
                    <i class="bi bi-wallet2"></i> GeoGasto
                </a>
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse"
                        data-bs-target="#navbarMain" aria-controls="navbarMain"
                        aria-expanded="false" aria-label="Mostrar menú">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div class="collapse navbar-collapse" id="navbarMain">
                    <ul class="navbar-nav me-auto">
                        ${NAV_ITEMS.map(item => `
                            <li class="nav-item">
                                <a class="nav-link ${active === item.key ? 'active' : ''}" href="${item.href}">
                                    <i class="bi ${item.icon}"></i> ${item.label}
                                </a>
                            </li>
                        `).join('')}
                    </ul>
                    <div class="d-flex flex-wrap align-items-center gap-2">
                        <a href="/new-expense.html" class="btn btn-light btn-sm">
                            <i class="bi bi-plus-circle"></i> Nuevo gasto
                        </a>
                        <a href="/profile.html"
                           class="text-white text-decoration-none small d-flex align-items-center gap-1"
                           title="Mi perfil">
                            <i class="bi bi-person-circle fs-5"></i>
                            <span>${navEscape(name)}</span>
                        </a>
                        <button class="btn btn-outline-light btn-sm" onclick="logout()" title="Cerrar sesión">
                            <i class="bi bi-box-arrow-right"></i> Salir
                        </button>
                    </div>
                </div>
            </div>
        </nav>
    `;
}

function navEscape(str) {
    if (str == null) return '';
    return String(str)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

document.addEventListener('DOMContentLoaded', renderAppNav);

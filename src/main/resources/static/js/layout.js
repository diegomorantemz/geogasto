// Navegación compartida: top navbar en escritorio y bottom nav en móvil.

const PRIMARY_NAV = [
    { key: 'gastos', href: '/expenses.html', icon: 'bi-list-ul', label: 'Gastos' },
    { key: 'mapa', href: '/map.html', icon: 'bi-geo-alt', label: 'Mapa' }
];

const SECONDARY_NAV = [
    { key: 'ingresos', href: '/incomes.html', icon: 'bi-cash-coin', label: 'Ingresos' },
    { key: 'presupuestos', href: '/budgets.html', icon: 'bi-bullseye', label: 'Presupuestos' },
    { key: 'recurrentes', href: '/recurring.html', icon: 'bi-arrow-repeat', label: 'Recurrentes' },
    { key: 'categorias', href: '/categories.html', icon: 'bi-tags', label: 'Categorías' },
    { key: 'lugares', href: '/places.html', icon: 'bi-bookmark-star', label: 'Lugares' }
];

const SECONDARY_KEYS = new Set(SECONDARY_NAV.map(item => item.key).concat('perfil'));

function renderAppNav() {
    const host = document.getElementById('appNav');
    if (!host) return;

    document.body.classList.add('app-authenticated');

    const rawActive = host.dataset.active || '';
    const active = rawActive === 'nuevo' ? 'gastos' : rawActive;
    const name = (typeof getNombre === 'function' && getNombre())
        || (typeof getEmail === 'function' && getEmail())
        || 'Mi cuenta';
    const email = (typeof getEmail === 'function' && getEmail()) || '';
    const secondaryActive = SECONDARY_KEYS.has(active);

    host.innerHTML = `
        <nav class="navbar navbar-expand-xl navbar-dark bg-primary app-topnav sticky-top d-none d-xl-flex">
            <div class="container">
                <a class="navbar-brand" href="/index.html">
                    <i class="bi bi-wallet2"></i> GeoGasto
                </a>
                <ul class="navbar-nav me-auto">
                    ${PRIMARY_NAV.map(item => navLink(item, active)).join('')}
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle ${secondaryActive ? 'active' : ''}" href="#"
                           role="button" data-bs-toggle="dropdown" aria-expanded="false">
                            <i class="bi bi-grid"></i> Más
                        </a>
                        <ul class="dropdown-menu">
                            ${SECONDARY_NAV.map(item => `
                                <li>
                                    <a class="dropdown-item ${active === item.key ? 'active' : ''}" href="${item.href}">
                                        <i class="bi ${item.icon} me-2"></i>${item.label}
                                    </a>
                                </li>
                            `).join('')}
                        </ul>
                    </li>
                </ul>
                <div class="d-flex align-items-center gap-2">
                    <a href="/new-expense.html" class="btn btn-light btn-sm">
                        <i class="bi bi-plus-circle"></i> Nuevo gasto
                    </a>
                    <div class="dropdown">
                        <button class="btn btn-outline-light btn-sm dropdown-toggle" type="button"
                                data-bs-toggle="dropdown" aria-expanded="false">
                            <i class="bi bi-person-circle"></i>
                            <span class="nav-user-text">${navEscape(name)}</span>
                        </button>
                        <ul class="dropdown-menu dropdown-menu-end">
                            <li class="px-3 py-2">
                                <div class="small fw-semibold">${navEscape(name)}</div>
                                <div class="small text-muted text-truncate" style="max-width: 220px;">${navEscape(email)}</div>
                            </li>
                            <li><hr class="dropdown-divider"></li>
                            <li><a class="dropdown-item" href="/profile.html"><i class="bi bi-person-gear me-2"></i>Mi perfil</a></li>
                            <li><button type="button" class="dropdown-item text-danger" onclick="logout()">
                                <i class="bi bi-box-arrow-right me-2"></i>Cerrar sesión
                            </button></li>
                        </ul>
                    </div>
                </div>
            </div>
        </nav>

        <header class="mobile-topbar d-xl-none">
            <a class="navbar-brand text-white" href="/index.html">
                <i class="bi bi-wallet2"></i> GeoGasto
            </a>
            <a href="/profile.html" class="mobile-profile-link" aria-label="Mi perfil" title="Mi perfil">
                <i class="bi bi-person-circle"></i>
            </a>
        </header>

        <nav class="mobile-bottom-nav d-xl-none" aria-label="Navegación principal">
            ${mobileNavLink(PRIMARY_NAV[0], active)}
            <a href="/new-expense.html" class="mobile-nav-fab" aria-label="Nuevo gasto">
                <i class="bi bi-plus-lg"></i>
            </a>
            ${mobileNavLink(PRIMARY_NAV[1], active)}
            <button type="button" class="mobile-nav-btn ${secondaryActive ? 'active' : ''}"
                    data-bs-toggle="offcanvas" data-bs-target="#mobileMenu" aria-controls="mobileMenu">
                <i class="bi bi-grid"></i>
                <span>Más</span>
            </button>
        </nav>

        <div class="offcanvas offcanvas-end" tabindex="-1" id="mobileMenu" aria-labelledby="mobileMenuLabel">
            <div class="offcanvas-header">
                <h5 class="offcanvas-title" id="mobileMenuLabel">Menú</h5>
                <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="Cerrar"></button>
            </div>
            <div class="offcanvas-body">
                <div class="mobile-menu-user mb-3">
                    <div class="avatar-circle avatar-sm">${navEscape((name.trim().charAt(0) || 'G').toUpperCase())}</div>
                    <div class="min-w-0">
                        <div class="fw-semibold text-truncate">${navEscape(name)}</div>
                        <div class="small text-muted text-truncate">${navEscape(email)}</div>
                    </div>
                </div>
                <div class="list-group list-group-flush">
                    ${SECONDARY_NAV.map(item => `
                        <a href="${item.href}"
                           class="list-group-item list-group-item-action ${active === item.key ? 'active' : ''}">
                            <i class="bi ${item.icon} me-2"></i>${item.label}
                        </a>
                    `).join('')}
                    <a href="/profile.html" class="list-group-item list-group-item-action ${active === 'perfil' ? 'active' : ''}">
                        <i class="bi bi-person-gear me-2"></i>Mi perfil
                    </a>
                    <button type="button" class="list-group-item list-group-item-action text-danger" onclick="logout()">
                        <i class="bi bi-box-arrow-right me-2"></i>Cerrar sesión
                    </button>
                </div>
            </div>
        </div>
    `;
}

function navLink(item, active) {
    return `
        <li class="nav-item">
            <a class="nav-link ${active === item.key ? 'active' : ''}" href="${item.href}">
                <i class="bi ${item.icon}"></i> ${item.label}
            </a>
        </li>
    `;
}

function mobileNavLink(item, active) {
    return `
        <a href="${item.href}" class="mobile-nav-link ${active === item.key ? 'active' : ''}">
            <i class="bi ${item.icon}"></i>
            <span>${item.label}</span>
        </a>
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

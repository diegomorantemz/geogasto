// Dashboard logic

// Respeta la preferencia de movimiento reducido del sistema.
if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    Chart.defaults.animation = false;
}

let chartCategorias;
let chartMeses;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    document.getElementById('userName').textContent = getNombre() || 'usuario';

    initCurrencySelector();
    await loadStats();
    await loadBudgetSummary();
});

function initCurrencySelector() {
    const select = document.getElementById('currencySelect');
    if (!select) return;

    select.value = getMoneda();
    select.addEventListener('change', () => changeCurrency(select.value));
}

async function changeCurrency(moneda) {
    try {
        await api.put('/auth/me', { moneda });
        localStorage.setItem('geogasto_moneda', moneda);
        window.location.reload();
    } catch (err) {
        showStatsError('No se pudo cambiar la moneda: ' + err.message);
        document.getElementById('currencySelect').value = getMoneda();
    }
}

async function loadStats() {
    const errorBox = document.getElementById('statsError');
    errorBox.classList.add('d-none');

    try {
        const stats = await api.get('/stats');

        setText('totalMes', formatMoney(stats.totalMes));
        setText('totalIngresos', formatMoney(stats.totalIngresosMes));
        setText('cantidadGastos', stats.cantidadGastos ?? 0);
        setText('cantidadCategorias', stats.cantidadCategorias ?? 0);
        setText('cantidadLugares', stats.cantidadLugares ?? 0);
        setText('totalMesAnterior', formatMoney(stats.totalMesAnterior));
        setText('promedioDiario', formatMoney(stats.promedioDiario));
        setText('categoriaTop', stats.categoriaTopNombre || 'Sin datos');
        setText('categoriaTopTotal', stats.categoriaTopTotal != null
            ? formatMoney(stats.categoriaTopTotal)
            : '');
        renderBalance(stats.balanceMes, stats.tasaAhorro);
        renderVariacion(stats.variacionPorcentual);

        renderCategoryChart(stats.gastosPorCategoria || []);
        renderMonthChart(stats.gastosPorMes || []);
    } catch (err) {
        showStatsError('No se pudieron cargar las estadísticas: ' + err.message);
    }
}

async function loadBudgetSummary() {
    const card = document.getElementById('budgetCard');
    try {
        const data = await api.get('/budgets');
        const items = data.presupuestos || [];
        if (!items.length) {
            card.classList.add('d-none');
            return;
        }

        card.classList.remove('d-none');
        setText('budgetTotal', formatMoney(data.totalPresupuesto));
        setText('budgetSpent', formatMoney(data.totalGastado));
        setText('budgetAvailable', formatMoney(data.totalDisponible));

        const pct = Number(data.porcentaje || 0);
        const width = Math.min(Math.max(pct, 0), 100);
        const bar = document.getElementById('budgetProgressBar');
        bar.style.width = width + '%';
        bar.className = 'progress-bar ' + (pct >= 100 ? 'bg-danger' : pct >= 80 ? 'bg-warning' : 'bg-success');
        bar.parentElement.setAttribute('aria-valuenow', String(width));

        const alerts = items.filter(i => i.estado !== 'OK');
        const box = document.getElementById('budgetAlerts');
        if (!alerts.length) {
            box.innerHTML = '<div class="small text-success"><i class="bi bi-check-circle"></i> Todo bajo control.</div>';
            return;
        }

        box.innerHTML = alerts.map(a => `
            <div class="d-flex justify-content-between align-items-center border-top py-2">
                <span class="small">
                    <i class="bi bi-exclamation-triangle ${a.estado === 'EXCEDIDO' ? 'text-danger' : 'text-warning'}"></i>
                    ${escapeHtml(a.categoryNombre)}
                </span>
                <span class="badge ${a.estado === 'EXCEDIDO' ? 'bg-danger-subtle text-danger-emphasis' : 'bg-warning-subtle text-warning-emphasis'}">
                    ${Number(a.porcentaje).toFixed(1)} %
                </span>
            </div>
        `).join('');
    } catch (err) {
        card.classList.add('d-none');
    }
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

function showStatsError(message) {
    const box = document.getElementById('statsError');
    box.textContent = message;
    box.classList.remove('d-none');
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

function renderBalance(balance, tasaAhorro) {
    const el = document.getElementById('balanceMes');
    if (!el) return;

    const valor = Number(balance || 0);
    el.textContent = formatMoney(valor);
    el.className = 'stat-value ' + (valor >= 0 ? 'text-success' : 'text-danger');

    const ahorro = document.getElementById('tasaAhorro');
    if (ahorro) {
        ahorro.textContent = tasaAhorro != null
            ? 'Tasa de ahorro: ' + Number(tasaAhorro).toFixed(1) + ' %'
            : 'Sin ingresos registrados este mes';
    }
}

function renderVariacion(value) {
    const el = document.getElementById('variacion');
    if (!el) return;

    if (value == null) {
        el.textContent = '—';
        el.className = 'mb-0 text-muted';
        return;
    }

    const numero = Number(value);
    const sube = numero > 0;
    const igual = numero === 0;
    el.textContent = (sube ? '↑ +' : igual ? '→ ' : '↓ ') + numero.toFixed(1) + '%';
    el.className = 'mb-0 fw-semibold ' + (igual ? 'text-muted' : sube ? 'text-danger' : 'text-success');
}

function renderCategoryChart(items) {
    const canvas = document.getElementById('chartCategorias');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    if (items.length === 0) {
        canvas.parentNode.innerHTML = '<p class="text-center text-muted mt-5">Sin datos este mes</p>';
        return;
    }

    if (chartCategorias) chartCategorias.destroy();

    chartCategorias = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: items.map(i => i.categoryNombre),
            datasets: [{
                data: items.map(i => Number(i.total)),
                backgroundColor: items.map(i => i.color || '#2563eb'),
                borderWidth: 2,
                borderColor: '#fff'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '62%',
            plugins: {
                legend: { position: 'bottom' },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ctx.label + ': ' + formatMoney(ctx.parsed)
                    }
                }
            }
        }
    });
}

function renderMonthChart(items) {
    const canvas = document.getElementById('chartMeses');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    if (items.length === 0) {
        canvas.parentNode.innerHTML = '<p class="text-center text-muted mt-5">Sin datos</p>';
        return;
    }

    if (chartMeses) chartMeses.destroy();

    chartMeses = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: items.map(i => i.mesLabel),
            datasets: [
                {
                    type: 'bar',
                    label: 'Gastos',
                    data: items.map(i => Number(i.total)),
                    backgroundColor: '#2563eb',
                    borderRadius: 6,
                    order: 2
                },
                {
                    type: 'line',
                    label: 'Ingresos',
                    data: items.map(i => Number(i.ingresos || 0)),
                    borderColor: '#10b981',
                    backgroundColor: '#10b981',
                    tension: 0.3,
                    pointRadius: 3,
                    borderWidth: 2,
                    fill: false,
                    order: 1
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'bottom' },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ctx.dataset.label + ': ' + formatMoney(ctx.parsed.y)
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: (v) => formatMoney(v)
                    }
                }
            }
        }
    });
}

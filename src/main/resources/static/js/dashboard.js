// Dashboard logic

// Respeta la preferencia de movimiento reducido del sistema.
if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    Chart.defaults.animation = false;
}

let chartCategorias;
let chartMeses;
let lastCategoryItems = null;
let lastMonthItems = null;

function chartTheme() {
    const dark = document.documentElement.getAttribute('data-theme') === 'dark';
    return {
        dark,
        text: dark ? '#f2f0ea' : '#1b1a17',
        muted: dark ? '#9c968b' : '#6f6a61',
        grid: dark ? 'rgba(242, 240, 234, .10)' : 'rgba(27, 26, 23, .08)',
        surface: dark ? '#171614' : '#fbfaf7',
        bar: dark ? '#e8e4dc' : '#1b1a17',
        income: dark ? '#9cc290' : '#3f6b3a'
    };
}

function applyChartTheme() {
    if (typeof Chart === 'undefined') return;
    const theme = chartTheme();

    Chart.defaults.color = theme.muted;
    Chart.defaults.borderColor = theme.grid;
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;

    if (Chart.defaults.plugins && Chart.defaults.plugins.tooltip) {
        Chart.defaults.plugins.tooltip.backgroundColor = theme.dark ? '#f2f0ea' : '#1b1a17';
        Chart.defaults.plugins.tooltip.titleColor = theme.dark ? '#151412' : '#fbfaf7';
        Chart.defaults.plugins.tooltip.bodyColor = theme.dark ? '#151412' : '#fbfaf7';
    }
}

applyChartTheme();

document.addEventListener('geogasto:themechange', () => {
    applyChartTheme();
    if (lastCategoryItems !== null) {
        renderCategoryChart(lastCategoryItems);
    }
    if (lastMonthItems !== null) {
        renderMonthChart(lastMonthItems);
    }
});

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    document.getElementById('userName').textContent = getNombre() || 'usuario';

    initCurrencySelector();
    await loadStats();
    await loadBudgetSummary();
    await loadRecurringSummary();
});

async function downloadMonthlyReport() {
    try {
        const mes = todayProfileISO().slice(0, 7);
        const response = await fetch('/api/reports/monthly?mes=' + encodeURIComponent(mes), {
            headers: { Authorization: 'Bearer ' + getToken() }
        });
        if (response.status === 401) {
            logout();
            return;
        }
        if (!response.ok) {
            throw new Error('No se pudo generar el reporte');
        }

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'geogasto-' + mes + '.pdf';
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    } catch (err) {
        showStatsError(err.message);
    }
}

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
        if (!items.length && data.presupuestoGeneral == null) {
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

        const generalLine = document.getElementById('budgetGeneralLine');
        if (data.presupuestoGeneral != null) {
            generalLine.textContent = `General: ${formatMoney(data.presupuestoGeneral)} · `
                + `gastado ${formatMoney(data.gastadoGeneral)} · `
                + `${Number(data.porcentajeGeneral || 0).toFixed(1)} %`;
            generalLine.classList.remove('d-none');
        } else {
            generalLine.classList.add('d-none');
        }

        const alerts = items.filter(i => i.estado !== 'OK');
        if (data.estadoGeneral && data.estadoGeneral !== 'OK') {
            alerts.unshift({
                categoryNombre: 'General',
                porcentaje: data.porcentajeGeneral,
                estado: data.estadoGeneral
            });
        }
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

async function loadRecurringSummary() {
    const card = document.getElementById('recurringCard');
    try {
        const items = (await api.get('/recurring-expenses'))
            .filter(r => r.activo)
            .sort((a, b) => String(a.proximaFecha).localeCompare(String(b.proximaFecha)));

        if (!items.length) {
            card.classList.add('d-none');
            return;
        }

        card.classList.remove('d-none');
        const labels = { DAILY: 'Diario', WEEKLY: 'Semanal', MONTHLY: 'Mensual', YEARLY: 'Anual' };
        document.getElementById('recurringUpcoming').innerHTML = items.slice(0, 4).map(r => `
            <div class="d-flex justify-content-between align-items-center border-top py-2 gap-3">
                <div>
                    <div class="fw-semibold">${escapeHtml(r.descripcion || 'Sin descripción')}</div>
                    <div class="small text-muted">
                        ${labels[r.frecuencia] || r.frecuencia}
                        ${r.categoryNombre ? ' · ' + escapeHtml(r.categoryNombre) : ''}
                    </div>
                </div>
                <div class="text-end text-nowrap">
                    <div class="fw-semibold">${formatMoney(r.monto)}</div>
                    <div class="small text-muted">${formatDate(r.proximaFecha)}</div>
                </div>
            </div>
        `).join('');
    } catch (err) {
        card.classList.add('d-none');
    }
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
    lastCategoryItems = items;
    const canvas = document.getElementById('chartCategorias');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const theme = chartTheme();

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
                backgroundColor: items.map(i => i.color || '#6f6a61'),
                borderWidth: 2,
                borderColor: theme.surface
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
    lastMonthItems = items;
    const canvas = document.getElementById('chartMeses');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const theme = chartTheme();

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
                    backgroundColor: theme.bar,
                    borderRadius: 6,
                    order: 2
                },
                {
                    type: 'line',
                    label: 'Ingresos',
                    data: items.map(i => Number(i.ingresos || 0)),
                    borderColor: theme.income,
                    backgroundColor: theme.income,
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

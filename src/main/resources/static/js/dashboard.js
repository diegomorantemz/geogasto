// Dashboard logic

let chartCategorias;
let chartMeses;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile();

    document.getElementById('userName').textContent = getNombre() || 'usuario';

    initCurrencySelector();
    await loadStats();
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
        setText('cantidadGastos', stats.cantidadGastos ?? 0);
        setText('cantidadCategorias', stats.cantidadCategorias ?? 0);
        setText('cantidadLugares', stats.cantidadLugares ?? 0);
        setText('totalMesAnterior', formatMoney(stats.totalMesAnterior));
        setText('promedioDiario', formatMoney(stats.promedioDiario));
        setText('categoriaTop', stats.categoriaTopNombre || 'Sin datos');
        setText('categoriaTopTotal', stats.categoriaTopTotal != null
            ? formatMoney(stats.categoriaTopTotal)
            : '');
        renderVariacion(stats.variacionPorcentual);

        renderCategoryChart(stats.gastosPorCategoria || []);
        renderMonthChart(stats.gastosPorMes || []);
    } catch (err) {
        showStatsError('No se pudieron cargar las estadísticas: ' + err.message);
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
            datasets: [{
                label: 'Gastos',
                data: items.map(i => Number(i.total)),
                backgroundColor: '#2563eb',
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (ctx) => formatMoney(ctx.parsed.y)
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

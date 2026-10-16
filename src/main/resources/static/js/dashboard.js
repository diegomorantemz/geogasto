// Dashboard logic

let chartCategorias;
let chartMeses;

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    const email = getEmail();
    if (email) {
        document.getElementById('userEmail').textContent = email;
        document.getElementById('userName').textContent = email.split('@')[0];
    }

    await loadStats();
});

async function loadStats() {
    try {
        const stats = await api.get('/stats');

        // Contadores
        document.getElementById('totalMes').textContent = formatMoney(stats.totalMes);
        document.getElementById('cantidadGastos').textContent = stats.cantidadGastos;
        document.getElementById('cantidadCategorias').textContent = stats.cantidadCategorias;

        // Gráfico por categoría
        renderCategoryChart(stats.gastosPorCategoria || []);

        // Gráfico por mes
        renderMonthChart(stats.gastosPorMes || []);

    } catch (err) {
        console.error('Error loading stats:', err);
    }
}

function renderCategoryChart(items) {
    const ctx = document.getElementById('chartCategorias').getContext('2d');

    if (items.length === 0) {
        ctx.canvas.parentNode.innerHTML = '<p class="text-center text-muted mt-5">Sin datos este mes</p>';
        return;
    }

    if (chartCategorias) chartCategorias.destroy();

    chartCategorias = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: items.map(i => i.categoryNombre),
            datasets: [{
                data: items.map(i => Number(i.total)),
                backgroundColor: items.map(i => i.color || '#0d6efd'),
                borderWidth: 2,
                borderColor: '#fff'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'bottom' },
                tooltip: {
                    callbacks: {
                        label: (ctx) => {
                            const value = ctx.parsed;
                            return ctx.label + ': S/ ' + value.toFixed(2);
                        }
                    }
                }
            }
        }
    });
}

function renderMonthChart(items) {
    const ctx = document.getElementById('chartMeses').getContext('2d');

    if (items.length === 0) {
        ctx.canvas.parentNode.innerHTML = '<p class="text-center text-muted mt-5">Sin datos</p>';
        return;
    }

    if (chartMeses) chartMeses.destroy();

    chartMeses = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: items.map(i => i.mesLabel),
            datasets: [{
                label: 'Gastos (S/)',
                data: items.map(i => Number(i.total)),
                backgroundColor: '#0d6efd',
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
                        label: (ctx) => 'S/ ' + ctx.parsed.y.toFixed(2)
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: (v) => 'S/ ' + v
                    }
                }
            }
        }
    });
}

function formatMoney(amount) {
    return 'S/ ' + Number(amount).toFixed(2);
}
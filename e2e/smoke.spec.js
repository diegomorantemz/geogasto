const { test, expect } = require('@playwright/test');

test('flujo principal: registro, presupuesto, notificación, ingreso y recurrencia', async ({ page }) => {
    const email = `e2e-${Date.now()}@test.com`;

    await page.goto('/register.html');
    await page.fill('#nombre', 'E2E GeoGasto');
    await page.fill('#email', email);
    await page.fill('#password', 'secreto123');
    await page.selectOption('#moneda', 'PEN');
    await page.selectOption('#timezone', 'America/Lima');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/index.html', { timeout: 15_000 });
    await expect(page.locator('#userName')).toContainText('E2E GeoGasto');

    // Presupuesto
    await page.goto('/budgets.html');
    await page.waitForFunction(() => document.querySelectorAll('#budgetCategory option').length > 1);
    await page.click('button:has-text("Nuevo presupuesto")');
    await page.waitForSelector('#budgetModal.show');
    await page.selectOption('#budgetCategory', { label: 'Comida' });
    await page.fill('#budgetMonto', '100');
    await page.click('#budgetSaveBtn');
    await page.waitForFunction(() => !document.getElementById('budgetModal').classList.contains('show'));
    await expect(page.locator('#budgetsTable')).toContainText('Comida');
    await expect(page.locator('#budgetsTable')).toContainText('S/ 100.00');

    // Gasto que dispara alerta de presupuesto
    const ids = await page.evaluate(async () => {
        const token = localStorage.getItem('geogasto_token');
        const categories = await fetch('/api/categories', {
            headers: { Authorization: 'Bearer ' + token }
        }).then(r => r.json());
        const comida = categories.find(c => c.nombre === 'Comida');
        const now = new Date();
        now.setDate(now.getDate() - 1);
        const fecha = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
            .toISOString().slice(0, 10);
        await fetch('/api/expenses', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: 'Bearer ' + token
            },
            body: JSON.stringify({
                monto: 85,
                fecha,
                descripcion: 'Gasto E2E',
                categoryId: comida.id
            })
        });
        return { comida: comida.id };
    });
    expect(ids.comida).toBeTruthy();

    // Notificación in-app
    await page.goto('/index.html');
    await page.waitForSelector('#budgetCard:not(.d-none)');
    await page.waitForSelector('.notification-badge:not(.d-none)', { timeout: 15_000 });
    await page.locator('.app-topnav .notification-bell').click();
    await page.waitForSelector('#notificationsMenu.show');
    await expect(page.locator('#notificationsList')).toContainText('Presupuesto');

    // Ingreso
    await page.goto('/incomes.html');
    await page.click('button:has-text("Nuevo ingreso")');
    await page.waitForSelector('#incomeModal.show');
    await page.fill('#incomeMonto', '500');
    await page.fill('#incomeDescripcion', 'Ingreso E2E');
    await page.fill('#incomeFuente', 'Salario');
    await page.click('#incomeSaveBtn');
    await page.waitForFunction(() => !document.getElementById('incomeModal').classList.contains('show'));
    await expect(page.locator('#incomesTable')).toContainText('Ingreso E2E');

    // Recurrencia futura
    await page.goto('/recurring.html');
    await page.waitForFunction(() => document.querySelectorAll('#recurringCategory option').length > 1);
    await page.click('button:has-text("Nueva recurrencia")');
    await page.waitForSelector('#recurringModal.show');
    await page.fill('#recurringMonto', '50');
    await page.fill('#recurringDescripcion', 'Recurrente E2E');
    await page.selectOption('#recurringFrecuencia', 'MONTHLY');
    await page.selectOption('#recurringCategory', { label: 'Comida' });
    const future = await page.evaluate(() => {
        const d = new Date();
        d.setDate(d.getDate() + 10);
        return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    });
    await page.fill('#recurringFechaInicio', future);
    await page.click('#recurringSaveBtn');
    await page.waitForFunction(() => !document.getElementById('recurringModal').classList.contains('show'));
    await expect(page.locator('#recurringTable')).toContainText('Recurrente E2E');
});

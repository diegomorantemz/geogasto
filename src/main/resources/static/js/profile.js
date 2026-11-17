// Profile and settings

const TIMEZONES = [
    'America/Lima',
    'America/Bogota',
    'America/Mexico_City',
    'America/Santiago',
    'America/Argentina/Buenos_Aires',
    'America/Sao_Paulo',
    'Europe/Madrid',
    'UTC'
];

document.addEventListener('DOMContentLoaded', async () => {
    if (!requireLogin()) return;

    await ensureProfile(true);
    await loadProfile();
});

async function loadProfile() {
    try {
        const me = await api.get('/auth/me');
        document.getElementById('profileNombre').value = me.nombre || '';
        document.getElementById('profileEmail').value = me.email || '';
        document.getElementById('profileMoneda').value = me.moneda || 'USD';

        const tzSelect = document.getElementById('profileTimezone');
        if (me.timezone && !TIMEZONES.includes(me.timezone)) {
            const option = document.createElement('option');
            option.value = me.timezone;
            option.textContent = me.timezone;
            tzSelect.appendChild(option);
        }
        tzSelect.value = me.timezone || 'America/Lima';

        updateIdentity(me.nombre, me.email);
    } catch (err) {
        showError('No se pudo cargar tu perfil: ' + err.message);
    }
}

function updateIdentity(nombre, email) {
    const display = nombre || email || 'Mi cuenta';
    document.getElementById('profileDisplayName').textContent = display;
    document.getElementById('profileDisplayEmail').textContent = email || '';
    document.getElementById('profileAvatar').textContent =
        (display.trim().charAt(0) || 'G').toUpperCase();
}

async function handleSaveProfile(event) {
    event.preventDefault();

    const nombre = document.getElementById('profileNombre').value.trim();
    const moneda = document.getElementById('profileMoneda').value;
    const timezone = document.getElementById('profileTimezone').value;

    if (!nombre) {
        showError('El nombre es obligatorio');
        return;
    }

    const btn = document.getElementById('profileSaveBtn');
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        const me = await api.put('/auth/me', { nombre, moneda, timezone });
        localStorage.setItem('geogasto_nombre', me.nombre);
        localStorage.setItem('geogasto_moneda', me.moneda);
        localStorage.setItem('geogasto_timezone', me.timezone);
        updateIdentity(me.nombre, me.email);
        if (typeof renderAppNav === 'function') renderAppNav();
        showSuccess('Perfil actualizado');
    } catch (err) {
        showError(err.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function handleChangePassword(event) {
    event.preventDefault();

    const currentPassword = document.getElementById('currentPassword').value;
    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;

    if (newPassword !== confirmPassword) {
        showError('La nueva contraseña y su confirmación no coinciden');
        return;
    }

    const btn = document.getElementById('passwordSaveBtn');
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Cambiando...';

    try {
        await api.put('/auth/me/password', { currentPassword, newPassword });
        document.getElementById('passwordForm').reset();
        showSuccess('Contraseña actualizada');
    } catch (err) {
        showError(err.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = original;
    }
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
    setTimeout(() => box.classList.add('d-none'), 6000);
}

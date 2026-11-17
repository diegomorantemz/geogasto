// Authentication helpers

const TOKEN_KEY = 'geogasto_token';
const EMAIL_KEY = 'geogasto_email';
const NAME_KEY = 'geogasto_nombre';
const CURRENCY_KEY = 'geogasto_moneda';
const TIMEZONE_KEY = 'geogasto_timezone';

function saveSession(token, email, moneda, nombre, timezone) {
    localStorage.setItem(TOKEN_KEY, token);
    if (email) localStorage.setItem(EMAIL_KEY, email);
    if (nombre) localStorage.setItem(NAME_KEY, nombre);
    if (moneda) localStorage.setItem(CURRENCY_KEY, moneda);
    if (timezone) localStorage.setItem(TIMEZONE_KEY, timezone);
}

function getEmail() {
    return localStorage.getItem(EMAIL_KEY);
}

function getNombre() {
    return localStorage.getItem(NAME_KEY) || getEmail();
}

function getMoneda() {
    return localStorage.getItem(CURRENCY_KEY) || 'USD';
}

function getTimezone() {
    return localStorage.getItem(TIMEZONE_KEY) || 'America/Lima';
}

function currencySymbol(moneda = getMoneda()) {
    const symbols = {
        USD: '$',
        PEN: 'S/',
        EUR: '€',
        MXN: '$',
        COP: '$',
        ARS: '$',
        CLP: '$',
        BRL: 'R$'
    };
    return symbols[moneda] || moneda;
}

// Completa los datos de perfil para sesiones creadas antes de existir estos campos.
async function ensureProfile(force = false) {
    if (!localStorage.getItem(TOKEN_KEY)) return;

    if (!force
        && localStorage.getItem(CURRENCY_KEY)
        && localStorage.getItem(NAME_KEY)
        && localStorage.getItem(TIMEZONE_KEY)) {
        return;
    }

    try {
        const me = await api.get('/auth/me');
        if (me.email) localStorage.setItem(EMAIL_KEY, me.email);
        if (me.nombre) localStorage.setItem(NAME_KEY, me.nombre);
        if (me.moneda) localStorage.setItem(CURRENCY_KEY, me.moneda);
        if (me.timezone) localStorage.setItem(TIMEZONE_KEY, me.timezone);
    } catch (e) {
        // Si falla, se mantienen los valores por defecto.
    }
}

function isLoggedIn() {
    return !!localStorage.getItem(TOKEN_KEY);
}

function logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(NAME_KEY);
    localStorage.removeItem(CURRENCY_KEY);
    localStorage.removeItem(TIMEZONE_KEY);
    window.location.href = '/login.html';
}

function requireLogin() {
    if (!isLoggedIn()) {
        window.location.href = '/login.html';
        return false;
    }
    return true;
}

// ===== Login form handler =====
async function handleLogin(event) {
    event.preventDefault();
    const form = event.target;
    const email = form.email.value.trim();
    const password = form.password.value;
    const errorBox = document.getElementById('errorBox');

    errorBox.classList.add('d-none');

    try {
        const data = await api.post('/auth/login', { email, password });
        saveSession(data.token, data.email || email, data.moneda, data.nombre, data.timezone);
        window.location.href = '/index.html';
    } catch (err) {
        errorBox.textContent = err.message || 'Email o contraseña incorrectos';
        errorBox.classList.remove('d-none');
    }
}

// ===== Register form handler =====
async function handleRegister(event) {
    event.preventDefault();
    const form = event.target;
    const errorBox = document.getElementById('errorBox');

    errorBox.classList.add('d-none');

    const body = {
        email: form.email.value.trim(),
        password: form.password.value,
        nombre: form.nombre.value.trim()
    };

    if (form.moneda) body.moneda = form.moneda.value;
    if (form.timezone) body.timezone = form.timezone.value;

    try {
        const data = await api.post('/auth/register', body);
        saveSession(data.token, data.email || body.email, data.moneda, data.nombre, data.timezone);
        window.location.href = '/index.html';
    } catch (err) {
        errorBox.textContent = err.message || 'Error al registrarse';
        errorBox.classList.remove('d-none');
    }
}

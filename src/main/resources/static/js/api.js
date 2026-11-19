// API client with JWT Authorization header
const API_BASE = '/api';

function getToken() {
    return localStorage.getItem('geogasto_token');
}

async function request(url, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {})
    };

    const token = getToken();
    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }

    let response;
    try {
        response = await fetch(API_BASE + url, { ...options, headers });
    } catch (e) {
        throw new Error('No hay conexión con el servidor. Revisa tu red e intenta de nuevo.');
    }

    const isAuthAttempt = url.startsWith('/auth/login') || url.startsWith('/auth/register');

    // Si el token expiró o es inválido, limpiamos la sesión.
    // En login/registro no: un 401 significa credenciales incorrectas.
    if (response.status === 401 && !isAuthAttempt) {
        localStorage.removeItem('geogasto_token');
        localStorage.removeItem('geogasto_email');
        localStorage.removeItem('geogasto_nombre');
        localStorage.removeItem('geogasto_moneda');
        localStorage.removeItem('geogasto_timezone');
        if (!window.location.pathname.endsWith('login.html')) {
            window.location.href = '/login.html';
        }
        throw new Error('Tu sesión expiró. Inicia sesión de nuevo.');
    }

    if (response.status === 204) {
        return null;
    }

    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (e) {
            data = null;
        }
    }

    if (!response.ok) {
        const message = firstErrorMessage(data)
            || (response.status === 403 ? 'No tienes permiso para realizar esta acción' : null)
            || 'La operación no se pudo completar (HTTP ' + response.status + ')';
        const error = new Error(message);
        error.status = response.status;
        if (data && data.errors && typeof data.errors === 'object') {
            error.fields = data.errors;
        }
        throw error;
    }

    return data;
}

function firstErrorMessage(data) {
    if (!data) return null;
    if (data.errors && typeof data.errors === 'object') {
        const first = Object.values(data.errors)[0];
        if (first) return first;
    }
    if (data.message) return data.message;
    return null;
}

const api = {
    get: (url) => request(url),
    post: (url, body) => request(url, { method: 'POST', body: JSON.stringify(body ?? {}) }),
    put: (url, body) => request(url, { method: 'PUT', body: JSON.stringify(body ?? {}) }),
    delete: (url) => request(url, { method: 'DELETE' })
};

// Fecha de hoy en la zona horaria del navegador (no en UTC).
function todayLocalISO() {
    const now = new Date();
    const offsetMs = now.getTimezoneOffset() * 60000;
    return new Date(now.getTime() - offsetMs).toISOString().split('T')[0];
}

// Fecha de hoy según la zona horaria del perfil, que es la que usa el backend.
function todayProfileISO() {
    const timezone = typeof getTimezone === 'function' ? getTimezone() : null;
    if (!timezone) {
        return todayLocalISO();
    }

    try {
        const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: timezone,
            year: 'numeric',
            month: '2-digit',
            day: '2-digit'
        }).formatToParts(new Date());
        const values = {};
        parts.forEach(part => { values[part.type] = part.value; });
        if (values.year && values.month && values.day) {
            return `${values.year}-${values.month}-${values.day}`;
        }
    } catch (ignored) {
        // Fallback al navegador.
    }
    return todayLocalISO();
}

// Formato de dinero consistente en todas las páginas.
function formatMoney(amount, moneda = (typeof getMoneda === 'function' ? getMoneda() : 'USD')) {
    const value = Number(amount || 0);
    const formatted = new Intl.NumberFormat('es-PE', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(value);
    const symbol = typeof currencySymbol === 'function' ? currencySymbol(moneda) : moneda;
    return symbol + ' ' + formatted;
}

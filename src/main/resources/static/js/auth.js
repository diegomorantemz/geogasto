// PWA: manifest, theme color y service worker.
(function initPwa() {
    if (!document.querySelector('link[rel="manifest"]')) {
        const link = document.createElement('link');
        link.rel = 'manifest';
        link.href = '/manifest.webmanifest';
        document.head.appendChild(link);
    }
    if ('serviceWorker' in navigator &&
        (location.protocol === 'https:' || ['localhost', '127.0.0.1'].includes(location.hostname))) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('/sw.js').catch(() => {
                // La app sigue funcionando sin service worker.
            });
        });
    }
})();

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

const EYE_ICON = `
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M2.8 12S6.2 6.5 12 6.5 21.2 12 21.2 12 17.8 17.5 12 17.5 2.8 12 2.8 12Z"
              stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
        <circle cx="12" cy="12" r="2.7" stroke="currentColor" stroke-width="1.6"/>
    </svg>`;

const EYE_OFF_ICON = `
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M4 4l16 16M9.5 9.7a3 3 0 0 0 4.2 4.2M6.2 6.9C3.9 8.6 2.8 12 2.8 12s3.4 5.5 9.2 5.5c1.3 0 2.5-.3 3.6-.8M11.2 6.6c.3 0 .5-.1.8-.1 5.8 0 9.2 5.5 9.2 5.5s-.7 1.2-1.9 2.4"
              stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`;

function togglePassword(inputId, button) {
    const input = document.getElementById(inputId);
    if (!input) return;

    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.innerHTML = show ? EYE_OFF_ICON : EYE_ICON;
    const label = show ? 'Ocultar contraseña' : 'Mostrar contraseña';
    button.setAttribute('aria-label', label);
    button.setAttribute('title', label);
}

function setAuthButtonLoading(button, loading, loadingText) {
    if (!button) return;
    if (loading) {
        button.dataset.originalHtml = button.innerHTML;
        button.disabled = true;
        button.innerHTML = `<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span><span>${loadingText}</span>`;
    } else {
        button.disabled = false;
        if (button.dataset.originalHtml) {
            button.innerHTML = button.dataset.originalHtml;
        }
    }
}

// ===== Validación de formularios de acceso =====
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

function setFieldError(input, message) {
    input.classList.add('is-invalid');
    input.setAttribute('aria-invalid', 'true');
    const box = document.getElementById(`${input.id}Error`);
    if (box) {
        box.textContent = message;
        box.classList.add('is-visible');
    }
}

function clearFieldError(input) {
    input.classList.remove('is-invalid');
    input.removeAttribute('aria-invalid');
    const box = document.getElementById(`${input.id}Error`);
    if (box) {
        box.textContent = '';
        box.classList.remove('is-visible');
    }
}

function applyValidation(input, message) {
    if (!input) return true;
    if (message) {
        setFieldError(input, message);
        return false;
    }
    clearFieldError(input);
    return true;
}

function validateRequired(input, message) {
    if (!input.value.trim()) return message;
    return '';
}

function validateEmail(input) {
    const value = input.value.trim();
    if (!value) return 'Ingresa tu email';
    if (!EMAIL_PATTERN.test(value)) return 'Ingresa un email válido';
    if (value.length > 100) return 'El email no puede superar 100 caracteres';
    return '';
}

function validateLoginPassword(input) {
    if (!input.value) return 'Ingresa tu contraseña';
    if (input.value.length > 72) return 'La contraseña no puede superar 72 caracteres';
    return '';
}

function validateNewPassword(input) {
    if (!input.value) return 'Crea una contraseña';
    if (input.value.length < 8) return 'La contraseña debe tener al menos 8 caracteres';
    if (input.value.length > 72) return 'La contraseña no puede superar 72 caracteres';
    return '';
}

function validateConfirmPassword(input, passwordInput) {
    if (!input.value) return 'Confirma tu contraseña';
    if (input.value !== passwordInput.value) return 'Las contraseñas no coinciden';
    return '';
}

function showServerFieldErrors(err, form, errorBox, fallback) {
    errorBox.classList.add('d-none');
    const fields = err && err.fields ? err.fields : null;
    let firstInvalid = null;

    if (fields) {
        Object.keys(fields).forEach((name) => {
            const input = form.elements.namedItem(name);
            if (input && input.id) {
                setFieldError(input, fields[name]);
                if (!firstInvalid) firstInvalid = input;
            }
        });
    }

    if (firstInvalid) {
        firstInvalid.focus();
        return;
    }

    errorBox.textContent = (err && err.message) || fallback;
    errorBox.classList.remove('d-none');
}

function renderPasswordStrength(value) {
    const container = document.getElementById('passwordStrength');
    const label = document.getElementById('passwordStrengthLabel');
    if (!container || !label) return;

    if (!value) {
        container.classList.remove('is-visible');
        container.removeAttribute('data-score');
        label.textContent = 'Mínimo 8 caracteres';
        return;
    }

    container.classList.add('is-visible');

    if (value.length < 8) {
        container.dataset.score = '1';
        label.textContent = 'Mínimo 8 caracteres';
        return;
    }

    let score = 2;
    if (value.length >= 12) score += 1;
    if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 1;
    if (/\d/.test(value) && /[^A-Za-z0-9]/.test(value)) score += 1;
    score = Math.min(score, 4);

    const labels = { 2: 'Aceptable', 3: 'Buena', 4: 'Fuerte' };
    container.dataset.score = String(score);
    label.textContent = labels[score];
}

function initAuthValidation() {
    const form = document.querySelector('.auth-form');
    if (!form) return;

    form.querySelectorAll('input, select').forEach((input) => {
        const clear = () => clearFieldError(input);
        input.addEventListener('input', clear);
        input.addEventListener('change', clear);
    });

    const email = form.elements.namedItem('email');
    if (email) {
        email.addEventListener('blur', () => {
            if (email.value) applyValidation(email, validateEmail(email));
        });
    }

    const password = form.elements.namedItem('password');
    const confirm = form.elements.namedItem('confirmPassword');
    const strength = document.getElementById('passwordStrength');

    if (password && strength) {
        renderPasswordStrength('');
        password.addEventListener('input', () => {
            renderPasswordStrength(password.value);
            if (confirm && confirm.value) {
                applyValidation(confirm, validateConfirmPassword(confirm, password));
            }
        });
        password.addEventListener('blur', () => {
            if (password.value) applyValidation(password, validateNewPassword(password));
        });
    }

    if (confirm && password) {
        confirm.addEventListener('input', () => {
            if (confirm.value) {
                applyValidation(confirm, validateConfirmPassword(confirm, password));
            }
        });
        confirm.addEventListener('blur', () => {
            if (confirm.value) {
                applyValidation(confirm, validateConfirmPassword(confirm, password));
            }
        });
    }
}

function initAuthPage() {
    initAuthValidation();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initAuthPage);
} else {
    initAuthPage();
}

// ===== Login form handler =====
async function handleLogin(event) {
    event.preventDefault();
    const form = event.target;
    const email = form.elements.namedItem('email');
    const password = form.elements.namedItem('password');
    const errorBox = document.getElementById('errorBox');
    const button = document.getElementById('loginBtn');

    errorBox.classList.add('d-none');

    const emailValid = applyValidation(email, validateEmail(email));
    const passwordValid = applyValidation(password, validateLoginPassword(password));

    if (!emailValid || !passwordValid) {
        (!emailValid ? email : password).focus();
        return;
    }

    setAuthButtonLoading(button, true, 'Ingresando...');

    try {
        const data = await api.post('/auth/login', {
            email: email.value.trim(),
            password: password.value
        });
        saveSession(data.token, data.email || email.value.trim(), data.moneda, data.nombre, data.timezone);
        window.location.href = '/index.html';
    } catch (err) {
        showServerFieldErrors(err, form, errorBox, 'Email o contraseña incorrectos');
        setAuthButtonLoading(button, false);
    }
}

// ===== Register form handler =====
async function handleRegister(event) {
    event.preventDefault();
    const form = event.target;
    const nombre = form.elements.namedItem('nombre');
    const email = form.elements.namedItem('email');
    const password = form.elements.namedItem('password');
    const confirmPassword = form.elements.namedItem('confirmPassword');
    const moneda = form.elements.namedItem('moneda');
    const timezone = form.elements.namedItem('timezone');
    const errorBox = document.getElementById('errorBox');
    const button = document.getElementById('registerBtn');

    errorBox.classList.add('d-none');

    const checks = [
        [nombre, validateRequired(nombre, 'Ingresa tu nombre')],
        [email, validateEmail(email)],
        [password, validateNewPassword(password)],
        [confirmPassword, validateConfirmPassword(confirmPassword, password)],
        [moneda, validateRequired(moneda, 'Selecciona una moneda')],
        [timezone, validateRequired(timezone, 'Selecciona tu zona horaria')]
    ];

    let firstInvalid = null;
    checks.forEach(([input, message]) => {
        if (!applyValidation(input, message) && !firstInvalid) {
            firstInvalid = input;
        }
    });

    if (firstInvalid) {
        firstInvalid.focus();
        return;
    }

    const body = {
        email: email.value.trim(),
        password: password.value,
        nombre: nombre.value.trim()
    };

    if (moneda) body.moneda = moneda.value;
    if (timezone) body.timezone = timezone.value;

    setAuthButtonLoading(button, true, 'Creando cuenta...');

    try {
        const data = await api.post('/auth/register', body);
        saveSession(data.token, data.email || body.email, data.moneda, data.nombre, data.timezone);
        window.location.href = '/index.html';
    } catch (err) {
        showServerFieldErrors(err, form, errorBox, 'Error al registrarse');
        setAuthButtonLoading(button, false);
    }
}

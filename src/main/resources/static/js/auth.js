// Authentication helpers

const TOKEN_KEY = 'geogasto_token';
const EMAIL_KEY = 'geogasto_email';

function saveSession(token, email) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(EMAIL_KEY, email);
}

function getEmail() {
    return localStorage.getItem(EMAIL_KEY);
}

function isLoggedIn() {
    return !!localStorage.getItem(TOKEN_KEY);
}

function logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
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
    const email = form.email.value;
    const password = form.password.value;
    const errorBox = document.getElementById('errorBox');

    errorBox.classList.add('d-none');

    try {
        const data = await api.post('/auth/login', { email, password });
        saveSession(data.token, email);
        window.location.href = '/index.html';
    } catch (err) {
        errorBox.textContent = 'Email o contraseña incorrectos';
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
        email: form.email.value,
        password: form.password.value,
        nombre: form.nombre.value
    };

    try {
        await api.post('/auth/register', body);
        // Registro exitoso: redirige a login
        window.location.href = '/login.html?registered=true';
    } catch (err) {
        errorBox.textContent = err.message || 'Error al registrarse';
        errorBox.classList.remove('d-none');
    }
}
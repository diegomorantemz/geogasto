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

    const response = await fetch(API_BASE + url, { ...options, headers });

    // If unauthorized, clear token and redirect to login
    if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('geogasto_token');
        localStorage.removeItem('geogasto_email');
        if (!window.location.pathname.endsWith('login.html')) {
            window.location.href = '/login.html';
        }
        throw new Error('Unauthorized');
    }

    // No content
    if (response.status === 204) {
        return null;
    }

    const text = await response.text();
    const data = text ? JSON.parse(text) : null;

    if (!response.ok) {
        const message = data && data.message ? data.message : 'Request failed';
        throw new Error(message);
    }

    return data;
}

const api = {
    get: (url) => request(url),
    post: (url, body) => request(url, { method: 'POST', body: JSON.stringify(body) }),
    put: (url, body) => request(url, { method: 'PUT', body: JSON.stringify(body) }),
    delete: (url) => request(url, { method: 'DELETE' })
};
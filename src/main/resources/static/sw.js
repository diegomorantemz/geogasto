const CACHE_NAME = 'geogasto-v1';
const APP_SHELL = [
    '/',
    '/index.html',
    '/login.html',
    '/register.html',
    '/expenses.html',
    '/new-expense.html',
    '/map.html',
    '/budgets.html',
    '/incomes.html',
    '/recurring.html',
    '/categories.html',
    '/places.html',
    '/profile.html',
    '/import.html',
    '/offline.html',
    '/css/app.css',
    '/js/api.js',
    '/js/auth.js',
    '/js/layout.js',
    '/js/dashboard.js',
    '/js/expenses.js',
    '/js/new-expense.js',
    '/js/map.js',
    '/js/budgets.js',
    '/js/incomes.js',
    '/js/recurring.js',
    '/js/categories.js',
    '/js/places.js',
    '/js/profile.js',
    '/js/import.js',
    '/favicon.svg',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/manifest.webmanifest'
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(APP_SHELL))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(
                keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
            ))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET') return;

    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;
    if (url.pathname.startsWith('/api/')) return;

    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request)
                .then(response => {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
                    return response;
                })
                .catch(() => caches.match(request)
                    .then(cached => cached || caches.match('/offline.html')))
        );
        return;
    }

    event.respondWith(
        fetch(request)
            .then(response => {
                if (response.ok) {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
                }
                return response;
            })
            .catch(() => caches.match(request))
    );
});

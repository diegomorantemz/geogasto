// Tema global claro/oscuro. Se carga en <head> para evitar el parpadeo inicial.
(function () {
    const KEY = 'geogasto_theme';
    const COLORS = { light: '#f3f1ec', dark: '#0f0e0d' };
    let lastApplied = null;

    function systemTheme() {
        return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
            ? 'dark'
            : 'light';
    }

    function savedTheme() {
        try {
            const value = localStorage.getItem(KEY);
            return value === 'light' || value === 'dark' ? value : null;
        } catch (ignored) {
            return null;
        }
    }

    function current() {
        return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }

    function syncControls(theme) {
        const nextLabel = theme === 'dark' ? 'claro' : 'oscuro';
        document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
            button.setAttribute('aria-label', `Cambiar a modo ${nextLabel}`);
            button.setAttribute('title', `Cambiar a modo ${nextLabel}`);
            button.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
        });
    }

    function apply(theme, persist) {
        document.documentElement.setAttribute('data-theme', theme);
        document.documentElement.setAttribute('data-bs-theme', theme);

        if (persist) {
            try {
                localStorage.setItem(KEY, theme);
            } catch (ignored) {
                // El tema funciona en esta página aunque no se pueda guardar.
            }
        }

        let meta = document.querySelector('meta[name="theme-color"]');
        if (!meta) {
            meta = document.createElement('meta');
            meta.name = 'theme-color';
            document.head.appendChild(meta);
        }
        meta.content = COLORS[theme];

        syncControls(theme);

        const changed = lastApplied !== theme;
        lastApplied = theme;

        if (changed) {
            document.dispatchEvent(new CustomEvent('geogasto:themechange', {
                detail: { theme }
            }));
        }
    }

    function toggle() {
        apply(current() === 'dark' ? 'light' : 'dark', true);
    }

    function refresh() {
        syncControls(current());
    }

    const initial = savedTheme() || systemTheme();
    apply(initial, false);

    document.addEventListener('click', (event) => {
        const button = event.target.closest('[data-theme-toggle]');
        if (!button) return;
        event.preventDefault();
        toggle();
    });

    document.addEventListener('DOMContentLoaded', () => {
        apply(current(), false);
        if (window.GGTheme) window.GGTheme.refresh();
    });

    const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    if (media && typeof media.addEventListener === 'function') {
        media.addEventListener('change', (event) => {
            if (!savedTheme()) {
                apply(event.matches ? 'dark' : 'light', false);
            }
        });
    }

    window.GGTheme = {
        apply,
        current,
        toggle,
        refresh
    };
})();

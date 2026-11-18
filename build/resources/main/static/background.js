/*
 * Background picker. Everything stays in this browser (localStorage); nothing is sent to the server.
 *
 *   appBackground        the chosen background: "none", a built-in id ("ocean") or "user:<id>"
 *   appBackgroundImages  the user's own images: [{id, name, data}] with data = a downscaled JPEG data URL
 *
 * Loaded in <head>, so the background is set before the page is drawn (no flash between pages).
 * The picker itself is the "background" fragment in layout.html.
 */
(function () {
    'use strict';

    const CHOICE_KEY = 'appBackground';
    const IMAGES_KEY = 'appBackgroundImages';
    const MAX_SIDE = 1920;     // user images are scaled down to at most this many pixels
    const QUALITY = 0.82;      // JPEG quality; keeps a photo around 200–400 KB
    // Everything after background.js (?v=…, ;jsessionid=…) is dropped to get the folder.
    const base = document.currentScript.src.replace(/background\.js.*$/, 'backgrounds/');

    const BUILT_IN = [
        {id: 'dawn', name: 'Dawn'},
        {id: 'ocean', name: 'Ocean'},
        {id: 'forest', name: 'Forest'},
        {id: 'blossom', name: 'Blossom'},
        {id: 'mountains', name: 'Sunset'},
        {id: 'night', name: 'Night sky'},
        {id: 'dots', name: 'Dots'},
    ].map(b => ({...b, url: base + b.id + '.svg'}));

    function read(key, fallback) {
        try {
            const value = localStorage.getItem(key);
            return value == null ? fallback : JSON.parse(value);
        } catch (e) {
            return fallback;   // storage blocked or corrupt
        }
    }

    /** Throws when the browser's storage is full; callers report that. */
    function write(key, value) {
        localStorage.setItem(key, JSON.stringify(value));
    }

    function userImages() {
        const list = read(IMAGES_KEY, []);
        return Array.isArray(list) ? list : [];
    }

    function backgrounds() {
        return [...BUILT_IN, ...userImages().map(u => ({id: 'user:' + u.id, name: u.name, url: u.data, user: true}))];
    }

    function current() {
        return read(CHOICE_KEY, 'none');
    }

    function apply(id) {
        const root = document.documentElement;
        const bg = backgrounds().find(b => b.id === id);
        if (bg) {
            root.style.setProperty('--app-bg', `url("${bg.url}")`);
            root.dataset.bg = bg.id;
        } else {
            root.style.removeProperty('--app-bg');
            delete root.dataset.bg;
        }
    }

    function choose(id) {
        try { write(CHOICE_KEY, id); } catch (e) { /* storage blocked: still apply for this page */ }
        apply(id);
        render();
    }

    // ---- Apply right away (this runs in <head>) ----
    apply(current());

    // ---- Picker UI ----
    function showError(message) {
        const box = document.getElementById('bgError');
        if (!box) return;
        box.textContent = message || '';
        box.classList.toggle('d-none', !message);
    }

    function tile(label, selected) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'bg-tile' + (selected ? ' active' : '');
        button.title = label;
        button.setAttribute('aria-pressed', selected);
        const caption = document.createElement('span');
        caption.className = 'bg-tile-name';
        caption.textContent = label;
        button.append(caption);
        return button;
    }

    function render() {
        const grid = document.getElementById('bgGrid');
        if (!grid) return;
        const chosen = current();
        grid.replaceChildren();

        const none = tile('None', !backgrounds().some(b => b.id === chosen));
        none.classList.add('bg-tile-none');
        none.prepend(Object.assign(document.createElement('i'), {className: 'bi bi-slash-circle'}));
        none.addEventListener('click', () => choose('none'));
        grid.append(none);

        for (const bg of backgrounds()) {
            const button = tile(bg.name, bg.id === chosen);
            button.style.backgroundImage = `url("${bg.url}")`;
            button.addEventListener('click', () => choose(bg.id));
            if (bg.user) {
                const remove = document.createElement('span');
                remove.className = 'bg-tile-remove';
                remove.setAttribute('role', 'button');
                remove.title = 'Remove this image';
                remove.innerHTML = '<i class="bi bi-x-lg"></i>';
                remove.addEventListener('click', event => {
                    event.stopPropagation();
                    removeImage(bg.id.slice('user:'.length));
                });
                button.append(remove);
            }
            grid.append(button);
        }

        const add = tile('Add image', false);
        add.classList.add('bg-tile-add');
        add.prepend(Object.assign(document.createElement('i'), {className: 'bi bi-plus-lg'}));
        add.addEventListener('click', () => document.getElementById('bgFile')?.click());
        grid.append(add);
    }

    /** Scales an image file down and returns it as a JPEG data URL. */
    function downscale(file) {
        return new Promise((resolve, reject) => {
            const url = URL.createObjectURL(file);
            const image = new Image();
            image.onload = () => {
                const scale = Math.min(1, MAX_SIDE / Math.max(image.naturalWidth, image.naturalHeight));
                const canvas = document.createElement('canvas');
                canvas.width = Math.round(image.naturalWidth * scale);
                canvas.height = Math.round(image.naturalHeight * scale);
                canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
                URL.revokeObjectURL(url);
                resolve(canvas.toDataURL('image/jpeg', QUALITY));
            };
            image.onerror = () => {
                URL.revokeObjectURL(url);
                reject(new Error(file.name + ' is not an image this browser can open'));
            };
            image.src = url;
        });
    }

    async function addImages(files) {
        showError('');
        let lastId = null;
        for (const file of files) {
            if (!file.type.startsWith('image/')) {
                showError(file.name + ' is not an image.');
                continue;
            }
            try {
                const data = await downscale(file);
                const entry = {id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
                               name: file.name.replace(/\.[^.]+$/, ''), data};
                write(IMAGES_KEY, [...userImages(), entry]);
                lastId = 'user:' + entry.id;
            } catch (error) {
                showError(error.name === 'QuotaExceededError'
                    ? 'The browser has no room for more images. Remove one of your images and try again.'
                    : error.message);
            }
        }
        if (lastId) choose(lastId); else render();
    }

    function removeImage(id) {
        try { write(IMAGES_KEY, userImages().filter(u => u.id !== id)); } catch (e) { /* ignore */ }
        if (current() === 'user:' + id) choose('none'); else render();
    }

    document.addEventListener('DOMContentLoaded', () => {
        render();
        const input = document.getElementById('bgFile');
        input?.addEventListener('change', () => {
            addImages(Array.from(input.files));
            input.value = '';   // allow choosing the same file again
        });
    });
})();

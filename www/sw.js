const V = 'mishkat-v4';
const SHELL = ['./', 'index.html', 'manifest.json', 'icon-192.png', 'icon-512.png'];
const CDN = /(cdnjs\.cloudflare\.com|cdn\.jsdelivr\.net|unpkg\.com|fonts\.googleapis\.com|fonts\.gstatic\.com|upload\.wikimedia\.org|tile\.openstreetmap\.org)/;
self.addEventListener('install', e => { e.waitUntil(caches.open(V).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(k => Promise.all(k.filter(x => x !== V).map(x => caches.delete(x)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const r = e.request; if (r.method !== 'GET' || r.destination === 'audio' || r.headers.has('range')) return;
  const u = new URL(r.url);
  if (u.pathname.includes('/mushaf/')) return;
  if (u.hostname === 'api.alquran.cloud' || u.hostname === 'en.wikipedia.org') {
    e.respondWith(fetch(r).then(x => { const c = x.clone(); caches.open(V).then(h => h.put(r, c)); return x; }).catch(() => caches.match(r)));
    return;
  }
  if (u.origin === location.origin) { e.respondWith(fetch(r).then(x => { const c = x.clone(); caches.open(V).then(k => k.put(r, c)); return x; }).catch(() => caches.match(r))); return; }
  if (CDN.test(u.hostname)) {
    e.respondWith(caches.match(r).then(h => {
      const n = fetch(r).then(x => { if (x.ok || x.type === 'opaque') { const c = x.clone(); caches.open(V).then(k => k.put(r, c)); } return x; }).catch(() => h);
      return h || n;
    }));
  }
});
self.addEventListener('notificationclick', e => { e.notification.close(); e.waitUntil(clients.matchAll({ type: 'window' }).then(l => l.length ? l[0].focus() : clients.openWindow('./'))); });

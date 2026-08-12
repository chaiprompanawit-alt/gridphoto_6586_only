/* Service Worker — ประกอบรูป ปงสนุก
   - แคชไฟล์แอป (app shell) ให้เปิดออฟไลน์ได้
   - แคชฟอนต์ไทยจาก Google Fonts แบบ cache-first (หลังโหลดครั้งแรกออนไลน์)
*/
const VERSION = 'v1';
const APP_CACHE  = 'app-' + VERSION;      // ไฟล์หลักของแอป
const FONT_CACHE = 'font-' + VERSION;     // ฟอนต์ (ข้ามโดเมน)

// ไฟล์ที่ต้องมีให้ครบเพื่อเปิดออฟไลน์
const APP_ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './stamp.png',
  './icon-192.png',
  './icon-512.png'
];

// ติดตั้ง: โหลดไฟล์แอปเก็บไว้
self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(APP_CACHE).then((c) => c.addAll(APP_ASSETS)).then(() => self.skipWaiting())
  );
});

// เปิดใช้งาน: ลบแคชเวอร์ชันเก่า
self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(
      keys.filter((k) => k !== APP_CACHE && k !== FONT_CACHE).map((k) => caches.delete(k))
    )).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // ฟอนต์จาก Google (googleapis = CSS, gstatic = ไฟล์ฟอนต์) → cache-first
  if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    e.respondWith(
      caches.open(FONT_CACHE).then(async (cache) => {
        const hit = await cache.match(req);
        if (hit) return hit;
        try {
          const res = await fetch(req);
          cache.put(req, res.clone());
          return res;
        } catch (err) {
          return hit || Response.error();
        }
      })
    );
    return;
  }

  // ไฟล์ในโดเมนเดียวกัน → cache-first แล้วอัปเดตแคชเบื้องหลัง
  if (url.origin === self.location.origin) {
    e.respondWith(
      caches.open(APP_CACHE).then(async (cache) => {
        const hit = await cache.match(req, { ignoreSearch: true });
        const network = fetch(req).then((res) => {
          if (res && res.ok) cache.put(req, res.clone());
          return res;
        }).catch(() => null);
        return hit || network || fetch(req);
      })
    );
  }
});

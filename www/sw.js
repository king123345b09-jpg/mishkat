// ملف الخدمة الخلفية Service Worker للإشعارات والتنبيهات المستمرة (مشابه لتطبيق صلاتك)
const CACHE_NAME = 'mishkat-cache-v1';

// تثبيت ملف الخدمة
self.addEventListener('install', (event) => {
  self.skipWaiting();
});

// تفعيل ملف الخدمة
self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

// استقبال طلبات عرض الإشعارات
self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SHOW_NOTIFICATION') {
    const { title, body } = event.data;
    
    self.registration.showNotification(title, {
      body: body,
      icon: './icon.png', // تأكد من وجود أيقونة بهذا الاسم أو غيرها
      badge: './badge.png',
      tag: 'mishkat-persistent-alarm',
      ongoing: true, // يجعله إشعاراً دائماً لا يختفي بسهولة (مناسب لتطبيق صلاتك)
      requireInteraction: true,
      actions: [
        { action: 'open_app', title: 'فتح التطبيق' }
      ]
    });
  }
});

// التعامل مع النقر على الإشعار
self.notificationclick = function(event) {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(clientList => {
      if (clientList.length > 0) {
        return clientList[0].focus();
      }
      return clients.openWindow('./index.html');
    })
  );
};

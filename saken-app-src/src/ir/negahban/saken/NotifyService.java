package ir.negahban.saken;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** ⚡️ پایش بلادرنگ ساکن: هر ۳۰ ثانیه صندوق شخصی چک می‌شود؛ با اپ بسته هم اعلان سیستمی با صدا می‌آید */
public class NotifyService extends Service {

    private Thread t;
    private volatile boolean run = true;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel watch = new NotificationChannel("watch", "پایش پیام‌ها", NotificationManager.IMPORTANCE_MIN);
            watch.setSound(null, null);
            nm.createNotificationChannel(watch);
            NotificationChannel alerts = new NotificationChannel("alerts", "پیام‌های فوری", NotificationManager.IMPORTANCE_HIGH);
            alerts.enableVibration(true);
            alerts.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build());
            nm.createNotificationChannel(alerts);
        }
        Intent open = new Intent(this, NotificationsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "watch") : new Notification.Builder(this);
        b.setContentTitle("🏠 ساکن‌یار")
                .setContentText("پایش پیام‌ها فعال است — خبر فوری دستت می‌رسد")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setOngoing(true)
                .setContentIntent(pi);
        try { startForeground(1001, b.build()); } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startLoop();
        return START_STICKY;
    }

    private void startLoop() {
        if (t != null && t.isAlive()) return;
        t = new Thread(new Runnable() {
            @Override public void run() {
                while (run) {
                    try { Thread.sleep(20000); } catch (Exception e) { return; }
                    try {
                        String code = Hub.code(NotifyService.this);
                        String inbox = Me.inbox(NotifyService.this);
                        if (code.isEmpty() || inbox.isEmpty()) { stopSelf(); return; }
                        long after = Cfg.p(NotifyService.this).getLong("bellAfter", 0);
                        java.util.ArrayList<Hub.MsgItem> items = Hub.msgs(code, inbox, after);
                        if (items.isEmpty()) continue;
                        int added = 0;
                        StringBuilder sb = new StringBuilder();
                        PatrolStore st = new PatrolStore(NotifyService.this);
                        try {
                            for (Hub.MsgItem it : items) {
                                String txt = Hub.notifText(it.kind, it.payload);
                                if (txt != null && st.addNotif(it.id, it.ts, it.kind, txt)) {
                                    added++;
                                    if (sb.length() > 0) sb.append("\n");
                                    sb.append(txt);
                                }
                            }
                        } finally { st.close(); }
                        if (Hub.sLastId > after)
                            Cfg.p(NotifyService.this).edit().putLong("bellAfter", Hub.sLastId).apply();
                        if (added > 0 && !MainActivity.uiVisible) alert(sb.toString().trim(), (int) Hub.sLastId);
                    } catch (Exception ignored) { }
                }
            }
        }, "saken-notify");
        t.start();
    }

    private void alert(String text, int id) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Intent open = new Intent(this, NotificationsActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, "alerts") : new Notification.Builder(this);
            b.setContentTitle("🔔 پیام جدید")
                    .setContentText(text.length() > 80 ? text.substring(0, 80) + "…" : text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            if (android.os.Build.VERSION.SDK_INT < 26) {
                b.setDefaults(Notification.DEFAULT_ALL);
                b.setPriority(Notification.PRIORITY_HIGH);
            }
            nm.notify(2000 + (id % 500), b.build());
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        run = false;
        if (t != null) t.interrupt();
        super.onDestroy();
    }
}

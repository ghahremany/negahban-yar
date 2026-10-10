package ir.negahban.modir;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

/**
 * سرویس اتصال به هاب: پیام‌های تازهٔ ساختمان را می‌گیرد و داخل پایگاه‌داده ثبت می‌کند
 * (گزارش نگهبان، ورود/خروج مهمان، بستهٔ تازه، درخواست عضویت، پرداخت شارژ، پیام آزاد)
 */
public class HubService extends Service {

    private static final String CH = "hub";
    private volatile Thread worker;
    public static volatile boolean alive = false;

    public static void start(Context c) {
        if (!Cfg.p(c).getString("bldgCode", "").trim().isEmpty()) {
            try { c.startService(new Intent(c, HubService.class)); } catch (Exception ignored) { }
        }
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, HubService.class)); } catch (Exception ignored) { }
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        startForegroundNow();
        if (worker == null || !worker.isAlive()) {
            alive = true;
            worker = new Thread(new Runnable() {
                @Override public void run() { loop(); }
            });
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    private void startForegroundNow() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "مدیر یار", NotificationManager.IMPORTANCE_MIN);
                nm.createNotificationChannel(ch);
            }
            Notification n = null;
            if (Build.VERSION.SDK_INT >= 26)
                n = new Notification.Builder(this, CH)
                        .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                        .setContentTitle("مدیر یار")
                        .setContentText("در حال دریافت خبرهای ساختمان")
                        .setOngoing(true).build();
            else
                n = new Notification.Builder(this)
                        .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                        .setContentTitle("مدیر یار")
                        .setContentText("در حال دریافت خبرهای ساختمان")
                        .setOngoing(true).build();
            startForeground(21, n);
        } catch (Exception ignored) { }
    }

    private void loop() {
        while (alive) {
            try { tick(); } catch (Exception ignored) { }
            try { Thread.sleep(10000); } catch (Exception e) { return; }
        }
    }

    /** یک دور دریافت پیام‌ها */
    void tick() {
        final String code = Cfg.p(this).getString("bldgCode", "").trim();
        if (code.isEmpty()) return;
        long after = Cfg.p(this).getLong("hubAfter", 0);
        java.util.ArrayList<Cloud.MsgItem> items = Cloud.hubMsgs(code, "modir", after);
        if (items.isEmpty()) return;
        final StringBuilder sysMsg = new StringBuilder();
        int sysN = 0;
        PatrolStore st = new PatrolStore(this);
        try {
            for (Cloud.MsgItem it : items) {
                try {
                    String ntxt = notifText(it.kind, it.payload);
                    if (ntxt != null && st.addNotif(it.id, it.ts, it.kind, ntxt)) {
                        sysN++;
                        if (sysMsg.length() > 0) sysMsg.append('\n');
                        sysMsg.append(ntxt);
                    }
                    JSONObject j = new JSONObject(it.payload);
                    if ("GUEST_ADD".equals(it.kind)) {
                        st.addGuestTs(j.optLong("ts", PatrolStore.now()), j.optString("name", ""),
                                j.optString("mobile", ""), j.optString("plate", ""), j.optString("nid", ""), 0);
                    } else if ("GUEST_OUT".equals(it.kind)) {
                        st.markGuestOut(j.optString("nid", ""), j.optString("name", ""));
                    } else if ("PKG_ADD".equals(it.kind)) {
                        st.addPackageTs(PatrolStore.now(), j.optString("kind", ""), j.optString("barcode", ""),
                                j.optString("rname", ""), j.optString("mobile", ""), j.optString("unit", ""), "PENDING");
                    } else if ("GU_GUEST_REQ".equals(it.kind)) {
                        st.addGuestReq(it.ts, j.optString("guest", ""), j.optString("mobile", ""),
                                j.optString("plate", ""), j.optString("when", ""), j.optLong("id", 0));
                        st.addMsg(it.ts, it.src, it.kind, it.payload);
                    } else if ("GU_OK".equals(it.kind) || "GU_NO".equals(it.kind)) {
                        st.setGuestApprovedByReq(j.optLong("id", 0), "GU_OK".equals(it.kind));
                    } else if (it.payload != null && it.payload.startsWith("NGHQ|")) {
                        // خطوط ماشینی نگهبان: ورود/خروج مهمان، بسته، شارژ
                        String[] q = it.payload.split("\\|");
                        if (q.length >= 3 && "GUEST_IN".equals(q[1])) {
                            st.addGuestTs(q.length > 2 ? safeLong(q[2], it.ts) : it.ts,
                                    q.length > 3 ? q[3] : "", q.length > 4 ? q[4] : "",
                                    q.length > 5 ? q[5] : "", q.length > 6 ? q[6] : "", 0);
                        } else if (q.length >= 5 && "GUEST_OUT".equals(q[1])) {
                            st.markGuestOut(q.length > 4 ? q[4] : "", q.length > 3 ? q[3] : "");
                        } else if (q.length >= 8 && "PKG".equals(q[1])) {
                            st.addPackageTs(safeLong(q[2], it.ts), q[3], q[4], q[5], q[6], q[7], "PENDING");
                        } else {
                            st.addMsg(it.ts, it.src, it.kind, it.payload);
                        }
                    } else {
                        // REPORT / REG_REQ / GUEST_REQ / CHARGE_PAY / MSG → صندوق پیام
                        st.addMsg(it.ts, it.src, it.kind, it.payload);
                    }
                } catch (Exception e) {
                    st.addMsg(it.ts, it.src, it.kind, it.payload);
                }
            }
        } finally {
            st.close();
        }
        if (Cloud.sLastId > after) Cfg.p(this).edit().putLong("hubAfter", Cloud.sLastId).apply();
        autoReport();
        if (sysN > 0 && !ir.negahban.modir.MainActivity.uiVisible) alert(sysMsg.toString().trim(), sysN);
    }
    /** 🐞 ارسال خودکار خطاهای ثبت‌شده به پشتیبانی (حداکثر هر ۶ ساعت یک‌بار) */
    void autoReport() {
        try {
            long last = Cfg.p(this).getLong("errReportedAt", 0);
            long now = PatrolStore.now();
            if (now - last < 21600000L) return;
            String crash = CrashCatcher.lastCrash(this);
            String err = CrashCatcher.lastErr(this);
            boolean hasC = crash != null && !crash.trim().isEmpty();
            boolean hasE = err != null && !err.trim().isEmpty();
            if (!hasC && !hasE) return;
            String code = Cfg.p(this).getString("bldgCode", "").trim();
            if (code.isEmpty()) { Cfg.p(this).edit().putLong("errReportedAt", now).apply(); return; }
            String ver;
            try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { ver = "?"; }
            org.json.JSONObject j = new org.json.JSONObject();
            j.put("app", "مدیر یار");
            j.put("ver", ver);
            j.put("android", android.os.Build.VERSION.RELEASE);
            j.put("model", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            j.put("text", "ارسال خودکار خطا");
            if (hasC) j.put("crash", crash.length() > 2500 ? crash.substring(crash.length() - 2500) : crash);
            if (hasE) j.put("err", err.length() > 2000 ? err.substring(err.length() - 2000) : err);
            if (Cloud.hubMsg(code, "support", "app:modir", "SUPPORT", j.toString()).ok) {
                try { CrashCatcher.clearCrash(this); CrashCatcher.clearErrs(this); } catch (Exception ignored) { }
            }
            Cfg.p(this).edit().putLong("errReportedAt", now).apply();
        } catch (Exception ignored) { }
    }

    /** متن اعلان زنگوله بر اساس نوع پیام — null یعنی اعلان نمی‌خواهد */
    static String notifText(String kind, String payload) {
        // گزارش‌های متنی نگهبان (متن خام، نه JSON)
        if ("GUEST_IN".equals(kind)) return "🚶 ورود مهمان" + who(payload, "مهمان");
        if ("GUEST_OUT".equals(kind)) return "🚪 خروج مهمان" + who(payload, "مهمان");
        if ("PKG_IN".equals(kind)) return "📦 دریافت بسته" + who(payload, "گیرنده");
        if ("PKG_OUT".equals(kind)) return "✅ بسته تحویل شد" + who(payload, "گیرنده");
        if ("NGHQ".equals(kind)) return null;   // ردیف همگام‌سازی — اعلان نمی‌خواهد
        if ("REPORT".equals(kind) && payload != null && payload.startsWith("NGHQ|")) {
            // نگهبان قدیمی: گزارش با محتوای NGHQ — عنوان درست از دل خود رشته
            String[] pp = payload.split("\\|");
            String t = pp.length > 1 ? pp[1] : "";
            if ("GUEST_IN".equals(t)) return "🚶 ورود مهمان" + (pp.length > 3 && !pp[3].isEmpty() ? " — " + pp[3] : "");
            if ("GUEST_OUT".equals(t)) return "🚪 خروج مهمان" + (pp.length > 3 && !pp[3].isEmpty() ? " — " + pp[3] : "");
            if ("PKG".equals(t)) return "📦 دریافت بسته" + (pp.length > 5 && !pp[5].isEmpty() ? " — " + pp[5] : "");
            return null;
        }
        try {
            JSONObject j = new JSONObject(payload);
            if ("REG_REQ".equals(kind))
                return "📝 درخواست عضویت «" + j.optString("name", "") + "» — واحد " + j.optString("unit", "—");
            if ("ROOF_KEY_REQ".equals(kind))
                return "🔑 «" + j.optString("name", "") + "» (واحد " + j.optString("unit", "—") + ") کلید پشت‌بام می‌خواهد";
            if ("MOVE_REQ".equals(kind))
                return "🚚 «" + j.optString("name", "") + "» (واحد " + j.optString("unit", "—") + ") درخواست اسباب‌کشی داده";
            if ("ELEV_REQ".equals(kind))
                return "🛗 «" + j.optString("name", "") + "» (واحد " + j.optString("unit", "—") + ") استفاده از آسانسور می‌خواهد";
            if ("GU_GUEST_REQ".equals(kind))
                return "🚶 درخواست ورود مهمان «" + j.optString("guest", "") + "»" + (j.optString("when", "").isEmpty() ? "" : " — " + j.optString("when", ""));
            if ("GUEST_REQ".equals(kind))
                return "🚶 درخواست حضور مهمان «" + j.optString("guest", j.optString("name", "")) + "»";
            if ("CHARGE_PAY".equals(kind)) return "💳 پرداخت شارژ ثبت شد";
            if ("REPORT".equals(kind)) return "📋 گزارش نگهبان رسید";
            if ("NEWS".equals(kind)) return "📢 " + j.optString("title", "اطلاعیه") + ": " + j.optString("body", "");
            if (kind != null && kind.startsWith("GU_")) return null;   // نتیجه‌ها اعلان مدیر نیستند
            return "💬 پیام جدید در صندوق";
        } catch (Exception e) { return "💬 پیام جدید در صندوق"; }
    }

    /** مقدار «نام/گیرنده» از متن گزارش نگهبان: خطی که کلید دارد، بعد از : */
    private static String who(String payload, String key) {
        try {
            for (String ln : payload.split("\n")) {
                String l = ln.trim();
                int c = l.indexOf(':');
                if (c > 0 && l.substring(0, c).contains(key)) {
                    String v = l.substring(c + 1).trim();
                    if (!v.isEmpty()) return " — " + v;
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    /** اعلان سیستمی فوری با صدا و لرزش */
    private void alert(String text, int n) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("alerts", "پیام‌های فوری", NotificationManager.IMPORTANCE_HIGH);
                ch.enableVibration(true);
                ch.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                        new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build());
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(this, InboxActivity.class);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 3, open,
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "alerts") : new Notification.Builder(this);
            b.setContentTitle("🔔 " + (n == 1 ? "پیام جدید" : n + " پیام جدید"))
                    .setContentText(text.length() > 80 ? text.substring(0, 80) + "…" : text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            if (Build.VERSION.SDK_INT < 26) { b.setDefaults(Notification.DEFAULT_ALL); b.setPriority(Notification.PRIORITY_HIGH); }
            nm.notify(3000, b.build());
        } catch (Exception ignored) { }
    }

    static long safeLong(String s, long dft) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return dft; }
    }
}

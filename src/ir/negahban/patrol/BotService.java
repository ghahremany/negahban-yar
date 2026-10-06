package ir.negahban.patrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * ربات ساکنین — بدون سرور: همین گوشی نگهبانی نقش سرور ربات را بازی می‌کند.
 * با long-poll به getUpdates گوش میدهد و به پیام‌های ساکنین جواب میدهد:
 *   📦 بسته‌های من — بسته‌های تحویل‌نشدهٔ عضو
 *   📝 ثبت‌نام — اتصال با شمارهٔ همراه (عضو قبلی: خودکار / تازه: در انتظار تأیید مدیر)
 * مدیریت تأیید در اپ: منوی ☰ → ✅ تأیید اعضا
 */
public class BotService extends Service {

    private static final String CH = "bot";
    private volatile Thread worker;
    public static volatile boolean alive = false;

    public static void start(Context c) {
        if (!Cfg.botEnabled(c) || Cfg.token(c).isEmpty()) return;
        Intent i = new Intent(c, BotService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {}
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, BotService.class)); } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        alive = true;
        startForegroundNow();
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(new Runnable() {
                @Override public void run() { loop(); }
            }, "bot");
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
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannel ch = new NotificationChannel(CH, "ربات نگهبان‌یار", NotificationManager.IMPORTANCE_MIN);
                ch.setDescription("پاسخگویی به ساکنین در بله");
                nm.createNotificationChannel(ch);
            }
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CH)
                    : new Notification.Builder(this);
            nb.setContentTitle("نگهبان‌یار — ربات ساکنین فعال است")
              .setContentText("پاسخگویی به اهالی در پیام‌رسان بله")
              .setSmallIcon(android.R.drawable.ic_dialog_email)
              .setOngoing(true);
            startForeground(11, nb.build());
        } catch (Exception ignored) {}
    }

    // ---------- حلقهٔ اصلی ----------

    private void loop() {
        long offset = Cfg.p(this).getLong("botOffset", 0);
        int failStreak = 0;
        while (alive) {
            try {
                if (!Cfg.botEnabled(this)) { stopSelf(); return; }
                String tk = Cfg.token(this);
                if (tk.isEmpty()) { stopSelf(); return; }

                ArrayList<Bale.Update> ups = Bale.pollUpdates(tk, offset);
                if (ups == null) {
                    // خطای شبکه یا توکن — صبورانه دوباره
                    if (++failStreak > 60) { // حدود یک ساعت پیوسته شکست
                        PatrolStore st = new PatrolStore(this);
                        st.enq("⚠️ ربات ساکنین نتوانست به بله وصل شود (توکن یا اینترنت را بررسی کن)");
                        st.close();
                        failStreak = 0;
                    }
                    Thread.sleep(60000);
                    continue;
                }
                failStreak = 0;
                for (Bale.Update u : ups) {
                    if (u.updateId >= offset) offset = u.updateId + 1;
                    try { handle(tk, u); } catch (Exception ignored) {}
                }
                if (!ups.isEmpty()) Cfg.set(this, "botOffset", offset);
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                try { Thread.sleep(30000); } catch (InterruptedException e2) { return; }
            }
        }
    }

    // ---------- پردازش پیام ----------

    private void handle(String tk, Bale.Update u) {
        String t = u.text.trim();
        long chat = u.chatId;
        Context c = this;

        String[] res = db().resByChat(chat);
        String[] pend = db().pendingReqByChat(chat);

        // مراحل ثبت‌نام
        String step = Cfg.p(c).getString("reg_" + chat, "");

        if (t.startsWith("/start") || t.contains("شروع")) {
            if (res != null) {
                send(tk, chat, "سلام " + res[1] + " 👋\nبه سامانهٔ نگهبان‌یار خوش آمدی.\n\n📦 «بسته‌های من» را بنویس تا بسته‌های در انتظارت را ببینی.");
            } else if (pend != null) {
                send(tk, chat, "✅ درخواست عضویتت ثبت شده و در انتظار تأیید مدیر است. به‌محض تأیید همین‌جا خبر میدهیم.");
            } else {
                send(tk, chat, "سلام 👋 به سامانهٔ نگهبان‌یار خوش آمدی!\n\n برای استفاده از خدمات (اطلاع از بسته‌ها و…) عضو شو:\n\n📝 عبارت «ثبت‌نام» را بفرست.", mainMenu());
            }
            return;
        }

        if (t.contains("بسته")) {
            if (res == null) {
                send(tk, chat, pend != null
                        ? "درخواست عضویتت هنوز در انتظار تأیید مدیر است 🕐"
                        : "برای دیدن بسته‌ها اول باید عضو شوی — «ثبت‌نام» را بفرست 📝", mainMenu());
                return;
            }
            ArrayList<String[]> pkgs = db().pendingForMobile(res[2]);
            if (pkgs.isEmpty()) {
                send(tk, chat, "📭 در حال حاضر بسته‌ای در انتظار تو نیست.\n\nمثل همیشه در نگهبانی چک می‌کنیم 🙂");
            } else {
                StringBuilder sb = new StringBuilder("📦 بسته‌های در انتظار تو در نگهبانی:\n\n");
                for (String[] k : pkgs) {
                    sb.append("• ").append(k[2]);
                    if (k[3] != null && !k[3].isEmpty()) sb.append(" (بارکد ").append(k[3]).append(")");
                    if (k[4] != null && !k[4].isEmpty()) sb.append(" — بلوک ").append(k[4]);
                    sb.append("\n  زمان ثبت: ").append(Scheduler.fa(Scheduler.hm(Long.parseLong(k[1]))))
                      .append(" — ").append(Scheduler.jalaliDate(Long.parseLong(k[1]))).append("\n");
                }
                sb.append("\nبرای تحویل، به نگهبانی مراجعه کن 🙏");
                send(tk, chat, sb.toString());
            }
            return;
        }

        if (t.contains("ثبت") && t.contains("نام") || t.equals("ثبت‌نام") || t.equals("ثبت نام")) {
            if (res != null) {
                send(tk, chat, "شما از قبل عضو تأییدشده هستی ✅ (" + res[1] + ")");
                return;
            }
            Cfg.set(c, "reg_" + chat, "mobile");
            send(tk, chat, "خوشحالیم که می‌پیوندی! 🌟\n\n📱 شمارهٔ همراهت را بفرست (مثل 09123456789)\nاگر قبلاً مدیر شماره‌ات را ثبت کرده باشد، خودکار وصل می‌شوی.");
            return;
        }

        // جریان چندمرحله‌ای ثبت‌نام
        if ("mobile".equals(step)) {
            String mobile = digits(t);
            if (mobile.length() < 10) {
                send(tk, chat, "شماره معتبر نیست — مثل 09123456789 بفرست 🙏");
                return;
            }
            long linked = db().linkChatByMobile(chat, mobile);
            if (linked > 0) {
                clearReg(chat);
                String name = db().resByChat(chat) != null ? db().resByChat(chat)[1] : "";
                send(tk, chat, "🎉 وصل شدی! شماره‌ات در سیستم بود و عضویتت تأیید است.\n\nخوش آمدی " + name + "\nحالا «بسته‌های من» را بنویس تا بسته‌هایت را ببینی.");
                notifyManager(tk, "🔗 ساکن از طریق ربات وصل شد: " + name + " — " + Scheduler.fa(mobile));
                return;
            }
            Cfg.set(c, "reg_" + chat, "name");
            Cfg.set(c, "regtmp_" + chat, mobile);
            send(tk, chat, "شماره‌ات در فهرست فعلی نبود — اشکالی ندارد، درخواست عضویت می‌سازیم 📝\n\n👤 نام و نام خانوادگی‌ات را بفرست:");
            return;
        }
        if ("name".equals(step)) {
            if (t.length() < 3) { send(tk, chat, "نام را کامل بنویس 🙏"); return; }
            Cfg.set(c, "reg_" + chat, "unit");
            Cfg.set(c, "regtmp2_" + chat, t);
            send(tk, chat, "🏠 شمارهٔ واحد/بلوک یا پارکینگت را بفرست (مثل ب۳ یا ۲۰۴):");
            return;
        }
        if ("unit".equals(step)) {
            String mobile = Cfg.p(c).getString("regtmp_" + chat, "");
            String name = Cfg.p(c).getString("regtmp2_" + chat, "");
            db().addMemberReq(PatrolStore.now(), chat, name, mobile, t);
            clearReg(chat);
            send(tk, chat, "✅ درخواستت ثبت شد!\nمدیر ساختمان بررسی می‌کند و به‌محض تأیید، همین‌جا بهت خبر میدهیم. صبورانه منتظر باش 🕐");
            notifyManager(tk, "🆕 درخواست عضویت جدید از ربات:\n👤 " + name + "\n📱 " + Scheduler.fa(mobile) + "\n🏠 " + t + "\n\nتأیید در اپ: منوی ☰ → ✅ تأیید اعضا");
            return;
        }

        // پیش‌فرض: راهنما
        send(tk, chat, "راهنما 📖\n\n📦 بنویس «بسته» — بسته‌های در انتظارت\n📝 بنویس «ثبت‌نام» — عضویت در سامانه", mainMenu());
    }

    private void clearReg(long chat) {
        Cfg.p(this).edit().remove("reg_" + chat).remove("regtmp_" + chat).remove("regtmp2_" + chat).apply();
    }

    private PatrolStore db() { return new PatrolStore(this); }

    private String digits(String s) {
        String[] fa = {"۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"};
        String[] ar = {"٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"};
        for (int i = 0; i < 10; i++) { s = s.replace(fa[i], String.valueOf(i)).replace(ar[i], String.valueOf(i)); }
        return s.replaceAll("[^0-9]", "");
    }

    private String mainMenu() {
        try {
            JSONObject kb = new JSONObject();
            JSONArray rows = new JSONArray();
            JSONArray r1 = new JSONArray();
            r1.put("📦 بسته‌های من");
            JSONArray r2 = new JSONArray();
            r2.put("📝 ثبت‌نام");
            rows.put(r1);
            rows.put(r2);
            kb.put("keyboard", rows);
            kb.put("resize_keyboard", true);
            return kb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void send(String tk, long chat, String text) { send(tk, chat, text, null); }

    private void send(String tk, long chat, String text, String replyMarkup) {
        try {
            String body = "{\"chat_id\":" + chat + ",\"text\":\"" + jsonEscape(text) + "\"";
            if (replyMarkup != null && !replyMarkup.isEmpty()) body += ",\"reply_markup\":" + replyMarkup;
            body += "}";
            Bale.call(tk, "sendMessage", body);
        } catch (Exception ignored) {}
    }

    private void notifyManager(String tk, String text) {
        long mgr = Cfg.chatId(this);
        if (mgr == 0) return;
        try {
            Bale.Res r = Bale.send(tk, mgr, text);
            if (r == null || !r.ok) {
                PatrolStore st = new PatrolStore(this);
                st.enq(text);
                st.close();
            }
        } catch (Exception e) {
            PatrolStore st = new PatrolStore(this);
            st.enq(text);
            st.close();
        }
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) sb.append(' ');
                    else sb.append(ch);
            }
        }
        return sb.toString();
    }
}

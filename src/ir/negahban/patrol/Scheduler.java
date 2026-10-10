package ir.negahban.patrol;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;

/**
 * برنامهٔ هر شب: پنجره‌های تصادفی گشت + زمان تصادفی چالش بیداری + آلارم‌ها
 * همهٔ زمان‌ها به‌صورت میلی‌ثانیهٔ مطلق در یک JSON ذخیره می‌شوند.
 */
public class Scheduler {

    public static final String A_PLAN = "PLAN";
    public static final String A_SYNC = "SYNC";
    public static final String A_WEND = "WEND";
    public static final String A_CHAL = "CHAL";
    public static final String A_MORN = "MORN";

    private static AlarmManager am(Context c) { return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE); }

    public static boolean exactOk(Context c) {
        return Build.VERSION.SDK_INT < 31 || am(c).canScheduleExactAlarms();
    }

    private static void setExact(Context c, long at, PendingIntent pi) {
        if (exactOk(c)) am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static PendingIntent pi(Context c, String action, int req) {
        return pi(c, action, req, 0);
    }

    private static PendingIntent pi(Context c, String action, int req, int extra) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(action);
        if (extra != 0) i.putExtra("idx", extra);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, req, i, flags);
    }

    public static JSONObject getPlan(Context c) {
        try {
            return new JSONObject(Cfg.p(c).getString("plan", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static void savePlan(Context c, JSONObject plan) {
        Cfg.set(c, "plan", plan.toString());
    }

    /** اگر برنامهٔ معتبری برای شب جاری وجود ندارد، بساز */
    public static void ensurePlan(Context c) {
        JSONObject plan = getPlan(c);
        long now = PatrolStore.now();
        long morning = plan.optLong("morning", 0);
        if (morning > now) { arm(c, plan); return; }

        // مبنای برنامه: نیمه‌شب بعدی
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) >= Cfg.reportMin(c) / 60 && cal.get(Calendar.HOUR_OF_DAY) >= 7) {
            // بعد از ۰۷:۰۰ صبح → برنامه برای امشب
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long base = cal.getTimeInMillis(); // نیمه‌شبِ شبِ گشت

        int n = Math.max(1, Cfg.patrolCount(c));
        int len = Cfg.windowMin(c);
        int startM = Cfg.nightStart(c);
        int endM = Cfg.nightEnd(c);
        Random rnd = new Random();

        // انتخاب شروع‌های تصادفیِ بدون هم‌پوشانی (واحد ۵ دقیقه)
        ArrayList<Integer> starts = new ArrayList<>();
        int span = endM - len - startM;
        boolean ok = false;
        for (int t = 0; t < 400 && !ok; t++) {
            starts.clear();
            ok = true;
            for (int i = 0; i < n; i++) {
                int off = span <= 0 ? 0 : (rnd.nextInt(span / 5 + 1)) * 5;
                starts.add(startM + off);
            }
            Collections.sort(starts);
            for (int i = 1; i < starts.size(); i++) {
                if (starts.get(i) - starts.get(i - 1) < len + 15) { ok = false; break; }
            }
        }
        if (!ok) { // جایگزین: فاصلهٔ مساوی + لرزش کوچک
            starts.clear();
            int step = span <= 0 ? 0 : span / Math.max(1, n);
            for (int i = 0; i < n; i++) {
                int j = step == 0 ? 0 : rnd.nextInt(Math.min(11, step + 1)) - 5;
                starts.add(startM + (step <= 0 ? 0 : i * step) + Math.max(0, Math.min(span, Math.max(0, j))));
            }
            Collections.sort(starts);
        }

        try {
            JSONObject p2 = new JSONObject();
            JSONArray js = new JSONArray();
            for (int i = 0; i < starts.size(); i++) {
                JSONObject w = new JSONObject();
                w.put("s", base + starts.get(i) * 60000L);
                w.put("e", base + (starts.get(i) + len) * 60000L);
                // ترتیب تصادفی ایستگاه‌ها برای همین گشت
                JSONArray order = new JSONArray();
                ArrayList<Integer> ids = new ArrayList<>();
                for (String[] st : Cfg.stations(c)) {
                    try { ids.add(Integer.parseInt(st[0])); } catch (Exception ignored) {}
                }
                Collections.shuffle(ids, rnd);
                for (Integer id : ids) order.put(id);
                w.put("order", order);
                w.put("done", false);
                js.put(w);
            }
            p2.put("patrols", js);

            // چالش بیداری: یک‌بار، داخل پنجرهٔ اول
            if (Cfg.wakeOn(c) && js.length() > 0) {
                JSONObject w0 = js.getJSONObject(0);
                long s0 = w0.getLong("s"), e0 = w0.getLong("e");
                long lenMs = e0 - s0;
                if (lenMs > 12 * 60000L) {
                    long chal = s0 + (5 + rnd.nextInt((int) (lenMs / 60000L) - 10)) * 60000L;
                    p2.put("challenge", chal);
                    p2.put("code", 1000 + rnd.nextInt(9000));
                    p2.put("challengeDone", false);
                }
            }

            // گزارش صبح
            Calendar m = Calendar.getInstance();
            m.setTimeInMillis(base);
            m.set(Calendar.HOUR_OF_DAY, Cfg.reportMin(c) / 60);
            m.set(Calendar.MINUTE, Cfg.reportMin(c) % 60);
            p2.put("morning", m.getTimeInMillis());
            p2.put("reported", false);
            p2.put("base", base);
            p2.put("date", jalaliDate(base)); // تاریخ شمسیِ شب گشت

            savePlan(c, p2);

            PatrolStore st = new PatrolStore(c);
            st.addEv("PLAN", null, null, p2.toString().length() + "b");
            st.close();
        } catch (Exception ignored) {}

        arm(c, getPlan(c));
    }

    /** تنظیم همهٔ آلارم‌ها بر اساس برنامه */
    public static void arm(Context c, JSONObject plan) {
        try {
            long now = PatrolStore.now();
            JSONArray js = plan.optJSONArray("patrols");
            if (js != null) {
                for (int i = 0; i < js.length(); i++) {
                    JSONObject w = js.getJSONObject(i);
                    long e = w.optLong("e", 0);
                    if (e > now) setExact(c, e, pi(c, A_WEND, 101 + i, i));
                }
            }
            if (plan.has("challenge") && !plan.optBoolean("challengeDone", true)) {
                long ch = plan.optLong("challenge", 0);
                if (ch > now - 60000) setExact(c, ch, pi(c, A_CHAL, 201));
            }
            long morning = plan.optLong("morning", 0);
            if (morning > now) setExact(c, morning, pi(c, A_MORN, 301));

            // برنامه‌ریز هر شب ۲۳:۳۰ برای شب بعد
            Calendar pl = Calendar.getInstance();
            pl.set(Calendar.HOUR_OF_DAY, 23);
            pl.set(Calendar.MINUTE, 30);
            pl.set(Calendar.SECOND, 0);
            if (pl.getTimeInMillis() <= now) pl.add(Calendar.DAY_OF_YEAR, 1);
            setExact(c, pl.getTimeInMillis(), pi(c, A_PLAN, 302));

            // سینک دوره‌ای هر ۶۰ ثانیه (پشتیبانِ ارسال فوری)
            PendingIntent syncPi = pi(c, A_SYNC, 303);
            am(c).cancel(syncPi);
            am(c).setRepeating(AlarmManager.RTC_WAKEUP, now + 60000, 60000, syncPi);
        } catch (Exception ignored) {}
    }

    public static void markChallengeDone(Context c, boolean ok) {
        JSONObject plan = getPlan(c);
        try {
            plan.put("challengeDone", true);
            plan.put("challengeOk", ok);
            savePlan(c, plan);
        } catch (Exception ignored) {}
    }

    /** اعداد لاتین → فارسی */
    public static String fa(String s) {
        String[] d = {"۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"};
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '0' && ch <= '9') sb.append(d[ch - '0']);
            else sb.append(ch);
        }
        return sb.toString();
    }

    public static String hm(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        return String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /** تاریخ شمسیِ یک لحظه، مثل «۱۲ مهر ۱۴۰۵» */
    public static String jalaliDate(long ts) {
        int[] j = Jalali.fromMillis(ts);
        return fa(String.valueOf(j[2])) + " " + Jalali.MONTHS[j[1] - 1] + " " + fa(String.valueOf(j[0]));
    }
}

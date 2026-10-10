package ir.negahban.modir;

import android.content.Context;
import android.content.SharedPreferences;

/** نگه‌داری تنظیمات در SharedPreferences + ابزارهای کمکی */
public class Cfg {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("cfg", 0); }

    public static String token(Context c) { return p(c).getString("token", "").trim(); }
    public static long chatId(Context c) { return p(c).getLong("chatId", 0); }
    public static String pin(Context c) { return p(c).getString("pin", "1234"); }
    public static String building(Context c) { return p(c).getString("building", "مجتمع مسکونی"); }

    public static String stationsRaw(Context c) {
        return p(c).getString("stations", "1|لابی\n2|پارکینگ\n3|حیاط\n4|پشت بام\n5|انبار");
    }
    public static int patrolCount(Context c) { return p(c).getInt("patrolCount", 2); }
    public static int windowMin(Context c) { return p(c).getInt("windowMin", 45); }
    public static int nightStart(Context c) { return p(c).getInt("nightStart", 0); }   // دقیقه از نیمه‌شب
    public static int nightEnd(Context c) { return p(c).getInt("nightEnd", 360); }
    public static int minGapSec(Context c) { return p(c).getInt("minGap", 60); }
    public static boolean wakeOn(Context c) { return p(c).getBoolean("wakeOn", true); }
    public static boolean botEnabled(Context c) { return p(c).getBoolean("botEnabled", false); }
    public static int reportMin(Context c) { return p(c).getInt("reportMin", 420); }   // ۰۷:۰۰

    public static String keyHex(Context c) {
        String k = p(c).getString("keyHex", "");
        if (k.isEmpty()) { k = Crypto.randHex(32); p(c).edit().putString("keyHex", k).apply(); }
        return k;
    }

    public static void set(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }
    public static void set(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
    public static void set(Context c, String k, long v) { p(c).edit().putLong(k, v).apply(); }
    public static void set(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }

    /** فهرست ایستگاه‌ها؛ خروجی: [n][2] با [0]=شناسه و [1]=نام */
    public static String[][] stations(Context c) {
        try {
            String[] lines = stationsRaw(c).split("\n");
            java.util.ArrayList<String[]> out = new java.util.ArrayList<>();
            for (String ln : lines) {
                ln = ln.trim();
                if (ln.isEmpty()) continue;
                String[] parts = ln.split("\\|");
                if (parts.length >= 2) out.add(new String[]{parts[0].trim(), parts[1].trim()});
                else if (parts.length == 1 && !parts[0].isEmpty()) out.add(new String[]{String.valueOf(out.size() + 1), parts[0]});
            }
            return out.toArray(new String[0][]);
        } catch (Exception e) {
            return new String[0][];
        }
    }

    public static String stationName(Context c, int id) {
        for (String[] s : stations(c)) {
            try { if (Integer.parseInt(s[0]) == id) return s[1]; } catch (Exception ignored) {}
        }
        return "ایستگاه " + id;
    }

    // ---------- نگهبان‌ها (JSON در SharedPreferences) ----------

    public static org.json.JSONArray guards(Context c) {
        try { return new org.json.JSONArray(p(c).getString("guards", "[]")); }
        catch (Exception e) { return new org.json.JSONArray(); }
    }

    public static void saveGuards(Context c, org.json.JSONArray a) {
        p(c).edit().putString("guards", a.toString()).apply();
    }

    /** نگهبانِ فعالِ امشب (بر اساس شمارهٔ همراه) یا null */
    public static org.json.JSONObject activeGuard(Context c) {
        String ph = p(c).getString("activeGuardPhone", "");
        if (ph.isEmpty()) return null;
        org.json.JSONArray a = guards(c);
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject g = a.optJSONObject(i);
            if (g != null && ph.equals(g.optString("phone"))) return g;
        }
        return null;
    }

    public static void setActiveGuard(Context c, String phone) {
        p(c).edit().putString("activeGuardPhone", phone == null ? "" : phone).apply();
    }

    /** «شیفت ۲۲:۰۰ تا ۰۶:۰۰ • شنبه تا چهارشنبه» */
    public static String guardShiftText(org.json.JSONObject g) {
        if (g == null) return "";
        StringBuilder sb = new StringBuilder("شیفت ");
        sb.append(g.optString("shStart", "—")).append(" تا ").append(g.optString("shEnd", "—"));
        String days = g.optString("days", "");
        if (!days.isEmpty()) sb.append(" • ").append(days);
        return sb.toString();
    }

    /** کلیدهای long که بازیابی بکاپِ قدیمی آن‌ها را int ذخیره کرده — اصلاح خودکار */
    public static void fixLongs(Context c) {
        String[] keys = {"licSeenMax", "licExpiry", "installTs", "hubAfter", "bellAfter",
                "lastCloudAuto", "lastUpdateCheck", "lastSync", "chatId", "roofAfter"};
        SharedPreferences sp = p(c);
        SharedPreferences.Editor ed = sp.edit();
        boolean ch = false;
        for (String k : keys) {
            try { sp.getLong(k, 0); }
            catch (ClassCastException e) {
                long v;
                try { v = sp.getInt(k, 0) & 0xFFFFFFFFL; } catch (Exception e2) { continue; }
                ed.putLong(k, v);
                ch = true;
            }
        }
        if (ch) ed.apply();
    }
}

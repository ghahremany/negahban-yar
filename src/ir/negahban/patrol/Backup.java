package ir.negahban.patrol;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;

/**
 * پشتیبان‌گیری و بازیابی: کل تنظیمات + نگهبان‌ها + ساکنین + رویدادهای ۹۰ روز اخیر
 * خروجی/ورودی: یک فایل JSON — قابل ارسال به بله (sendDocument) یا ذخیرهٔ لوکال (SAF)
 */
public class Backup {

    /** ساخت رشتهٔ JSON پشتیبان */
    public static String make(Context c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("app", "negahban-patrol");
        o.put("v", 1);
        o.put("ts", PatrolStore.now());

        // همهٔ تنظیمات (شامل توکن، کلید، PIN، نگهبان‌ها)
        Map<String, ?> all = Cfg.p(c).getAll();
        JSONObject cfg = new JSONObject(all);
        o.put("cfg", cfg);

        PatrolStore st = new PatrolStore(c);
        JSONArray res = new JSONArray();
        for (String[] r : st.searchRes(null)) {
            JSONObject rj = new JSONObject();
            rj.put("id", Long.parseLong(r[0]));
            rj.put("name", r[1]); rj.put("mobile", r[2]); rj.put("plate", r[3]);
            rj.put("car", r[4]); rj.put("parking", r[5]);
            res.put(rj);
        }
        o.put("residents", res);

        JSONArray evs = new JSONArray();
        for (String[] e : st.rawEventsSince(PatrolStore.now() - 90L * 86400000L)) {
            JSONObject ej = new JSONObject();
            ej.put("ts", Long.parseLong(e[0]));
            ej.put("type", e[1]);
            if (!e[2].isEmpty()) ej.put("station", Integer.parseInt(e[2]));
            ej.put("flag", e[3]);
            ej.put("extra", e[4] == null ? "" : e[4]);
            evs.put(ej);
        }
        o.put("events", evs);

        JSONArray gts = new JSONArray();
        for (String[] g : st.allGuests(1000)) {
            JSONObject gj = new JSONObject();
            gj.put("ts", Long.parseLong(g[1]));
            gj.put("name", g[2]); gj.put("mobile", g[3]); gj.put("plate", g[4]);
            gj.put("nid", g[5]); gj.put("outTs", Long.parseLong(g[6]));
            gts.put(gj);
        }
        o.put("guests", gts);

        JSONArray pks = new JSONArray();
        for (String[] k : st.allPackages(1000)) {
            JSONObject kj = new JSONObject();
            kj.put("ts", Long.parseLong(k[1]));
            kj.put("kind", k[2]); kj.put("barcode", k[3]); kj.put("rname", k[4]);
            kj.put("rmobile", k[5]); kj.put("rblock", k[6]); kj.put("sms", k[7]);
            pks.put(kj);
        }
        o.put("packages", pks);

        st.close();
        return o.toString();
    }

    /**
     * بازیابی از JSON. خروجی: پیام خلاصهٔ فارسی.
     * توجه: رویدادهای بازیابی‌شده زنجیرهٔ HMAC خودشان را از پشتیبان می‌آورند (ادامهٔ همان زنجیره).
     */
    public static String restore(Context c, String json) throws Exception {
        JSONObject o = new JSONObject(json);
        if (!"negahban-patrol".equals(o.optString("app")))
            throw new Exception("این فایل پشتیبان سامانهٔ گشت نیست");
        JSONObject cfg = o.optJSONObject("cfg");
        if (cfg == null) throw new Exception("فایل ناقص است (cfg)");

        // ۱) تنظیمات
        SharedPreferences.Editor ed = Cfg.p(c).edit();
        java.util.Iterator<String> it = cfg.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = cfg.opt(k);
            if (v == null) continue;
            if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Number) {
                if (k.equals("chatId") || k.equals("lastSync")) ed.putLong(k, ((Number) v).longValue());
                else ed.putInt(k, ((Number) v).intValue());
            } else ed.putString(k, String.valueOf(v));
        }
        ed.apply();

        // ۲) ساکنین (جایگزین کامل)
        PatrolStore st = new PatrolStore(c);
        st.clearRes();
        JSONArray res = o.optJSONArray("residents");
        int nRes = 0;
        if (res != null) {
            for (int i = 0; i < res.length(); i++) {
                JSONObject r = res.optJSONObject(i);
                if (r == null) continue;
                st.addRes(r.optString("name"), r.optString("mobile"), r.optString("plate"),
                        r.optString("car"), r.optString("parking"));
                nRes++;
            }
        }

        // ۳) رویدادها (افزودنی — رویداد تکراری نداریم چون دستگاه تازه معمولاً خالی است)
        JSONArray evs = o.optJSONArray("events");
        int nEv = 0;
        if (evs != null) {
            for (int i = 0; i < evs.length(); i++) {
                JSONObject e = evs.optJSONObject(i);
                if (e == null) continue;
                Integer station = e.has("station") ? e.getInt("station") : null;
                st.addEvRaw(e.optLong("ts"), e.optString("type", ""), station,
                        e.optString("flag", ""), e.optString("extra", ""));
                nEv++;
            }
        }
        // ۴) مهمان‌ها (جایگزین کامل)
        JSONArray gts = o.optJSONArray("guests");
        int nG = 0;
        if (gts != null) {
            st.clearGuests();
            for (int i = 0; i < gts.length(); i++) {
                JSONObject g = gts.optJSONObject(i);
                if (g == null) continue;
                st.addGuestTs(g.optLong("ts"), g.optString("name"), g.optString("mobile"),
                        g.optString("plate"), g.optString("nid"), g.optLong("outTs"));
                nG++;
            }
        }

        // ۵) بسته‌ها (جایگزین کامل)
        JSONArray pks = o.optJSONArray("packages");
        int nP = 0;
        if (pks != null) {
            st.clearPackages();
            for (int i = 0; i < pks.length(); i++) {
                JSONObject k = pks.optJSONObject(i);
                if (k == null) continue;
                st.addPackageTs(k.optLong("ts"), k.optString("kind"), k.optString("barcode"),
                        k.optString("rname"), k.optString("rmobile"), k.optString("rblock"), k.optString("sms", "PENDING"));
                nP++;
            }
        }

        st.close();
        return "بازیابی انجام شد ✅\nساکنین: " + Scheduler.fa(String.valueOf(nRes))
                + " نفر\nرویدادها: " + Scheduler.fa(String.valueOf(nEv)) + " مورد"
                + "\nمهمان‌ها: " + Scheduler.fa(String.valueOf(nG)) + " مورد"
                + "\nبسته‌ها: " + Scheduler.fa(String.valueOf(nP)) + " مورد";
    }

    /** خواندن کل یک استریم به رشته */
    public static String readStream(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }

    /** نوشتن رشته در استریم مقصد (برای ACTION_CREATE_DOCUMENT) */
    public static void writeStream(OutputStream out, String s) throws Exception {
        out.write(s.getBytes("UTF-8"));
        out.flush();
    }
}

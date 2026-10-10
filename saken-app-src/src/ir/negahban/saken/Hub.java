package ir.negahban.saken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * اتصال ساکن‌یار به هاب ساختمان: ارسال درخواست‌ها به مدیریت و خواندن وضعیت مشترک
 * (بسته‌ها، شارژها، فهرست اهالی) — احراز با کد ساختمان.
 */
public class Hub {

    public static final String HOME = "https://negahbanyar.xo.je/";
    public static final String API = "https://negahbanyar.xo.je/api/hub.php";
    static final String APP_KEY = "NGY-CLOUD-7c4e2f91a8d63b50e7f1c2a493d85b6e";

    static String sCookie;
    static long sCookieAt;

    public static class Res {
        public boolean ok;
        public String err = "";
        public String payload = "";
        public long ts;
    }

    /** کد ساختمان از تنظیمات */
    public static String code(android.content.Context c) {
        return Cfg.p(c).getString("bldgCode", "").trim();
    }

    // ================= عملیات =================

    public static Res msg(String code, String to, String from, String kind, String payload) {
        try {
            return parse(call(API + "?op=msg", "POST",
                    "code=" + code + "&to=" + enc(to) + "&from=" + enc(from)
                            + "&kind=" + kind + "&payload=" + enc(payload == null ? "" : payload)));
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    public static class Res2 extends Res { public String status = ""; public String name = "", unit = "", role = ""; }

    /** ثبت‌نام اهالی (سرور پیام REG_REQ را خودش برای مدیر میفرستد) */
    public static Res resReg(String code, String uid, String name, String mobile, String unit,
                             String role, String car, String plate, String park, String nid, int nres) {
        try {
            Res r = call(API + "?op=res_reg", "POST",
                    "code=" + enc(code) + "&uid=" + enc(uid) + "&name=" + enc(name)
                    + "&mobile=" + enc(mobile) + "&unit=" + enc(unit) + "&role=" + role
                    + "&car=" + enc(car) + "&plate=" + enc(plate) + "&park=" + enc(park)
                    + "&nid=" + enc(nid) + "&nres=" + nres);
            return parse(r);
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    /** وضعیت ثبت‌نام این گوشی: none | PENDING | OK | NO */
    public static Res2 resStatus(String code, String uid) {
        Res2 r = new Res2();
        try {
            Res raw = call(API + "?op=res_status&code=" + enc(code) + "&uid=" + enc(uid), "GET", null);
            if (!raw.ok) { r.ok = false; r.err = raw.err; return r; }
            JSONObject o = new JSONObject(raw.payload);
            if (!o.optBoolean("ok", false)) { r.ok = false; r.err = humanErr(o.optString("err", "")); return r; }
            r.ok = true;
            r.status = o.optString("status", "none");
            r.name = o.optString("name", "");
            r.unit = o.optString("unit", "");
            r.role = o.optString("role", "");
            return r;
        } catch (Exception e) { r.ok = false; r.err = String.valueOf(e); return r; }
    }

    /** ثبت درخواست مهمان روی سرور — تأیید توسط نگهبان (یا مدیر)، نتیجه با GU_OK/GU_NO برمی‌گردد */
    public static Res guestReq(String code, String uid, String name, String mobile, String plate, String when) {
        try {
            return parse(call(API + "?op=guest_req", "POST",
                    "code=" + enc(code) + "&uid=" + enc(uid) + "&name=" + enc(name)
                            + "&mobile=" + enc(mobile) + "&plate=" + enc(plate) + "&when=" + enc(when)));
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    /** متن اعلان زنگوله بر اساس نوع پیام */
    public static String notifText(String kind, String payload) {
        try {
            JSONObject j = new JSONObject(payload);
            if ("GU_OK".equals(kind)) return "✅ ورود مهمان «" + j.optString("guest", "") + "» تأیید شد";
            if ("GU_NO".equals(kind)) return "⛔ درخواست مهمان «" + j.optString("guest", "") + "» تأیید نشد";
            if ("ROOF_KEY_OK".equals(kind)) return "🔑 درخواست کلید پشت‌بام تأیید شد — از نگهبانی تحویل بگیر";
            if ("MOVE_OK".equals(kind)) return "🚚 درخواست اسباب‌کشی تأیید شد — با هماهنگی نگهبانی انجام شود";
            if ("MOVE_NO".equals(kind)) return "🚚 درخواست اسباب‌کشی تأیید نشد";
            if ("ELEV_OK".equals(kind)) return "🛗 استفاده از آسانسور تأیید شد";
            if ("ELEV_NO".equals(kind)) return "🛗 درخواست استفاده از آسانسور تأیید نشد";
            if ("ROOF_KEY_NO".equals(kind)) return "🔑 درخواست کلید پشت‌بام تأیید نشد";
            if ("REG_OK".equals(kind)) return "📝 عضویتت در ساختمان تأیید شد 🎉";
            if ("REG_NO".equals(kind)) return "📝 عضویتت تأیید نشد — با مدیر هماهنگ کن";
            if ("NEWS".equals(kind)) return "📢 " + j.optString("title", "اطلاعیه") + ": " + j.optString("body", "");
            return "💬 پیام جدید در صندوق";
        } catch (Exception e) { return "💬 پیام جدید در صندوق"; }
    }

    public static class MsgItem { public long id, ts; public String src, kind, payload; }
    public static long sLastId = -1;

    public static ArrayList<MsgItem> msgs(String code, String to, long after) {
        ArrayList<MsgItem> out = new ArrayList<MsgItem>();
        try {
            Res r = call(API + "?op=msgs&code=" + code + "&to=" + enc(to) + "&after=" + after, "GET", null);
            if (!r.ok) return out;
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) return out;
            JSONArray arr = o.optJSONArray("items");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject m = arr.getJSONObject(i);
                MsgItem it = new MsgItem();
                it.id = m.optLong("id"); it.ts = m.optLong("ts");
                it.src = m.optString("src", ""); it.kind = m.optString("kind", "");
                it.payload = m.optString("payload", "");
                out.add(it);
            }
            sLastId = o.optLong("last", after);
        } catch (Exception ignored) { }
        return out;
    }

    public static Res dataGet(String code, String key) {
        try {
            Res r = call(API + "?op=data_get&code=" + code + "&key=" + enc(key), "GET", null);
            if (!r.ok) return r;
            return parse(r);
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    public static Res dataPut(String code, String key, String payload, boolean append) {
        try {
            return call(API + "?op=data_put", "POST",
                    "code=" + code + "&key=" + enc(key) + "&mode=" + (append ? "append" : "replace")
                            + "&payload=" + enc(payload == null ? "" : payload));
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    static Res parse(Res r) {
        if (!r.ok) return r;
        try {
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) {
                r.ok = false;
                r.err = humanErr(o.optString("err", ""));
                return r;
            }
            r.ts = o.optLong("ts", 0);
            r.payload = o.optString("payload", "");
            return r;
        } catch (Exception e) {
            r.ok = false;
            r.err = "پاسخ نامفهوم";
            return r;
        }
    }

    static String humanErr(String e) {
        if (e.equals("auth")) return "کلید پذیرفته نشد";
        if (e.equals("empty")) return "هنوز چیزی ثبت نشده";
        if (e.equals("nobldg")) return "کد ساختمان درست نیست — از ⚙️ چک کن";
        if (e.equals("db")) return "خطای پایگاه‌داده";
        if (e.equals("size") || e.equals("toolarge")) return "حجم نامناسب";
        return "خطا: " + e;
    }

    static Res err(String s) {
        Res r = new Res();
        r.err = s;
        return r;
    }

    static String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    // ================= شبکه + چالش میزبان =================

    static Res call(String url, String method, String form) {
        Res r = raw(url, method, form);
        if (r.payload != null && r.payload.contains("/aes.js")) {
            sCookie = null;
            r = raw(url, method, form);
        }
        Res out = new Res();
        out.ok = r.ok;
        out.payload = r.payload == null ? "" : r.payload;
        out.ts = r.ts;
        if (!r.ok) out.err = r.err;
        return out;
    }

    static Res raw(String url, String method, String form) {
        Res r = new Res();
        try {
            ensureCookie();
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestMethod(method);
            c.setRequestProperty("User-Agent", "negahban-yar");
            c.setRequestProperty("X-NGY-KEY", APP_KEY);
            if (sCookie != null && !sCookie.isEmpty()) c.setRequestProperty("Cookie", sCookie);
            if (form != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                OutputStream os = c.getOutputStream();
                os.write(form.getBytes("UTF-8"));
                os.close();
            }
            r.ts = c.getResponseCode();
            InputStream in = r.ts >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                r.payload = bo.toString("UTF-8");
            }
            r.ok = r.ts == 200 && !(r.payload != null && r.payload.contains("/aes.js"));
            if (!r.ok) r.err = r.ts == 0 ? "اینترنت در دسترس نیست" : "پاسخ: " + r.ts;
        } catch (Exception e) {
            r.ok = false;
            r.err = "اینترنت در دسترس نیست";
        }
        return r;
    }

    static void ensureCookie() throws Exception {
        if (sCookie != null && System.currentTimeMillis() - sCookieAt < 5 * 3600_000L) return;
        HttpURLConnection c = (HttpURLConnection) new URL(HOME).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "negahban-yar");
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while (in != null && (n = in.read(buf)) > 0) bo.write(buf, 0, n);
        String html = bo.toString("UTF-8");
        Matcher m = Pattern.compile("toNumbers\\(\"([0-9a-f]{32})\"\\)").matcher(html);
        String a = null, b = null, cc = null;
        if (m.find()) a = m.group(1);
        if (m.find()) b = m.group(1);
        if (m.find()) cc = m.group(1);
        if (a == null || b == null || cc == null) {
            sCookie = "";
            sCookieAt = System.currentTimeMillis();
            return;
        }
        Cipher ci = Cipher.getInstance("AES/CBC/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, new SecretKeySpec(hex(a), "AES"), new IvParameterSpec(hex(b)));
        byte[] pt = ci.doFinal(hex(cc));
        sCookie = "__test=" + hex(pt) + "; path=/";
        sCookieAt = System.currentTimeMillis();
    }

    static byte[] hex(String s) {
        byte[] o = new byte[s.length() / 2];
        for (int i = 0; i < o.length; i++)
            o[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return o;
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}

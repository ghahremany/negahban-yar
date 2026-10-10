package ir.negahban.patrol;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** اتصال نگهبان‌یار به هاب ساختمان (گزارش‌ها، بسته‌ها، وضعیت مشترک) — احراز با کد ساختمان */
public class Hub {

    public static final String HOME = "https://negahbanyar.xo.je/";
    public static final String API = "https://negahbanyar.xo.je/api/hub.php";
    static final String APP_KEY = "NGY-CLOUD-7c4e2f91a8d63b50e7f1c2a493d85b6e";

    static String sCookie;
    static long sCookieAt;

    public static class Res {
        public boolean ok;
        public int code;
        public String err = "";
        public String payload = "";
        public long ts;
    }

    public static String code(android.content.Context c) {
        return Cfg.p(c).getString("bldgCode", "").trim();
    }

    /** پیام به صندوق مقصد (مثلاً «modir») */
    public static Res msg(String code, String to, String from, String kind, String payload) {
        try {
            return parse(call(API + "?op=msg", "POST",
                    "code=" + enc(code) + "&to=" + enc(to) + "&from=" + enc(from)
                            + "&kind=" + kind + "&payload=" + enc(payload == null ? "" : payload)));
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    public static class MsgItem { public long id, ts; public String src, kind, payload; }
    public static long sLastId = -1;

    /** پیام‌های جدید صندوق این اپ (مثلاً اطلاع تحویل کلید) */
    public static String sLastErr = null;

    public static java.util.ArrayList<MsgItem> msgs(String code, String to, long after) {
        java.util.ArrayList<MsgItem> out = new java.util.ArrayList<MsgItem>();
        try {
            Res r = call(API + "?op=msgs&code=" + enc(code) + "&to=" + enc(to) + "&after=" + after, "GET", null);
            if (!r.ok) { sLastErr = r.err; return out; }
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) { sLastErr = o.optString("err", "خطای سرور"); return out; }
            sLastErr = null;
            org.json.JSONArray arr = o.optJSONArray("items");
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

    /** متن اعلان زنگوله بر اساس نوع پیام — null یعنی اعلان نمی‌خواهد */
    public static String notifText(String kind, String payload) {
        try {
            JSONObject j = new JSONObject(payload);
            if ("ROOF_KEY_OK".equals(kind))
                return "🔑 کلید پشت‌بام «" + j.optString("name", "ساکن") + "» (واحد " + j.optString("unit", "—") + ") تأیید شد — تحویل داده شود";
            if ("MOVE_OK".equals(kind)) return "🚚 اسباب‌کشی «" + j.optString("name", "ساکن") + "» (واحد " + j.optString("unit", "—") + ") تأیید شد — هماهنگی شود";
            if ("ELEV_OK".equals(kind)) return "🛗 استفاده از آسانسور توسط «" + j.optString("name", "ساکن") + "» (واحد " + j.optString("unit", "—") + ") تأیید شد";
            if ("ROOF_KEY_NO".equals(kind)) return "⛔ درخواست کلید پشت‌بام «" + j.optString("name", "شخص") + "» توسط مدیر رد شد";
            if ("MOVE_NO".equals(kind)) return "⛔ اسباب‌کشی «" + j.optString("name", "شخص") + "» توسط مدیر رد شد";
            if ("ELEV_NO".equals(kind)) return "⛔ درخواست آسانسور «" + j.optString("name", "شخص") + "» توسط مدیر رد شد";
            if ("GU_OK".equals(kind) || "GU_NO".equals(kind)) {
                boolean ok = "GU_OK".equals(kind);
                String by = "modir".equals(j.optString("by", "")) ? " توسط مدیر" : "";
                return (ok ? "✅ ورود مهمان «" : "⛔ درخواست مهمان «") + j.optString("guest", "") + "»" + (ok ? " تأیید شد" : " رد شد") + by;
            }
            if ("NEWS".equals(kind)) return "📢 " + j.optString("title", "اطلاعیه") + ": " + j.optString("body", "");
            if ("GU_GUEST_REQ".equals(kind)) return null;
            return "💬 پیام جدید در صندوق";
        } catch (Exception e) { return "💬 پیام جدید"; }
    }

    /** درخواست‌های مهمانِ ساکن‌ها — در انتظار تأیید نگهبان */
    public static java.util.ArrayList<String[]> guestList(String code) {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<String[]>();
        try {
            Res r = call(API + "?op=guest_list&code=" + enc(code) + "&filter=pending", "GET", null);
            if (!r.ok) return out;
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) return out;
            org.json.JSONArray arr = o.optJSONArray("items");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject g = arr.getJSONObject(i);
                out.add(new String[]{String.valueOf(g.optLong("id")), g.optString("guest", ""),
                        g.optString("mobile", ""), g.optString("plate", ""), g.optString("when", "")});
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** تأیید/رد درخواست مهمانِ ساکن (نگهبان) — مدیر و ساکن خودکار اطلاع می‌گیرند */
    public static Res guestDecide(String code, long id, boolean ok) {
        try {
            return parse(call(API + "?op=guest_decide", "POST",
                    "code=" + enc(code) + "&id=" + id + "&ok=" + (ok ? "1" : "0") + "&by=guard"));
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    public static Res dataGet(String code, String key) {
        try {
            Res r = call(API + "?op=data_get&code=" + enc(code) + "&key=" + enc(key), "GET", null);
            if (!r.ok) return r;
            return parse(r);
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    public static Res dataPut(String code, String key, String payload, boolean append) {
        try {
            return call(API + "?op=data_put", "POST",
                    "code=" + enc(code) + "&key=" + enc(key) + "&mode=" + (append ? "append" : "replace")
                            + "&payload=" + enc(payload == null ? "" : payload));
        } catch (Exception e) { return err(String.valueOf(e)); }
    }

    static Res parse(Res r) {
        if (!r.ok) return r;
        try {
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) {
                r.ok = false;
                r.err = o.optString("err", "");
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
        out.code = r.code;
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
            r.code = c.getResponseCode();
            r.ts = r.code;
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                r.payload = bo.toString("UTF-8");
            }
            r.ok = r.code == 200 && !(r.payload != null && r.payload.contains("/aes.js"));
            if (!r.ok) r.err = r.code == 0 ? "اینترنت در دسترس نیست" : "پاسخ: " + r.code;
        } catch (Exception e) {
            r.ok = false;
            r.code = 0;
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

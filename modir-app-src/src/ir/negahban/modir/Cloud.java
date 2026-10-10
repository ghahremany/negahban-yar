package ir.negahban.modir;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * پشتیبان ابری سوئیت «یار»:
 * فایل پشتیبانِ رمزنگاری‌شده (AES-GCM، کلید از توکن ربات) به فضای ابری ما فرستاده می‌شود؛
 * ۱۰ نسخهٔ آخر هر ساختمان نگه داشته می‌شود. بازیابی فقط با همان توکن ربات ممکن است.
 * این کلاس چالش امنیتی میزبان را هم خودکار حل می‌کند (کوکی جلسه).
 */
public class Cloud {

    public static final String HOME = "https://negahbanyar.xo.je/";
    public static final String API = "https://negahbanyar.xo.je/api/backup.php";
    static final String APP_KEY = "NGY-CLOUD-7c4e2f91a8d63b50e7f1c2a493d85b6e";
    static final String MAGIC = "NGYE1:";
    static final long DAY = 86400000L;

    static String sCookie;
    static long sCookieAt;

    public static class Res {
        public boolean ok;
        public String err = "";
        public String payload = "";
        public long ts;
    }

    // ================= رمزنگاری =================

    public static String encrypt(String keyMaterial, String plain) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, key(keyMaterial), new GCMParameterSpec(128, iv));
        byte[] ct = ci.doFinal(plain.getBytes("UTF-8"));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(iv);
        bo.write(ct);
        return MAGIC + android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
    }

    public static String decrypt(String keyMaterial, String blob) throws Exception {
        if (!blob.startsWith(MAGIC)) return blob;
        byte[] all = android.util.Base64.decode(blob.substring(MAGIC.length()), android.util.Base64.NO_WRAP);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, key(keyMaterial), new GCMParameterSpec(128, all, 0, 12));
        byte[] pt = ci.doFinal(all, 12, all.length - 12);
        return new String(pt, "UTF-8");
    }

    private static SecretKeySpec key(String material) throws Exception {
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(material.getBytes("UTF-8")), "AES");
    }

    // ================= عملیات =================

    /** برچسب پشتیبان هر ساختمان = کد ساختمان */
    public static String label(Context c) {
        return "b" + Cfg.p(c).getString("bldgCode", "").trim();
    }

    // ================= هاب ارتباطی =================

    /** ثبت ساختمان و دریافت کد (کد = شمارهٔ همراه مدیر) — خروجی: Res.payload = کد */
    public static Res hubReg(String name, String mobile) {
        try {
            Res r = call(API.replace("backup.php", "hub.php") + "?op=reg", "POST",
                    "name=" + java.net.URLEncoder.encode(name == null ? "" : name, "UTF-8")
                    + "&mobile=" + java.net.URLEncoder.encode(mobile == null ? "" : mobile, "UTF-8"));
            if (!r.ok) return r;
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) { r.ok = false; r.err = humanErr(o.optString("err", "")); return r; }
            r.payload = o.optString("code", "");
            return r;
        } catch (Exception e) { Res r = new Res(); r.err = String.valueOf(e); return r; }
    }

    /** خواندن وضعیت مشترک — خروجی: Res.payload = رشتهٔ JSON */
    public static Res hubDataGet(String code, String key) {
        try {
            Res r = call(API.replace("backup.php", "hub.php") + "?op=data_get&code=" + code + "&key="
                    + java.net.URLEncoder.encode(key, "UTF-8"), "GET", null);
            if (!r.ok) return r;
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) { r.ok = false; r.err = humanErr(o.optString("err", "")); return r; }
            r.payload = o.optString("payload", "");
            return r;
        } catch (Exception e) { Res r = new Res(); r.err = String.valueOf(e); return r; }
    }

    /** نوشتن وضعیت مشترک (append=افزودن به آرایه) */
    public static Res hubDataPut(String code, String key, String payload, boolean append) {
        try {
            return call(API.replace("backup.php", "hub.php") + "?op=data_put", "POST",
                    "code=" + code + "&key=" + java.net.URLEncoder.encode(key, "UTF-8")
                    + "&mode=" + (append ? "append" : "replace")
                    + "&payload=" + java.net.URLEncoder.encode(payload, "UTF-8"));
        } catch (Exception e) { Res r = new Res(); r.err = String.valueOf(e); return r; }
    }

    /** فهرست اهالی ثبت‌نام‌شده (filter: OK | pending) */
    public static java.util.ArrayList<JSONObject> hubResList(String code, String filter) {
        java.util.ArrayList<JSONObject> out = new java.util.ArrayList<JSONObject>();
        try {
            Res r = call(API.replace("backup.php", "hub.php") + "?op=res_list&code=" + code
                    + (filter == null || filter.isEmpty() ? "" : "&filter=" + filter), "GET", null);
            if (!r.ok) return out;
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) return out;
            org.json.JSONArray arr = o.optJSONArray("items");
            for (int i = 0; arr != null && i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (Exception ignored) { }
        return out;
    }

    /** تأیید/رد ثبت‌نام اهالی (سرور پیام REG_OK/REG_NO را خودش میفرستد) */
    public static Res hubResDecide(String code, String uid, boolean ok) {
        try {
            return call(API.replace("backup.php", "hub.php") + "?op=res_decide", "POST",
                    "code=" + code + "&uid=" + java.net.URLEncoder.encode(uid, "UTF-8")
                    + "&ok=" + (ok ? "1" : "0"));
        } catch (Exception e) { Res r = new Res(); r.err = String.valueOf(e); return r; }
    }

    /** تأیید/رد درخواست مهمان ساکن (به‌جای نگهبان) */
    public static Res hubGuestDecide(String code, long gid, boolean ok) {
        try {
            return call(API.replace("backup.php", "hub.php") + "?op=guest_decide", "POST",
                    "code=" + code + "&id=" + gid + "&ok=" + (ok ? "1" : "0") + "&by=modir");
        } catch (Exception e) { Res r = new Res(); r.err = String.valueOf(e); return r; }
    }

    /** ارسال پیام داخل ساختمان */
    public static Res hubMsg(String code, String to, String from, String kind, String payload) {
        try {
            return call(API.replace("backup.php", "hub.php") + "?op=msg", "POST",
                    "code=" + code + "&to=" + java.net.URLEncoder.encode(to, "UTF-8")
                    + "&from=" + java.net.URLEncoder.encode(from, "UTF-8")
                    + "&kind=" + kind + "&payload=" + java.net.URLEncoder.encode(payload == null ? "" : payload, "UTF-8"));
        } catch (Exception e) { Res r = new Res(); r.err = String.valueOf(e); return r; }
    }

    /** پیام‌های جدید صندوق — خروجی: Res.lastId و Res.items */
    public static class MsgItem { public long id, ts; public String src, kind, payload; }
    public static long sLastId = -1;

    public static java.util.ArrayList<MsgItem> hubMsgs(String code, String to, long after) {
        java.util.ArrayList<MsgItem> out = new java.util.ArrayList<MsgItem>();
        try {
            Res r = call(API.replace("backup.php", "hub.php") + "?op=msgs&code=" + code + "&to="
                    + java.net.URLEncoder.encode(to, "UTF-8") + "&after=" + after, "GET", null);
            if (!r.ok) return out;
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) return out;
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

    /** 📅 تاریخچهٔ رویداد — همهٔ پیام‌های ساختمان از هاب (آرشیو ۴۰۰ روزه) */
    public static java.util.ArrayList<MsgItem> hubEvents(String code, long after) {
        java.util.ArrayList<MsgItem> out = new java.util.ArrayList<MsgItem>();
        try {
            Res r = call(API.replace("backup.php", "hub.php") + "?op=events&code=" + code
                    + "&after=" + after + "&limit=3000", "GET", null);
            if (!r.ok) return out;
            JSONObject o = new JSONObject(r.payload);
            if (!o.optBoolean("ok", false)) return out;
            org.json.JSONArray arr = o.optJSONArray("items");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject m = arr.getJSONObject(i);
                MsgItem it = new MsgItem();
                it.id = m.optLong("id"); it.ts = m.optLong("ts");
                it.src = m.optString("src", ""); it.kind = m.optString("kind", "");
                it.payload = m.optString("payload", "");
                out.add(it);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** ارسال پشتیبان رمزشده */
    public static Res save(Context c, String blob) {
        try {
            String form = "label=" + URLEncoder.encode(label(c), "UTF-8")
                    + "&payload=" + URLEncoder.encode(blob, "UTF-8");
            return parse(call(API + "?op=save", "POST", form));
        } catch (Exception e) {
            Res r = new Res();
            r.err = String.valueOf(e);
            return r;
        }
    }

    /** آخرین پشتیبان */
    public static Res latest(Context c) {
        try {
            return parse(call(API + "?op=latest&label=" + URLEncoder.encode(label(c), "UTF-8"), "GET", null));
        } catch (Exception e) {
            Res r = new Res();
            r.err = String.valueOf(e);
            return r;
        }
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
            r.err = "پاسخ نامفهوم از فضای ابری";
            return r;
        }
    }

    static String humanErr(String e) {
        if (e.equals("auth")) return "کلید ابر پذیرفته نشد";
        if (e.equals("empty")) return "هنوز پشتیبانی ذخیره نشده است";
        if (e.equals("db")) return "خطای پایگاه‌دادهٔ ابر";
        if (e.equals("size") || e.equals("toolarge")) return "حجم پشتیبان نامناسب است";
        if (e.equals("label")) return "شناسهٔ ساختمان تنظیم نشده (اول اتصال به بله را کامل کن)";
        return "خطای ابر: " + e;
    }

    // ================= پایهٔ شبکه =================

    static Res call(String url, String method, String form) {
        Res r = raw(url, method, form);
        if (r.payload != null && r.payload.contains("/aes.js")) { // چالش تازه → حل و یک‌بار دیگر
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
            if (sCookie != null) c.setRequestProperty("Cookie", sCookie);
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
            if (!r.ok && r.payload != null && r.payload.contains("/aes.js")) r.err = "چالش امنیتی میزبان";
            else if (!r.ok) r.err = "پاسخ فضای ابری: " + r.ts;
        } catch (Exception e) {
            r.ok = false;
            r.err = "اینترنت در دسترس نیست";
        }
        return r;
    }

    /** حل چالش امنیتی میزبان: از صفحهٔ اصلی مقدارها را می‌خواند و کوکی جلسه میسازد (۶ ساعت اعتبار) */
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
        if (a == null || b == null || cc == null) { // بدون چالش → نیازی به کوکی نیست
            sCookie = "";
            sCookieAt = System.currentTimeMillis();
            return;
        }
        Cipher ci = Cipher.getInstance("AES/CBC/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, new SecretKeySpec(hex(a), "AES"), new javax.crypto.spec.IvParameterSpec(hex(b)));
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

    // ================= خودکار =================

    /** پشتیبان خودکار روزانه — هنگام باز شدن اپ */
    public static void autoIfDue(final Context c) {
        try {
            if (Cfg.p(c).getString("bldgCode", "").trim().isEmpty()) return;
            long last = Cfg.p(c).getLong("lastCloudAuto", 0);
            if (PatrolStore.now() - last <= DAY) return;
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        String blob = encrypt(Cfg.token(c), Backup.make(c));
                        final Res r = save(c, blob);
                        if (r.ok) {
                            Cfg.p(c).edit().putLong("lastCloudAuto", PatrolStore.now()).apply();
                            ((android.app.Activity) c).runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    android.widget.Toast.makeText(c, "☁️ پشتیبان خودکار ذخیره شد", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } catch (Exception ignored) { }
                }
            }).start();
        } catch (Exception ignored) { }
    }
}

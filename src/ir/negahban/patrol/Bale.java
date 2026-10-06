package ir.negahban.patrol;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** ارتباط مستقیم اپ با سرور ربات بله — بدون هیچ سرور واسط */
public class Bale {

    public static class Res {
        public boolean ok;
        public int code;
        public String body = "";
        public long serverTime; // از هدر Date — برای کنترل ساعت دستگاه
    }

    static Res call(String token, String method, String jsonBody) {
        Res r = new Res();
        try {
            URL u = new URL("https://tapi.bale.ai/bot" + token + "/" + method);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Content-Type", "application/json");
            if (jsonBody != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                OutputStream os = c.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.close();
            }
            r.serverTime = serverDate(c);
            r.code = c.getResponseCode();
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                r.body = bo.toString("UTF-8");
            }
            r.ok = r.code == 200 && r.body.contains("\"ok\":true");
        } catch (Exception e) {
            r.ok = false;
            r.body = String.valueOf(e);
        }
        return r;
    }

    static long serverDate(HttpURLConnection c) {
        try {
            String d = c.getHeaderField("Date");
            if (d == null) return 0;
            SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("GMT"));
            return f.parse(d).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    public static Res send(String token, long chat, String text) {
        JSONObject o = new JSONObject();
        try {
            o.put("chat_id", chat);
            o.put("text", text);
        } catch (Exception ignored) {}
        return call(token, "sendMessage", o.toString());
    }

    /** خواندن کامل یک استریم به رشته (برای پاسخ‌های API و به‌روزرسان) */
    public static String readRawStream(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }

    public static boolean tokenOk(String token) {
        return call(token, "getMe", null).ok;
    }

    /** ارسال فایل (برای پشتیبان‌گیری) به چت مقصد — multipart مثل آپلود رسمی Bot API */
    public static Res sendDocument(String token, long chat, byte[] data, String filename, String caption) {
        Res r = new Res();
        try {
            String B = "----NGHBN" + System.currentTimeMillis();
            URL u = new URL("https://tapi.bale.ai/bot" + token + "/sendDocument");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + B);
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            body.write(("--" + B + "\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + chat + "\r\n").getBytes("UTF-8"));
            if (caption != null && !caption.isEmpty()) {
                body.write(("--" + B + "\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n").getBytes("UTF-8"));
                body.write(caption.getBytes("UTF-8"));
                body.write("\r\n".getBytes("UTF-8"));
            }
            body.write(("--" + B + "\r\nContent-Disposition: form-data; name=\"document\"; filename=\"" + filename + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes("UTF-8"));
            body.write(data);
            body.write(("\r\n--" + B + "--\r\n").getBytes("UTF-8"));
            java.io.OutputStream os = c.getOutputStream();
            os.write(body.toByteArray());
            os.close();
            r.serverTime = serverDate(c);
            r.code = c.getResponseCode();
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                r.body = bo.toString("UTF-8");
            }
            r.ok = r.code == 200 && r.body.contains("\"ok\":true");
        } catch (Exception e) {
            r.ok = false;
            r.body = String.valueOf(e);
        }
        return r;
    }

    /** یک پیام ورودی ربات */
    public static class Update {
        public long updateId, chatId;
        public String text = "";
    }

    /** getUpdates با long-poll برای ربات ساکنین؛ خروجی null = خطا (توکن/شبکه) */
    public static java.util.ArrayList<Update> pollUpdates(String token, long offset) {
        try {
            URL u = new URL("https://tapi.bale.ai/bot" + token + "/getUpdates?timeout=25"
                    + (offset > 0 ? "&offset=" + offset : ""));
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(40000);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : readRawStream(in);
            if (code != 200) return null;
            JSONObject j = new JSONObject(body);
            if (!j.optBoolean("ok", false)) return null;
            java.util.ArrayList<Update> out = new java.util.ArrayList<>();
            JSONArray a = j.getJSONArray("result");
            for (int i = 0; i < a.length(); i++) {
                JSONObject up = a.getJSONObject(i);
                JSONObject msg = up.optJSONObject("message");
                if (msg == null) continue;
                Update x = new Update();
                x.updateId = up.optLong("update_id", 0);
                x.chatId = msg.getJSONObject("chat").optLong("id", 0);
                x.text = msg.optString("text", "").trim();
                if (x.chatId != 0 && !x.text.isEmpty()) out.add(x);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** chat_id آخرین پیامی که به ربات رسیده (مدیر یک‌بار به ربات پیام می‌دهد) */
    public static Long lastChatId(String token) {
        Res r = call(token, "getUpdates", null);
        try {
            JSONObject j = new JSONObject(r.body);
            if (!j.optBoolean("ok", false)) return null;
            JSONArray a = j.getJSONArray("result");
            if (a.length() == 0) return null;
            return a.getJSONObject(a.length() - 1).getJSONObject("message").getJSONObject("chat").getLong("id");
        } catch (Exception e) {
            return null;
        }
    }

    /** کنترل ساعت دستگاه با ساعت سرور بله؛ مغایرت بیش از ۳ دقیقه = پرچم در گزارش */
    public static void checkClock(android.content.Context ctx, Res r) {
        if (r == null || r.serverTime <= 0) return;
        long diff = Math.abs(PatrolStore.now() - r.serverTime);
        if (diff > 180000) {
            PatrolStore st = new PatrolStore(ctx);
            long last = st.countEv("TIMEFLAG", PatrolStore.now() - 6 * 3600000L, PatrolStore.now() + 3600000L);
            if (last == 0) st.addEv("TIMEFLAG", null, "CLOCK", "offsetMin=" + (diff / 60000));
        }
    }
}

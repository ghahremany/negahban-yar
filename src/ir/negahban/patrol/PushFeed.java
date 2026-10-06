package ir.negahban.patrol;

import android.app.AlertDialog;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

/** پوش نوتیفیکیشن سازنده — از فایل push.json روی صفحهٔ محصول خوانده میشود */
public class PushFeed {

    public static class Item {
        public String id = "", title = "", body = "";
    }

    /** همگام — ترد جدا؛ جدیدترین اعلانِ دیده‌نشدهٔ مربوط به این اپ یا null */
    public static Item fetchUnseen(Context c, String appKey) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(LicenseActivity.PAGE_URL + "push.json").openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != 200) return null;
            String body = Bale.readRawStream(conn.getInputStream());
            JSONArray a = new JSONArray(body);
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject j = a.getJSONObject(i);
                String app = j.optString("app", "all");
                if (!"all".equals(app) && !app.contains(appKey)) continue;
                String id = j.optString("id", "");
                if (Cfg.p(c).getString("pushSeen_" + id, "").equals("1")) continue;
                Item it = new Item();
                it.id = id;
                it.title = j.optString("title", "");
                it.body = j.optString("body", "");
                return it;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void markSeen(Context c, Item it) {
        Cfg.set(c, "pushSeen_" + it.id, "1");
    }

    public static void show(final Context c, final Item it) {
        new AlertDialog.Builder(c)
                .setTitle(it.title)
                .setMessage(it.body)
                .setPositiveButton("باشه", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { markSeen(c, it); }
                })
                .setCancelable(false)
                .show();
    }
}

package ir.negahban.patrol;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * به‌روزرسان خودکار از گیت‌هاب:
 * نسخهٔ آخر از releases/latest خوانده می‌شود (تگ v0.9 = نسخهٔ ۰٫۹)؛
 * اگر از نسخهٔ نصب‌شده بزرگ‌تر بود → دانلود APK با درصد پیشرفت → راه‌اندازی نصب‌گر.
 * نصب نهایی همیشه با تأیید کاربر است (اندروید اجازهٔ نصب بی‌صدا نمیدهد).
 */
public class Updater {

    public static final String REPO = "ghahremany/negahban-yar";
    /** فقط فایل‌های همین اپ از میان دارایی‌های انتشار (سوئیت «یار» چند APK دارد) */
    public static final String ASSET_PREFIX = "negahban-";
    public static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    private static String assetName = "";

    public static class Res {
        public boolean ok;
        public boolean updateAvailable;
        public String tag = "", notes = "", downloadUrl = "", error = "";
        public long size;
    }

    public interface Progress {
        void onProgress(int percent);
    }

    // ---------- بررسی نسخه ----------

    /** همگام — در ترد جدا صدا زده شود */
    public static Res check(Context c) {
        Res r = new Res();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(API).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "negahban-yar-app");
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : Bale.readRawStream(in);
            if (code != 200) {
                r.error = "پاسخ گیت‌هاب: " + code;
                return r;
            }
            JSONObject j = new JSONObject(body);
            r.tag = j.optString("tag_name", "").trim();
            r.notes = j.optString("body", "");
            if (r.tag.startsWith("v")) r.tag = r.tag.substring(1);

            JSONArray assets = j.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                String n = a.optString("name", "");
                if (n.startsWith(ASSET_PREFIX) && n.endsWith(".apk")) {
                    r.downloadUrl = a.optString("browser_download_url", "");
                    r.size = a.optLong("size", 0);
                    assetName = n;
                    break;
                }
            }
            if (r.downloadUrl.isEmpty()) {
                // در این انتشار فایلی برای این اپ نبود → بدون تغییر
                r.updateAvailable = false;
                r.ok = true;
                return r;
            }
            // نسخهٔ هر اپ از نام فایل خودش خوانده میشود (تگ انتشار مشترک سوئیت است)
            // مثال: modir-v1.0.1.apk → «1.0.1»
            String av = versionFromAsset(assetName, ASSET_PREFIX);
            if (av == null || av.isEmpty()) av = r.tag;
            r.tag = av; // نمایش «نسخهٔ جدید» همان نسخهٔ فایل این اپ است
            String local = localVersion(c);
            int cmp = compareVersions(av, local);
            r.updateAvailable = cmp > 0;
            Cfg.set(c, "forceTag", av);
            r.ok = true;
        } catch (Exception e) {
            r.error = String.valueOf(e);
        }
        return r;
    }

    /** «modir-v1.0.1.apk» با پیشوند «modir-» → «1.0.1» */
    static String versionFromAsset(String name, String prefix) {
        try {
            String v = name.replace(prefix, "").replace(".apk", "").trim();
            if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
            return v.trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static String localVersion(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return p.versionName == null ? "0" : p.versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** «0.10.1» در برابر «0.9» — عددی، مؤلفه‌به‌مؤلفه */
    static int compareVersions(String a, String b) {
        try {
            String[] pa = a.split("\\.");
            String[] pb = b.split("\\.");
            int n = Math.max(pa.length, pb.length);
            for (int i = 0; i < n; i++) {
                int x = i < pa.length ? Integer.parseInt(pa[i].trim()) : 0;
                int y = i < pb.length ? Integer.parseInt(pb[i].trim()) : 0;
                if (x != y) return x > y ? 1 : -1;
            }
            return 0;
        } catch (Exception e) {
            return a.compareTo(b);
        }
    }

    // ---------- دانلود ----------

    /** همگام — در ترد جدا؛ فایل update.apk در حافظهٔ خارجیِ اختصاصی اپ ذخیره می‌شود */
    public static File download(Context c, String url, long expectedSize, Progress cb) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "negahban-yar-app");
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("پاسخ گیت‌هاب: " + code);
        long total = expectedSize > 0 ? expectedSize : conn.getContentLength();
        InputStream in = conn.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long done = 0;
        int lastP = -1;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            done += n;
            if (total > 0 && cb != null) {
                int p = (int) (done * 100 / total);
                if (p != lastP) {
                    lastP = p;
                    cb.onProgress(p);
                }
            }
        }
        in.close();
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        File f = new File(dir, "update.apk");
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(bo.toByteArray());
        fo.close();
        if (cb != null) cb.onProgress(100);
        return f;
    }

    // ---------- نصب ----------

    /** آماده‌سازی: اگر «نصب از منابع ناشناس» نداده، صفحهٔ مجوز باز می‌شود (true=آماده است) */
    public static boolean ensureInstallPermission(android.app.Activity a) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                if (!a.getPackageManager().canRequestPackageInstalls()) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + a.getPackageName()));
                    a.startActivity(i);
                    return false;
                }
            } catch (Exception e) {
                // دستگاه‌های خاص — ادامه با ACTION_VIEW
            }
        }
        return true;
    }

    /** راه‌اندازی نصب‌گر با فایل دانلودشده (از طریق ApkProvider بدون FileProvider/AndroidX) */
    public static void install(android.app.Activity a, File apk) {
        Uri uri = Uri.parse("content://ir.negahban.patrol.apk/update.apk");
        Intent i;
        if (Build.VERSION.SDK_INT >= 24) i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        else i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            // آخرین راه
            try {
                i.setAction(Intent.ACTION_VIEW);
                a.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    /** --- به‌روزرسانی اجباری: کش نتیجهٔ آخرین چک --- */
    public static void setForce(Context c, boolean needed) {
        Cfg.set(c, "forceUpdate", needed);
    }

    public static boolean forceNeeded(Context c) {
        return Cfg.p(c).getBoolean("forceUpdate", false)
                && compareVersions(localVersion(c), Cfg.p(c).getString("forceTag", "")) < 0;
    }

    /** فاصلهٔ زمانی چک خودکار: یک‌بار در شبانه‌روز */
    public static boolean shouldAutoCheck(Context c) {
        long last = Cfg.p(c).getLong("lastUpdateCheck", 0);
        return PatrolStore.now() - last > 86400000L;
    }

    public static void markChecked(Context c) {
        Cfg.set(c, "lastUpdateCheck", PatrolStore.now());
    }
}

package ir.negahban.modir;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

/**
 * جریان رابط کاربری به‌روزرسانی: دیالوگ نتیجهٔ چک → دانلود با درصد → نصب
 * auto=true: خطاها بی‌صدا و فقط «نسخهٔ جدید» نشان داده می‌شود
 */
public class UpdaterUi {

    /** اکتیویتی مرده/در حال بسته‌شدن — نمایش دیالوگ ممنوع (جلوگیری از BadTokenException) */
    private static boolean gone(final Activity a) {
        return a == null || a.isFinishing() || a.isDestroyed();
    }

    public static void run(final Activity a, final boolean auto) {
        final TextView tvStatus = new TextView(a);
        tvStatus.setText(auto ? "…" : "در حال بررسی نسخهٔ جدید…");
        final AlertDialog wait = new AlertDialog.Builder(a)
                .setTitle("🔄 به‌روزرسانی مدیر یار")
                .setView(tvStatus)
                .setCancelable(!auto)
                .setNegativeButton(auto ? null : "بستن", null)
                .show();

        new Thread(new Runnable() {
            @Override public void run() {
                final Updater.Res r = Updater.check(a);
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (gone(a)) return;
                        try { wait.dismiss(); } catch (Exception ignored) {}
                        if (!r.ok) {
                            if (!auto) toast(a, "⛔ بررسی ناموفق: " + r.error);
                            return;
                        }
                        Updater.markChecked(a);
                        if (!r.updateAvailable) {
                            if (!auto) toast(a, "✅ آخرین نسخه را داری (v" + r.tag + ")");
                            return;
                        }
                        showOffer(a, r);
                    }
                });
            }
        }).start();
    }

    private static void showOffer(final Activity a, final Updater.Res r) {
        if (gone(a)) return;
        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.length() > 700) notes = notes.substring(0, 700) + " …";
        String msg = "نسخهٔ جدید: v" + Scheduler.fa(r.tag) + "  (نسخهٔ فعلی: v"
                + Scheduler.fa(Updater.localVersion(a)) + ")\n"
                + (r.size > 0 ? "حجم: " + Scheduler.fa(String.valueOf(Math.round(r.size / 1024.0))) + " کیلوبایت\n" : "")
                + "\n" + notes;

        new AlertDialog.Builder(a)
                .setTitle("🎉 نسخهٔ جدید موجود است")
                .setMessage(msg)
                .setPositiveButton("⬇️ دانلود و نصب", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { downloadAndInstall(a, r); }
                })
                .setNegativeButton("بعداً", null)
                .show();
    }

    private static void downloadAndInstall(final Activity a, final Updater.Res r) {
        final TextView tv = new TextView(a);
        tv.setTextSize(17);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(dp(a, 24), dp(a, 18), dp(a, 24), dp(a, 10));
        tv.setText("۰٪");

        final AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("در حال دانلود…")
                .setView(tv)
                .setCancelable(false)
                .show();

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final File f = Updater.download(a, r.downloadUrl, r.size, new Updater.Progress() {
                        @Override public void onProgress(final int percent) {
                            a.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    tv.setText(Scheduler.fa(String.valueOf(percent)) + "٪  " + bar(percent));
                                }
                            });
                        }
                    });
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (gone(a)) return;
                            try { dlg.dismiss(); } catch (Exception ignored) {}
                            new AlertDialog.Builder(a)
                                    .setTitle("دانلود کامل شد ✅")
                                    .setMessage("نصب‌گر اندروید باز می‌شود — دکمهٔ «نصب/به‌روزرسانی» را بزن.\n\nبعد از نصب، برنامه به نسخهٔ جدید می‌پرد و همهٔ داده‌ها می‌مانند.")
                                    .setPositiveButton("نصب", new android.content.DialogInterface.OnClickListener() {
                                        @Override public void onClick(android.content.DialogInterface d, int w) {
                                            if (Updater.ensureInstallPermission(a)) {
                                                Updater.install(a, f);
                                            } else {
                                                toast(a, "اول اجازهٔ «نصب از منابع ناشناس» را بده و دوباره تلاش کن");
                                            }
                                        }
                                    })
                                    .setNegativeButton("انصراف", null)
                                    .show();
                        }
                    });
                } catch (final Exception e) {
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (gone(a)) return;
                            try { dlg.dismiss(); } catch (Exception ignored) {}
                            toast(a, "⛔ دانلود ناموفق: " + e);
                        }
                    });
                }
            }
        }).start();
    }

    private static String bar(int percent) {
        int filled = percent / 10;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "█" : "░");
        return sb.toString();
    }

    private static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }
}

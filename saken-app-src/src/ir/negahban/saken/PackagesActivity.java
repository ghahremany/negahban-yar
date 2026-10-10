package ir.negahban.saken;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * بسته‌های من — از طریق ربات بله:
 * اپ پیام «بسته‌های من» را به ربات می‌فرستد؛ مدیر یار (که به پیام‌های اهالی جواب میدهد)
 * فهرست بسته‌های تحویل‌نشده را برای همین ساکن می‌فرستد و اپ آخرین پاسخ را نشان میدهد.
 */
public class PackagesActivity extends Activity {

    LinearLayout box;
    TextView tvState;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.WHITE);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("📦 بسته‌های من");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        tvState = new TextView(this);
        tvState.setText("در حال پرس‌وجو از نگهبانی…");
        tvState.setTextSize(15);
        tvState.setPadding(0, dp(16), 0, dp(4));
        root.addView(tvState);

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box);

        fetch();
    }

    private void fetch() {
        final String code = Hub.code(this);
        if (code.isEmpty()) {
            tvState.setText("⚙️ اول کد ساختمان را در تنظیمات وارد کن");
            return;
        }
        tvState.setText("…");
        new Thread(new Runnable() {
            @Override public void run() {
                String text;
                Cloud0: {
                    Hub.Res r = Hub.dataGet(code, "packages");
                    if (!r.ok) {
                        text = r.err.contains("چیزی ثبت نشده")
                                ? "📭 فعلاً بسته‌ای برای تو نیست"
                                : "⛔ " + r.err;
                        break Cloud0;
                    }
                    StringBuilder sb = new StringBuilder();
                    int n = 0;
                    try {
                        org.json.JSONArray a = new org.json.JSONArray(r.payload);
                        for (int i = a.length() - 1; i >= 0; i--) {
                            org.json.JSONObject j = a.getJSONObject(i);
                            if (!"PENDING".equals(j.optString("status", "PENDING"))) continue;
                            String u = j.optString("unit", ""), m = j.optString("mobile", "");
                            if (!u.equals(Me.unit(PackagesActivity.this)) && !m.equals(Me.mobile(PackagesActivity.this))) continue;
                            n++;
                            sb.append("📦 ").append(j.optString("kind", "بسته"))
                              .append(" — گیرنده: ").append(j.optString("rname", ""))
                              .append("\n🔖 ").append(Scheduler.fa(j.optString("barcode", "")))
                              .append("\n🕐 ").append(Scheduler.jalaliDate(j.optLong("ts", 0)))
                              .append("\n\n");
                        }
                    } catch (Exception e) {
                        text = "⛔ خطا در خواندن فهرست";
                        break Cloud0;
                    }
                    text = n == 0 ? "📭 فعلاً بسته‌ای برای تو نیست"
                            : Scheduler.fa(String.valueOf(n)) + " بسته در انتظارت است:\n\n" + sb;
                }
                final String f = text;
                runOnUiThread(new Runnable() {
                    @Override public void run() { tvState.setText(f); }
                });
            }
        }).start();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

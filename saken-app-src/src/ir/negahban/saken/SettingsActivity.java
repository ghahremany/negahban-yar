package ir.negahban.saken;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** تنظیمات ساکن‌یار: کد ساختمان + اطلاعات من */
public class SettingsActivity extends Activity {

    LinearLayout box;
    EditText etCode, etName, etMobile, etUnit;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(50));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setBackgroundColor(Color.WHITE);
        scroll.addView(box);
        setContentView(scroll);

        title("⚙️ تنظیمات ساکن‌یار");

        TextView info = new TextView(this);
        info.setText("این اطلاعات را از مدیر ساختمان (یا از خودِ ربات) بگیر. توکن و آیدی، همان اطلاعات ربات ساختمان است.");
        info.setTextSize(13);
        info.setTextColor(0xFF607D8B);
        box.addView(info);

        section("🔗 ربات ساختمان");
        etCode = field("شمارهٔ همراه مدیر ساختمان (کد اتصال)", Hub.code(this), "مثلاً 09127285065", InputType.TYPE_CLASS_PHONE);

        section("👤 اطلاعات من");
        etName = field("نام و نام خانوادگی", Me.name(this), null, InputType.TYPE_CLASS_TEXT);
        etMobile = field("شمارهٔ همراه", Me.mobile(this), "09…", InputType.TYPE_CLASS_PHONE);
        etUnit = field("شمارهٔ واحد/بلوک", Me.unit(this), "مثلاً ب۳", InputType.TYPE_CLASS_TEXT);

        section("🔄 به‌روزرسانی");
        TextView v = new TextView(this);
        v.setText("نسخهٔ نصب‌شده: v" + Updater.localVersion(this) + " — به‌روزرسانی اجباری از گیت‌هاب");
        v.setTextSize(12);
        v.setTextColor(0xFF607D8B);
        box.addView(v);
        Button up = mkBtn("🔄 بررسی نسخهٔ جدید و نصب");
        up.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View vv) { UpdaterUi.run(SettingsActivity.this, false); }
        });

        section("📩 پشتیبانی");
        Button sup = mkBtn("📩 پشتیبانی (بله) — سوال یا گزارش خطا");
        sup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View vv) { supportDialog(); }
        });

        Button save = new Button(this);
        save.setText("💾 ذخیره");
        save.setTextColor(Color.WHITE);
        save.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(14);
        save.setLayoutParams(lp);
        box.addView(save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
    }

    private void save() {
        Cfg.set(this, "bldgCode", normMobile(etCode.getText().toString().trim()));
        // اطلاعات من (بدون تغییر وضعیت تأیید)
        Cfg.set(this, "myName", etName.getText().toString().trim());
        Cfg.set(this, "myMobile", etMobile.getText().toString().trim());
        Cfg.set(this, "myUnit", etUnit.getText().toString().trim());
        Toast.makeText(this, "✅ ذخیره شد", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void title(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(20);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(0xFF0F3D56);
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.addView(tv);
    }

    private void section(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(16);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(0xFF0F3D56);
        tv.setPadding(0, dp(18), 0, dp(4));
        box.addView(tv);
    }

    private EditText field(String label, String value, String hint, int inputType) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setPadding(0, dp(10), 0, dp(2));
        box.addView(tv);
        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setTextSize(14);
        box.addView(et);
        return et;
    }

    private Button mkBtn(String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        btn.setLayoutParams(lp);
        box.addView(btn);
        return btn;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }


    /** نرمال‌سازی شمارهٔ موبایل (فقط رقم؛ 0098/98 → 0) */
    static String normMobile(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '0' && ch <= '9') b.append(ch);
            else if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
        }
        String m = b.toString();
        if (m.startsWith("0098")) m = "0" + m.substring(4);
        if (m.length() == 12 && m.startsWith("98")) m = "0" + m.substring(2);
        if (m.length() == 10 && m.startsWith("9")) m = "0" + m;
        return m;
    }

    // ---------- 📩 پشتیبانی (بله) ----------
    private void supportDialog() {
        try {
            final EditText et = new EditText(this);
            et.setHint("توضیح کوتاه (اختیاری)");
            final String crash = CrashCatcher.lastCrash(this);
            final String err = CrashCatcher.lastErr(this);
            boolean has = (crash != null && !crash.trim().isEmpty()) || (err != null && !err.trim().isEmpty());
            String msg = "گزارش مستقیم به ربات پشتیبانی در بله می‌رود و توسعه‌دهنده مطلع می‌شود.";
            if (has) msg += "\n\n🐞 خطاهای ثبت‌شدهٔ همین گوشی هم ضمیمه می‌شود.";
            new AlertDialog.Builder(this)
                    .setTitle("📩 پشتیبانی (بله)")
                    .setMessage(msg)
                    .setView(et)
                    .setPositiveButton("📤 ارسال", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            sendSupport(et.getText().toString().trim(), crash, err);
                        }
                    })
                    .setNegativeButton("انصراف", null).show();
        } catch (Exception ignored) { }
    }

    private void sendSupport(final String text, final String crash, final String err) {
        Toast.makeText(this, "⏳ در حال ارسال…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    String ver;
                    try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { ver = "?"; }
                    org.json.JSONObject j = new org.json.JSONObject();
                    j.put("app", "ساکن‌یار");
                    j.put("ver", ver);
                    j.put("android", android.os.Build.VERSION.RELEASE);
                    j.put("model", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                    j.put("text", text == null ? "" : text);
                    if (crash != null && !crash.trim().isEmpty()) j.put("crash", crash.length() > 2500 ? crash.substring(crash.length() - 2500) : crash);
                    if (err != null && !err.trim().isEmpty()) j.put("err", err.length() > 2000 ? err.substring(err.length() - 2000) : err);
                    String code = Hub.code(SettingsActivity.this);
                    ok = Hub.msg(code, "support", "app:saken", "SUPPORT", j.toString()).ok;
                } catch (Exception ignored) { }
                final boolean fok = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(SettingsActivity.this, fok
                                ? "✅ ارسال شد — توسعه‌دهنده در بله مطلع می‌شود 🙏"
                                : "⛔ ارسال نشد — اینترنت را چک کن و دوباره تلاش کن", Toast.LENGTH_LONG).show();
                        if (fok) {
                            try { CrashCatcher.clearCrash(SettingsActivity.this); CrashCatcher.clearErrs(SettingsActivity.this); } catch (Exception ignored) { }
                        }
                    }
                });
            }
        }, "support").start();
    }
}
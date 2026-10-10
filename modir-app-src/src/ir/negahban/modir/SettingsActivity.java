package ir.negahban.modir;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** تنظیمات مدیر یار v2.0: اتصال به هاب (ثبت ساختمان) + ایستگاه‌ها + پشتیبان ابری + به‌روزرسانی */
public class SettingsActivity extends Activity {

    EditText etBuilding, etStations, etCode, etMobile;

    LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { Cfg.fixLongs(this); } catch (Exception ignored) { }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(50));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setBackgroundColor(Color.WHITE);
        scroll.addView(box);
        setContentView(scroll);

        // 🐞 گزارش خطای آخر (اگر باشه)
        try {
            final String cr = CrashCatcher.lastCrash(this);
            if (cr != null && !cr.trim().isEmpty()) {
                LinearLayout cb = new LinearLayout(this);
                cb.setOrientation(LinearLayout.VERTICAL);
                android.graphics.drawable.GradientDrawable cbg = new android.graphics.drawable.GradientDrawable();
                cbg.setColor(0xFFFFEBEE);
                cbg.setCornerRadius(dp(10));
                cbg.setStroke(dp(1), 0xFFE53935);
                cb.setBackground(cbg);
                cb.setPadding(dp(12), dp(10), dp(12), dp(10));
                TextView ct = new TextView(this);
                ct.setText("🐞 خطای اجرای قبلی ثبت شد — برای دیدن و کپی لمس کنید");
                ct.setTextColor(0xFFB71C1C);
                ct.setTextSize(13);
                cb.addView(ct);
                cb.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("🐞 خطای قبلی")
                                .setMessage(cr.length() > 1600 ? "…" + cr.substring(cr.length() - 1600) : cr)
                                .setPositiveButton("📤 ارسال به پشتیبانی", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        sendSupport("", cr, CrashCatcher.lastErr(SettingsActivity.this));
                                    }
                                })
                                .setNeutralButton("📋 کپی", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        try {
                                            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                            cm.setPrimaryClip(android.content.ClipData.newPlainText("ngy-crash", cr));
                                            Toast.makeText(SettingsActivity.this, "کپی شد ✅", Toast.LENGTH_SHORT).show();
                                        } catch (Exception ignored) { }
                                    }
                                })
                                .setNegativeButton("بستن", null).show();
                    }
                });
                box.addView(cb);
            }
        } catch (Exception ignored) { }

        // نوار فعال‌سازی
        TextView lic = new TextView(this);
        int lst = License.status(this);
        lic.setText(lst == 0 ? "✅ فعال‌سازی کامل تا " + Scheduler.jalaliDate(License.expiry(this))
                : lst == 1 ? "🎁 دورهٔ آزمایشی — " + Scheduler.fa(String.valueOf(License.daysLeft(this))) + " روز"
                : "⛔ دورهٔ استفاده تمام شده");
        lic.setTextColor(Color.WHITE);
        lic.setTextSize(13);
        lic.setBackgroundColor(lst == 0 ? 0xFF12B5A5 : 0xFFE74C3C);
        lic.setPadding(dp(12), dp(6), dp(12), dp(6));
        box.addView(lic);
        mkBtn("🔑 فعال‌سازی برنامه / ورود کد").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, LicenseActivity.class)); }
        });

        title("⚙️ تنظیمات مدیر یار");

        section("🏢 ساختمان و اتصال");
        etBuilding = field("نام ساختمان", Cfg.building(this), null, InputType.TYPE_CLASS_TEXT);
        etMobile = field("شمارهٔ همراه مدیر (این شماره = کد ساختمان)", Cfg.p(this).getString("mgrMobile", ""), "مثلاً 09127285065", InputType.TYPE_CLASS_PHONE);
        etCode = field("کد ساختمان (به‌طور خودکار = شمارهٔ همراه مدیر)", Cfg.p(this).getString("bldgCode", ""), "09127285065", InputType.TYPE_CLASS_PHONE);
        mkBtn("🆕 ثبت ساختمان و دریافت کد").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { registerBuilding(); }
        });
        TextView codeNote = new TextView(this);
        codeNote.setText("کد ساختمان همان شمارهٔ همراه مدیر است و هیچ‌وقت تغییر نمیکند. این شماره را به گوشی نگهبانی (نگهبان‌یار) و اهالی (ساکن‌یار) بده. ثبتِ دوباره هم کد را عوض نمیکند.");
        codeNote.setTextSize(12);
        codeNote.setTextColor(0xFF607D8B);
        box.addView(codeNote);

        section("🏢 ایستگاه‌های گشت");
        etStations = field("ایستگاه‌ها (هر خط: شناسه|نام)", Cfg.stationsRaw(this), "1|لابی\n2|پارکینگ…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etStations.setMinLines(3);

        section("☁️ پشتیبان ابری");
        TextView bNote = new TextView(this);
        bNote.setText("پشتیبانِ رمزنگاری‌شدهٔ برنامه روی فضای ابری ذخیره می‌شود؛ ۱۰ نسخهٔ آخر نگه داشته می‌شود و هر ۲۴ ساعت خودکار هم ارسال می‌گردد. کلید رمز، کد ساختمان است.");
        bNote.setTextSize(12);
        bNote.setTextColor(0xFF607D8B);
        box.addView(bNote);
        mkBtnPrimary("☁️ ارسال پشتیبان ابری (همین حالا)").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cloudBackupNow(false); }
        });
        mkBtn("♻️ بازیابی آخرین پشتیبان ابری").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cloudRestoreAsk(); }
        });

        section("📩 پشتیبانی");
        mkBtn("📩 پشتیبانی (بله) — سوال یا گزارش خطا").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { supportDialog(); }
        });

        section("🔄 به‌روزرسانی");
        TextView vInfo = new TextView(this);
        vInfo.setText("نسخهٔ نصب‌شده: v" + Updater.localVersion(this) + " — به‌روزرسانی اجباری است");
        vInfo.setTextSize(12);
        vInfo.setTextColor(0xFF607D8B);
        box.addView(vInfo);
        mkBtn("🔄 بررسی نسخهٔ جدید و نصب").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { UpdaterUi.run(SettingsActivity.this, false); }
        });
        Button bat = mkBtn("🔋 معافیت از بهینه‌سازی باتری (برای دریافت خبرها پیشنهاد می‌شود)");
        bat.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) { toast("از تنظیمات باتری انجام بده"); }
            }
        });

        Button save = mkBtnPrimary("💾 ذخیره");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
    }

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

    /** ثبت ساختمان روی سرور و دریافت کد */
    private void registerBuilding() {
        final String name = etBuilding.getText().toString().trim();
        if (name.isEmpty()) { toast("اول نام ساختمان را بنویس"); return; }
        final String mobile = normMobile(etMobile.getText().toString().trim());
        if (mobile.length() != 11 || !mobile.startsWith("09")) { toast("شمارهٔ همراه مدیر را درست وارد کن (11 رقم)"); return; }
        toast("در حال ثبت…");
        new Thread(new Runnable() {
            @Override public void run() {
                final Cloud.Res r = Cloud.hubReg(name, mobile);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (r.ok && !r.payload.isEmpty()) {
                            etCode.setText(r.payload);
                            Cfg.set(SettingsActivity.this, "bldgCode", r.payload);
                            HubService.start(SettingsActivity.this);
                            toast("✅ کد ساختمان: " + Scheduler.fa(r.payload));
                            offerCloudRestore();
                        } else toast("⛔ " + (r.err.isEmpty() ? "ثبت نشد" : r.err));
                    }
                });
            }
        }).start();
    }

    private void save() {
        try {
            Cfg.set(this, "building", etBuilding.getText().toString().trim());
            Cfg.set(this, "stations", etStations.getText().toString());
            Cfg.set(this, "mgrMobile", normMobile(etMobile.getText().toString().trim()));
            String code = normMobile(etCode.getText().toString().trim());
            Cfg.set(this, "bldgCode", code);
            if (code.isEmpty()) HubService.stop(this); else HubService.start(this);
            toast("✅ ذخیره شد");
        } catch (Exception e) {
            toast("خطا در ذخیره");
        }
    }

    // ---------- پشتیبان ابری ----------

    /** نصب تازه؟ اگر پشتیبان ابریِ همین شماره موجود است، پیشنهاد بازیابی بده */
    private void offerCloudRestore() {
        new Thread(new Runnable() {
            @Override public void run() {
                final Cloud.Res r = Cloud.latest(SettingsActivity.this);
                if (!(r.ok && !r.payload.isEmpty())) return;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("☁️ پشتیبان قبلی پیدا شد")
                                .setMessage("برای این شمارهٔ مدیر، پشتیبان ابری ثبت شده است.\nهمهٔ اطلاعات (ساکنین، مهمان‌ها، بسته‌ها، رویدادها و تنظیمات) بازیابی شود؟")
                                .setPositiveButton("بله، بازیابی کن", new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(android.content.DialogInterface d, int w) { cloudRestoreAsk(); }
                                })
                                .setNegativeButton("فعلاً نه", null).show();
                    }
                });
            }
        }).start();
    }

    private void cloudBackupNow(final boolean auto) {
        final String code = Cfg.p(this).getString("bldgCode", "").trim();
        if (code.isEmpty()) {
            toast("اول کد ساختمان را ثبت/ذخیره کن");
            return;
        }
        if (!auto) toast("در حال ارسال…");
        new Thread(new Runnable() {
            @Override public void run() {
                String msg0 = "";
                try {
                    String blob = Cloud.encrypt(code, Backup.make(SettingsActivity.this));
                    Cloud.Res r = Cloud.save(SettingsActivity.this, blob);
                    if (r.ok) {
                        Cfg.p(SettingsActivity.this).edit().putLong("lastCloudAuto", PatrolStore.now()).apply();
                        msg0 = auto ? "☁️ پشتیبان خودکار ذخیره شد" : "✅ پشتیبان روی فضای ابری ذخیره شد";
                    } else msg0 = "⛔ " + r.err;
                } catch (Exception e) {
                    msg0 = "⛔ " + String.valueOf(e);
                }
                final String msg = msg0;
                runOnUiThread(new Runnable() {
                    @Override public void run() { Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show(); }
                });
            }
        }).start();
    }

    private void cloudRestoreAsk() {
        new AlertDialog.Builder(this)
                .setTitle("♻️ بازیابی پشتیبان ابری")
                .setMessage("تمام اطلاعات فعلی (تنظیمات، ساکنین، مهمان‌ها، بسته‌ها، رویدادها) با آخرین پشتیبان جایگزین می‌شود.\n\nمطمئنی؟")
                .setPositiveButton("بله، بازیابی کن", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { cloudRestore(); }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void cloudRestore() {
        final String code = Cfg.p(this).getString("bldgCode", "").trim();
        if (code.isEmpty()) {
            toast("اول کد ساختمان را ثبت/ذخیره کن");
            return;
        }
        toast("در حال دریافت آخرین پشتیبان…");
        new Thread(new Runnable() {
            @Override public void run() {
                String msg0 = "";
                try {
                    Cloud.Res r = Cloud.latest(SettingsActivity.this);
                    if (!r.ok) {
                        msg0 = "⛔ " + r.err;
                    } else {
                        String json;
                        try {
                            json = Cloud.decrypt(code, r.payload);
                        } catch (Exception e) {
                            json = null;
                            msg0 = "⛔ رمزگشایی نشد — کد ساختمان با موقعِ پشتیبان‌گیری فرق دارد";
                        }
                        if (json != null) {
                            String when = Scheduler.jalaliDate(r.ts * 1000L);
                            msg0 = Backup.restore(SettingsActivity.this, json) + "\n(پشتیبان: " + when + ")";
                        }
                    }
                } catch (Exception e) {
                    msg0 = "⛔ " + String.valueOf(e.getMessage());
                }
                final String msg = msg0;
                runOnUiThread(new Runnable() {
                    @Override public void run() { Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show(); }
                });
            }
        }).start();
    }

    // ---------- helpers ----------

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

    private Button styledBtn(String label, boolean primary) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setBackgroundResource(primary ? R.drawable.btn_primary : R.drawable.btn_dark);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        btn.setLayoutParams(lp);
        box.addView(btn);
        return btn;
    }

    private Button mkBtn(String l) { return styledBtn(l, false); }
    private Button mkBtnPrimary(String l) { return styledBtn(l, true); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

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
                    j.put("app", "مدیر یار");
                    j.put("ver", ver);
                    j.put("android", android.os.Build.VERSION.RELEASE);
                    j.put("model", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                    j.put("text", text == null ? "" : text);
                    if (crash != null && !crash.trim().isEmpty()) j.put("crash", crash.length() > 2500 ? crash.substring(crash.length() - 2500) : crash);
                    if (err != null && !err.trim().isEmpty()) j.put("err", err.length() > 2000 ? err.substring(err.length() - 2000) : err);
                    String code = Cfg.p(SettingsActivity.this).getString("bldgCode", "").trim();
                    ok = Cloud.hubMsg(code, "support", "app:modir", "SUPPORT", j.toString()).ok;
                } catch (Exception ignored) { }
                final boolean fok = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(SettingsActivity.this, fok
                                ? "✅ ارسال شد — توسعه‌دهنده در بله مطلع می‌شود 🙏"
                                : "⛔ ارسال نشد — اینترنت را چک کن و دوباره تلاش کن", Toast.LENGTH_LONG).show();
                        if (fok) {
                            try { CrashCatcher.clearCrash(SettingsActivity.this); CrashCatcher.clearErrs(SettingsActivity.this); } catch (Exception ignored) { }
                            recreate();
                        }
                    }
                });
            }
        }, "support").start();
    }
}
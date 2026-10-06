package ir.negahban.patrol;

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
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** صفحهٔ تنظیمات — فقط با PIN مدیر؛ شامل ویرایشگر ایستگاه‌ها (افزودن/حذف) */
public class SettingsActivity extends Activity {

    EditText etToken, etChat, etKey, etBuilding, etCount, etWindow, etNightStart, etNightEnd, etGap, etReport, etPin;
    CheckBox cbWake, cbBot;
    LinearLayout box, stBox;
    final ArrayList<EditText> stIds = new ArrayList<>();
    final ArrayList<EditText> stNames = new ArrayList<>();

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

        title("⚙️ تنظیمات سامانهٔ گشت");

        // نوار لایسنس
        TextView lic = new TextView(this);
        int lst = License.status(this);
        lic.setText(lst == 0 ? "✅ لایسنس فعال تا " + Scheduler.jalaliDate(License.expiry(this))
                : lst == 1 ? "🎁 دورهٔ آزمایشی — " + Scheduler.fa(String.valueOf(License.daysLeft(this))) + " روز"
                : "⛔ لایسنس منقضی");
        lic.setTextColor(Color.WHITE);
        lic.setTextSize(13);
        lic.setBackgroundColor(lst == 0 ? 0xFF12B5A5 : 0xFFE74C3C);
        lic.setPadding(dp(12), dp(6), dp(12), dp(6));
        box.addView(lic);
        mkBtn("🔑 مدیریت لایسنس / ورود کد").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, LicenseActivity.class)); }
        });

        section("🔗 اتصال به بله");
        etToken = field("توکن ربات بله (از @botfather)", Cfg.token(this), "123456:ABC-DEF…", InputType.TYPE_CLASS_TEXT);
        etChat = field("آیدی عددی مقصد (مدیر یا گروه)", Cfg.chatId(this) == 0 ? "" : String.valueOf(Cfg.chatId(this)), "مثلاً 123456789", InputType.TYPE_CLASS_NUMBER);
        mkBtn("🔌 تست توکن").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { testToken(); }
        });
        mkBtn("📥 دریافت chat_id از آخرین پیام به ربات").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fetchChatId(); }
        });
        mkBtn("✉️ ارسال پیام آزمایشی").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendTest(); }
        });

        section("👥 دفتر ساکنین");
        mkBtn("📥 دریافت دفتر ساکنین (فایل از مدیر یار)").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { importResidents(); }
        });

        section("🏢 ساختمان و ایستگاه‌ها");
        etBuilding = field("نام ساختمان", Cfg.building(this), null, InputType.TYPE_CLASS_TEXT);
        TextView lbl = new TextView(this);
        lbl.setText("ایستگاه‌ها — با ✕ حذف و با دکمهٔ زیر اضافه کن:");
        lbl.setPadding(0, dp(12), 0, dp(4));
        box.addView(lbl);

        stBox = new LinearLayout(this);
        stBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(stBox);
        String[][] cur = Cfg.stations(this);
        if (cur.length == 0) addStationRow("1", "");
        else for (String[] s : cur) addStationRow(s[0], s[1]);

        mkBtnPrimary("➕ افزودن ایستگاه").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (stIds.size() >= 20) { toast("حداکثر ۲۰ ایستگاه"); return; }
                int max = 0;
                for (EditText e : stIds) {
                    try { max = Math.max(max, Integer.parseInt(e.getText().toString().trim())); } catch (Exception ignored) {}
                }
                addStationRow(String.valueOf(max + 1), "");
            }
        });

        section("🕒 زمان‌بندی گشت");
        etCount = field("تعداد گشت در شب", String.valueOf(Cfg.patrolCount(this)), null, InputType.TYPE_CLASS_NUMBER);
        etWindow = field("طول هر پنجره (دقیقه)", String.valueOf(Cfg.windowMin(this)), null, InputType.TYPE_CLASS_NUMBER);
        etNightStart = field("شروع بازهٔ شبانه (HH:MM)", hhmm(Cfg.nightStart(this)), "00:00", InputType.TYPE_CLASS_TEXT);
        etNightEnd = field("پایان بازهٔ شبانه (HH:MM)", hhmm(Cfg.nightEnd(this)), "06:00", InputType.TYPE_CLASS_TEXT);
        etGap = field("حداقل فاصلهٔ بین دو اسکن (ثانیه)", String.valueOf(Cfg.minGapSec(this)), null, InputType.TYPE_CLASS_NUMBER);
        etReport = field("ساعت گزارش صبح (HH:MM)", hhmm(Cfg.reportMin(this)), "07:00", InputType.TYPE_CLASS_TEXT);
        cbWake = new CheckBox(this);
        cbWake.setText("چالش بیداری تصادفی فعال باشد");
        box.addView(cbWake);

        // ربات ساکنین روی «مدیر یار» اجرا می‌شود — اینجا فقط پیام می‌فرستیم
        TextView botNote = new TextView(this);
        botNote.setText("با فعال بودن، یک اعلان ثابت «ربات فعال» در گوشی دیده می‌شود و مصرف باتری کمی بیشتر است. تأیید عضویت‌ها: منوی ☰ → ✅ تأیید اعضا");
        botNote.setTextSize(12);
        botNote.setTextColor(0xFF607D8B);
        box.addView(botNote);

        section("🔐 امنیت و پلاک‌ها");
        etKey = field("کلید امضای پلاک‌ها (HMAC)", Cfg.keyHex(this), "۶۴ کاراکتر هگز — با ابزار چاپ پلاک یکی باشد", InputType.TYPE_CLASS_TEXT);
        etPin = field("PIN جدید مدیر (خالی = بدون تغییر)", "", null, InputType.TYPE_CLASS_NUMBER);

        mkBtn("🔑 تولید کلید جدید").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { newKeyFlow(); }
        });
        mkBtn("🏷️ امضای پلاک‌ها (برای چاپ)").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSigs(); }
        });

        section("🔋 پایداری روی گوشی");
        mkBtn("⏰ اجازهٔ هشدار دقیق (Android 12+)").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 31 && !Scheduler.exactOk(SettingsActivity.this)) {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) { toast("از تنظیمات سیستم: Programs → Alarms & reminders"); }
                } else toast("از قبل اجازه داده شده ✅");
            }
        });
        mkBtn("🔋 معافیت از بهینه‌سازی باتری (مهم!)").setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) { toast("از تنظیمات باتری، این اپ را مستثنا کن"); }
            }
        });

        Button save = mkBtnPrimary("💾 ذخیره (برنامهٔ امشب از نو قرعه می‌شود)");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
    }

    // ---------- دریافت دفتر ساکنین (فایل صادراتی مدیر یار) ----------

    private void importResidents() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, 95);
        } catch (Exception e) {
            toast("انتخاب‌گر فایل باز نشد: " + e);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 95 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                String json = Backup.readStream(is);
                is.close();
                org.json.JSONObject o = new org.json.JSONObject(json);
                org.json.JSONArray arr = o.optJSONArray("residents");
                if (arr == null) { toast("فایل دفتر ساکنین نیست"); return; }
                PatrolStore st = new PatrolStore(this);
                st.clearRes();
                int n = 0;
                for (int i2 = 0; i2 < arr.length(); i2++) {
                    org.json.JSONObject r = arr.optJSONObject(i2);
                    if (r == null) continue;
                    st.addRes(r.optString("name"), r.optString("mobile"), r.optString("plate"),
                            r.optString("car"), r.optString("parking"));
                    n++;
                }
                st.close();
                new AlertDialog.Builder(this)
                        .setTitle("دفتر ساکنین")
                        .setMessage(Scheduler.fa(String.valueOf(n)) + " ساکن از فایل مدیر خوانده و جایگزین شد ✅")
                        .setPositiveButton("باشه", null).show();
            } catch (Exception e) {
                toast("خواندن فایل ناموفق: " + e);
            }
        }
    }

    // ---------- جریان تولید کلید جدید: هشدار → PIN → تولید ----------

    private void newKeyFlow() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ هشدار مهم")
                .setMessage("با تولید کلید جدید، امضای همهٔ پلاک‌های نصب‌شده روی دیوار باطل می‌شود و QR آن‌ها دیگر پذیرفته نمی‌شود.\n\nیعنی باید همهٔ پلاک‌ها را از نو چاپ و نصب کنی.\n\nادامه می‌دهی؟")
                .setPositiveButton("ادامه", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { askPinForNewKey(); }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void askPinForNewKey() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("PIN مدیر");
        new AlertDialog.Builder(this)
                .setTitle("🔐 تأیید هویت")
                .setMessage("برای تولید کلید جدید، PIN مدیر را وارد کن")
                .setView(et)
                .setPositiveButton("تولید کلید", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (Cfg.pin(SettingsActivity.this).equals(et.getText().toString().trim())) {
                            etKey.setText(Crypto.randHex(32));
                            Toast.makeText(SettingsActivity.this, "✅ کلید جدید ساخته شد — حتماً ذخیره کن", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(SettingsActivity.this, "⛔ PIN اشتباه است — کلید تغییری نکرد", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    // ---------- ویرایشگر ایستگاه‌ها ----------

    private void addStationRow(String id, String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        final EditText eId = new EditText(this);
        eId.setInputType(InputType.TYPE_CLASS_NUMBER);
        eId.setText(id);
        eId.setGravity(android.view.Gravity.CENTER);
        eId.setTextSize(14);
        LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.WRAP_CONTENT);
        idLp.setMargins(0, 0, dp(6), 0);
        eId.setLayoutParams(idLp);

        final EditText eName = new EditText(this);
        eName.setInputType(InputType.TYPE_CLASS_TEXT);
        eName.setText(name);
        eName.setHint("نام ایستگاه (مثلاً لابی)");
        eName.setTextSize(14);
        eName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button del = new Button(this);
        del.setText("✕");
        del.setTextSize(14);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT);
        delLp.setMargins(dp(6), 0, 0, 0);
        del.setLayoutParams(delLp);
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (stIds.size() <= 1) { toast("حداقل یک ایستگاه لازم است"); return; }
                stBox.removeView((View) v.getParent());
                stIds.remove(eId);
                stNames.remove(eName);
                toast("ایستگاه حذف شد — برای اعمال، ذخیره کن");
            }
        });

        row.addView(eId);
        row.addView(eName);
        row.addView(del);
        stBox.addView(row);
        stIds.add(eId);
        stNames.add(eName);
    }

    /** خط‌های "id|name" از رابط کاربری؛ ایستگاه‌های بی‌نام رد می‌شوند */
    private String collectStations() {
        StringBuilder sb = new StringBuilder();
        int fallback = 1;
        for (int i = 0; i < stIds.size(); i++) {
            String name = stNames.get(i).getText().toString().trim();
            if (name.isEmpty()) continue;
            String id = stIds.get(i).getText().toString().trim();
            if (id.isEmpty()) id = String.valueOf(fallback);
            sb.append(id).append("|").append(name).append("\n");
            fallback++;
        }
        return sb.toString();
    }

    // ---------- عملیات ----------

    private void save() {
        try {
            String stations = collectStations();
            if (stations.isEmpty()) { toast("حداقل یک ایستگاه با نام لازم است"); return; }

            Cfg.set(this, "token", etToken.getText().toString().trim());
            String chat = etChat.getText().toString().trim();
            Cfg.set(this, "chatId", chat.isEmpty() ? 0L : Long.parseLong(chat));
            Cfg.set(this, "keyHex", etKey.getText().toString().trim().toLowerCase());
            Cfg.set(this, "building", etBuilding.getText().toString().trim());
            Cfg.set(this, "stations", stations);
            Cfg.set(this, "patrolCount", Math.max(1, Integer.parseInt(etCount.getText().toString().trim())));
            Cfg.set(this, "windowMin", Math.max(5, Integer.parseInt(etWindow.getText().toString().trim())));
            Cfg.set(this, "nightStart", hhmm2min(etNightStart.getText().toString()));
            Cfg.set(this, "nightEnd", hhmm2min(etNightEnd.getText().toString()));
            Cfg.set(this, "minGap", Math.max(0, Integer.parseInt(etGap.getText().toString().trim())));
            Cfg.set(this, "reportMin", hhmm2min(etReport.getText().toString()));
            Cfg.set(this, "wakeOn", cbWake.isChecked());
            String pin = etPin.getText().toString().trim();
            if (!pin.isEmpty()) Cfg.set(this, "pin", pin);
            Cfg.set(this, "tokenInvalid", false);

            Cfg.set(this, "plan", "{}");
            Scheduler.ensurePlan(this);
            toast("✅ ذخیره شد — برنامهٔ امشب قرعه کشیده شد");
        } catch (Exception e) {
            toast("خطا در ذخیره: قالب ساعت‌ها HH:MM باشد");
        }
    }

    private void testToken() {
        final String tk = etToken.getText().toString().trim();
        if (tk.isEmpty()) { toast("توکن را وارد کنید"); return; }
        toast("در حال بررسی…");
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok = Bale.tokenOk(tk);
                runOnUiThread(new Runnable() {
                    @Override public void run() { toast(ok ? "✅ توکن معتبر است" : "⛔ توکن نامعتبر یا اینترنت نیست"); }
                });
            }
        }).start();
    }

    private void fetchChatId() {
        final String tk = etToken.getText().toString().trim();
        if (tk.isEmpty()) { toast("اول توکن را وارد کنید"); return; }
        toast("اول مدیر باید یک‌بار به ربات پیام داده باشد…");
        new Thread(new Runnable() {
            @Override public void run() {
                final Long id = Bale.lastChatId(tk);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (id == null) toast("پیامی پیدا نشد — اول در بله به ربات پیام بدهید");
                        else { etChat.setText(String.valueOf(id)); toast("✅ chat_id: " + id); }
                    }
                });
            }
        }).start();
    }

    private void sendTest() {
        final String tk = etToken.getText().toString().trim();
        long ch;
        try { ch = Long.parseLong(etChat.getText().toString().trim()); } catch (Exception e) { ch = 0; }
        if (tk.isEmpty() || ch == 0) { toast("توکن و chat_id لازم است"); return; }
        final long fch = ch;
        toast("در حال ارسال…");
        new Thread(new Runnable() {
            @Override public void run() {
                Bale.Res r = Bale.send(tk, fch, "✅ پیام آزمایشی سامانهٔ گشت — اتصال درست است");
                Bale.checkClock(SettingsActivity.this, r);
                runOnUiThread(new Runnable() {
                    @Override public void run() { toast(r.ok ? "✅ ارسال شد" : "⛔ ارسال نشد (" + r.code + ")"); }
                });
            }
        }).start();
    }

    private void showSigs() {
        String key = etKey.getText().toString().trim().toLowerCase();
        StringBuilder sb = new StringBuilder("محتوای QR هر پلاک (برای ابزار چاپ):\n\n");
        for (String[] st : Cfg.stations(this)) {
            sb.append("ایستگاه ").append(st[1]).append(":\n")
              .append(Crypto.plaquePayload(key, st[0])).append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("امضای پلاک‌ها")
                .setMessage(sb.toString())
                .setPositiveButton("باشه", null)
                .show();
    }

    // ---------- پشتیبان‌گیری ----------

    private String pendingBackup = null;

    private void backupToBale() {
        long ch = Cfg.chatId(this);
        String tk = Cfg.token(this);
        if (tk.isEmpty() || ch == 0) { toast("اول توکن و chat_id را ذخیره کن"); return; }
        toast("در حال ساخت فایل پشتیبان…");
        final byte[] data;
        try {
            data = Backup.make(this).getBytes("UTF-8");
        } catch (Exception e) {
            toast("خطا در ساخت پشتیبان: " + e);
            return;
        }
        final long fch = ch;
        final String fname = "negahban-backup-" + PatrolStore.now() + ".json";
        new Thread(new Runnable() {
            @Override public void run() {
                Bale.Res r = Bale.sendDocument(Cfg.token(SettingsActivity.this), fch, data, fname,
                        "💾 پشتیبان سامانهٔ گشت — " + Scheduler.jalaliDate(PatrolStore.now()));
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        toast(r.ok ? "✅ فایل پشتیبان در بله ارسال شد" : "⛔ ارسال نشد (" + r.code + ") — از گزینهٔ ذخیرهٔ لوکال استفاده کن");
                    }
                });
            }
        }).start();
    }

    private void backupToLocal() {
        try {
            pendingBackup = Backup.make(this);
        } catch (Exception e) {
            toast("خطا در ساخت پشتیبان: " + e);
            return;
        }
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(android.content.Intent.EXTRA_TITLE, "negahban-backup-" + Scheduler.jalaliDate(PatrolStore.now()).replace(" ", "-") + ".json");
            startActivityForResult(i, 91);
        } catch (Exception e) {
            toast("انتخاب‌گر فایل باز نشد: " + e);
        }
    }

    private void restoreFlow() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, 92);
        } catch (Exception e) {
            toast("انتخاب‌گر فایل باز نشد: " + e);
        }
    }

    private void onLegacyActivityResult(int req, int res, android.content.Intent data) {
        if (data == null || data.getData() == null) return;
        if (req == 91 && res == RESULT_OK) { // ذخیرهٔ لوکال
            try {
                java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                Backup.writeStream(os, pendingBackup);
                os.close();
                toast("✅ فایل پشتیبان ذخیره شد");
            } catch (Exception e) {
                toast("خطا در ذخیره: " + e);
            }
        } else if (req == 92 && res == RESULT_OK) { // بازیابی
            try {
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                final String json = Backup.readStream(is);
                is.close();
                new AlertDialog.Builder(this)
                        .setTitle("بازیابی پشتیبان")
                        .setMessage("همهٔ تنظیمات، نگهبان‌ها، ساکنین و رویدادها با محتوای فایل جایگزین می‌شود. ادامه بدهم؟")
                        .setPositiveButton("بازیابی", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                try {
                                    String summary = Backup.restore(SettingsActivity.this, json);
                                    Scheduler.ensurePlan(SettingsActivity.this);
                                    new AlertDialog.Builder(SettingsActivity.this)
                                            .setTitle("نتیجه")
                                            .setMessage(summary + "\n\nبرای تازه‌شدن فیلدها، صفحه را ببند و دوباره باز کن.")
                                            .setPositiveButton("باشه", null).show();
                                } catch (Exception e) {
                                    toast("بازیابی ناموفق: " + e);
                                }
                            }
                        })
                        .setNegativeButton("انصراف", null).show();
            } catch (Exception e) {
                toast("خواندن فایل ناموفق: " + e);
            }
        }
    }

    // ---------- helpers رابط کاربری ----------

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

    private Button mkBtn(String label) { return styledBtn(label, false); }
    private Button mkBtnPrimary(String label) { return styledBtn(label, true); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private static String hhmm(int min) {
        return String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60);
    }

    private static int hhmm2min(String s) {
        s = s.trim();
        if (!s.contains(":")) return Integer.parseInt(s);
        String[] p = s.split(":");
        return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
    }
}

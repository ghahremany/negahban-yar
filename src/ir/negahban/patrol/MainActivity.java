package ir.negahban.patrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    TextView tvStatus, tvPlan, tvToday, drawerSub;
    android.widget.LinearLayout cardGuard;
    android.widget.ImageView imgGuard;
    TextView tvGuardName, tvGuardShift;
    View scrim;
    LinearLayout drawer;
    boolean drawerOpen = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashCatcher.install(this);
        // گیت لایسنس: بدون لایسنس معتبر/آزمایشی → صفحهٔ لایسنس
        if (License.status(this) == 2) {
            startActivity(new Intent(this, LicenseActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);
        tvPlan = findViewById(R.id.tvPlan);
        tvToday = findViewById(R.id.tvToday);
        scrim = findViewById(R.id.scrim);
        drawer = findViewById(R.id.drawer);
        drawerSub = findViewById(R.id.drawerSub);
        cardGuard = findViewById(R.id.cardGuard);
        imgGuard = findViewById(R.id.imgGuard);
        tvGuardName = findViewById(R.id.tvGuardName);
        tvGuardShift = findViewById(R.id.tvGuardShift);
        cardGuard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askPinGuard(); }
        });
        drawerSub.setText(Cfg.building(this) + " • نسخهٔ ۱٫۱ • بدون سرور");

        // اول از همه منو را بیرونِ صفحه بگذار تا موقع باز شدن انیمیشن داشته باشد
        drawer.post(new Runnable() {
            @Override public void run() { drawer.setTranslationX(drawer.getWidth()); }
        });

        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (drawerOpen) closeDrawer(); else openDrawer(); }
        });
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); }
        });
        findViewById(R.id.btnScan).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startScan(); }
        });

        findViewById(R.id.btnResidents).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // بدون PIN — برای استفادهٔ خود نگهبان
                startActivity(new Intent(MainActivity.this, ResidentsActivity.class));
            }
        });

        findViewById(R.id.btnGuests).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, GuestActivity.class)); }
        });

        findViewById(R.id.btnPackages).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, PackageActivity.class)); }
        });

        View.OnClickListener nav = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.dHome) {
                    closeDrawer();
                } else if (id == R.id.dScan) {
                    closeDrawer();
                    startScan();
                } else if (id == R.id.dSearchRes) {
                    closeDrawer();
                    startActivity(new Intent(MainActivity.this, ResidentsActivity.class));
                } else if (id == R.id.dGuests) {
                    closeDrawer();
                    startActivity(new Intent(MainActivity.this, GuestActivity.class));
                } else if (id == R.id.dPackages) {
                    closeDrawer();
                    startActivity(new Intent(MainActivity.this, PackageActivity.class));
                } else if (id == R.id.dManage) {
                    closeDrawer();
                    startActivity(new Intent(MainActivity.this, ManagementActivity.class));
                } else if (id == R.id.dGuards) {
                    closeDrawer();
                    askPinGuard();
                } else if (id == R.id.dResAdmin) {
                    closeDrawer();
                    askPinRes();
                } else if (id == R.id.dSettings) {
                    closeDrawer();
                    askPin();
                } else if (id == R.id.dPrint) {
                    closeDrawer();
                    askPinPrint();
                } else if (id == R.id.dLock) {
                    closeDrawer();
                    Toast.makeText(MainActivity.this, "برای برداشتن قفل: دکمهٔ بازگشت + مرور برنامه‌ها را همزمان نگه دارید", Toast.LENGTH_LONG).show();
                    startLockTask();
                } else if (id == R.id.dSync) {
                    closeDrawer();
                    Toast.makeText(MainActivity.this, "در حال ارسال…", Toast.LENGTH_SHORT).show();
                    new Thread(new Runnable() {
                        @Override public void run() { Sync.drain(MainActivity.this); }
                    }).start();
                } else if (id == R.id.dMembers) {
                    closeDrawer();
                    askPinMembers();
                } else if (id == R.id.dUpdate) {
                    closeDrawer();
                    UpdaterUi.run(MainActivity.this, false);
                } else if (id == R.id.dPage) {
                    closeDrawer();
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LicenseActivity.PAGE_URL))); }
                    catch (Exception e) { Toast.makeText(MainActivity.this, LicenseActivity.PAGE_URL, Toast.LENGTH_LONG).show(); }
                } else if (id == R.id.dDev) {
                    devDialog();
                }
            }
        };
        int[] items = {R.id.dHome, R.id.dScan, R.id.dSearchRes, R.id.dGuests, R.id.dPackages, R.id.dMembers, R.id.dManage, R.id.dGuards, R.id.dResAdmin, R.id.dSettings, R.id.dPrint, R.id.dLock, R.id.dSync, R.id.dUpdate, R.id.dPage, R.id.dDev};
        for (int it : items) findViewById(it).setOnClickListener(nav);
    }

    // ---------- منوی همبرگری ----------

    private void openDrawer() {
        drawerOpen = true;
        scrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0).setDuration(210).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) { drawer.setVisibility(View.GONE); return; }
        drawerOpen = false;
        scrim.setVisibility(View.GONE);
        drawer.animate().translationX(drawer.getWidth()).setDuration(190)
                .withEndAction(new Runnable() {
                    @Override public void run() { drawer.setVisibility(View.GONE); }
                }).start();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) closeDrawer();
        else super.onBackPressed();
    }

    // ---------- اسکن ----------

    private void startScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 7);
            return;
        }
        startActivity(new Intent(this, ScannerActivity.class));
    }

    // ---------- PIN برای تنظیمات و چاپ ----------

    private void askPin() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("PIN مدیر");
        new AlertDialog.Builder(this)
                .setTitle("⚙️ تنظیمات")
                .setMessage("PIN مدیر را وارد کنید")
                .setView(et)
                .setPositiveButton("ورود", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (Cfg.pin(MainActivity.this).equals(et.getText().toString().trim())) {
                            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        } else {
                            Toast.makeText(MainActivity.this, "PIN اشتباه است", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void askPinPrint() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("PIN مدیر");
        new AlertDialog.Builder(this)
                .setTitle("🖨️ چاپ پلاک‌ها")
                .setMessage("چاپ پلاک‌ها مخصوص مدیر است — PIN را وارد کنید")
                .setView(et)
                .setPositiveButton("چاپ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (!Cfg.pin(MainActivity.this).equals(et.getText().toString().trim())) {
                            Toast.makeText(MainActivity.this, "PIN اشتباه است", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (Cfg.stations(MainActivity.this).length == 0) {
                            Toast.makeText(MainActivity.this, "اول در تنظیمات ایستگاه‌ها را وارد کن", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(MainActivity.this, "دیالوگ چاپ اندروید باز می‌شود…", Toast.LENGTH_SHORT).show();
                        PlaquePrinter.print(MainActivity.this);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void askPinGuard() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("PIN مدیر");
        new AlertDialog.Builder(this)
                .setTitle("👤 نگهبان‌ها و شیفت")
                .setMessage("PIN مدیر را وارد کنید")
                .setView(et)
                .setPositiveButton("ورود", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (Cfg.pin(MainActivity.this).equals(et.getText().toString().trim())) {
                            startActivity(new Intent(MainActivity.this, GuardActivity.class));
                        } else {
                            Toast.makeText(MainActivity.this, "PIN اشتباه است", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void askPinRes() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("PIN مدیر");
        new AlertDialog.Builder(this)
                .setTitle("👥 مدیریت ساکنین")
                .setMessage("برای افزودن/ویرایش، PIN مدیر لازم است (جستجو بدون PIN از دکمهٔ صفحهٔ اصلی)")
                .setView(et)
                .setPositiveButton("ورود", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (Cfg.pin(MainActivity.this).equals(et.getText().toString().trim())) {
                            Intent i = new Intent(MainActivity.this, ResidentsActivity.class);
                            i.putExtra("admin", true);
                            startActivity(i);
                        } else {
                            Toast.makeText(MainActivity.this, "PIN اشتباه است", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void askPinMembers() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("PIN مدیر");
        new AlertDialog.Builder(this)
                .setTitle("✅ تأیید اعضا")
                .setMessage("PIN مدیر را وارد کنید")
                .setView(et)
                .setPositiveButton("ورود", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (Cfg.pin(MainActivity.this).equals(et.getText().toString().trim())) {
                            startActivity(new Intent(MainActivity.this, MembersActivity.class));
                        } else {
                            Toast.makeText(MainActivity.this, "PIN اشتباه است", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void devDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📞 ارتباط با توسعه‌دهنده")
                .setMessage("محمد جواد قهرمانی\nشمارهٔ تماس: " + Scheduler.fa("09127285065")
                        + "\n\nبرای سؤال، گزارش اشکال یا سفارش تغییرات تماس بگیرید.")
                .setPositiveButton("📞 تماس", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:09127285065")));
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "شماره: 09127285065", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    // ---------- تازه‌سازی صفحه ----------

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Scheduler.ensurePlan(this);
        } catch (Exception e) {
            Toast.makeText(this, "خطا در برنامه‌ریزی: " + e, Toast.LENGTH_LONG).show();
        }
        try {
            refresh();
        } catch (Exception e) {
            tvStatus.setText("⚠️ خطا در نمایش: " + e);
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try { Sync.drain(MainActivity.this); } catch (Exception ignored) {}
            }
        }).start();
        maybeShowCrash();
        maybeAutoUpdate();
        // اگر لایسنس وسط کار تمام شد
        if (License.status(this) == 2) {
            startActivity(new Intent(this, LicenseActivity.class));
            finish();
            return;
        }
        if (Cfg.botEnabled(this)) BotService.start(this);
    }

    /** چک خودکار: حداکثر یک‌بار در شبانه‌روز، بی‌صدا؛ فقط وقتی نسخهٔ جدید هست پنجره می‌آید */
    private void maybeAutoUpdate() {
        if (!Updater.shouldAutoCheck(this)) return;
        new Thread(new Runnable() {
            @Override public void run() {
                Updater.Res r = Updater.check(MainActivity.this);
                if (r.ok) {
                    Updater.markChecked(MainActivity.this);
                    if (r.updateAvailable) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                try { UpdaterUi.run(MainActivity.this, true); } catch (Exception ignored) {}
                            }
                        });
                    }
                }
                // خطا در حالت خودکار بی‌صدا
            }
        }).start();
    }

    /** اگر اجرای قبلی کرش کرده بود، متن خطا را نشان بده تا قابل گزارش‌گیری باشد */
    private void maybeShowCrash() {
        try {
            final String crash = CrashCatcher.lastCrash(this);
            if (crash == null) return;
            CrashCatcher.clearCrash(this);
            final String shortTxt = crash.length() > 900 ? crash.substring(0, 900) + " …" : crash;
            new AlertDialog.Builder(this)
                    .setTitle("⚠️ خطای اجرای قبلی")
                    .setMessage("اجرای قبلی برنامه با خطا متوقف شد:\n\n" + shortTxt
                            + "\n\nاین متن را اسکرین‌شات بگیر و برای توسعه‌دهنده بفرست (منوی ☰ → ارتباط با توسعه‌دهنده).")
                    .setPositiveButton("باشه", null)
                    .setNeutralButton("📧 ارسال به بله", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            PatrolStore st = new PatrolStore(MainActivity.this);
                            st.enq("🐞 گزارش خطای اپ:\n" + crash);
                            st.close();
                            Toast.makeText(MainActivity.this, "به صف ارسال اضافه شد", Toast.LENGTH_SHORT).show();
                            new Thread(new Runnable() {
                                @Override public void run() {
                                    try { Sync.drain(MainActivity.this); } catch (Exception ignored) {}
                                }
                            }).start();
                        }
                    })
                    .show();
        } catch (Exception ignored) {}
    }

    private void refresh() {
        int[] jt = Jalali.fromMillis(PatrolStore.now());
        tvToday.setText("🏢 " + Cfg.building(this) + "   •   امروز: "
                + Scheduler.fa(String.valueOf(jt[2])) + " " + Jalali.MONTHS[jt[1] - 1] + " " + Scheduler.fa(String.valueOf(jt[0])));

        // کارت نگهبانِ حاضر در شیفت
        JSONObject guardNow = Cfg.activeGuard(this);
        if (guardNow != null) {
            cardGuard.setVisibility(View.VISIBLE);
            tvGuardName.setText("👤 " + guardNow.optString("name", "—"));
            String shiftTxt = Cfg.guardShiftText(guardNow);
            String phoneTxt = Scheduler.fa(guardNow.optString("phone", ""));
            tvGuardShift.setText(shiftTxt + (phoneTxt.isEmpty() ? "" : "  |  " + phoneTxt));
            android.graphics.Bitmap gp = GuardActivity.loadPhoto(this, guardNow.optString("phone", ""), 128);
            if (gp != null) imgGuard.setImageBitmap(gp);
            else {
                imgGuard.setImageDrawable(null);
                imgGuard.setBackgroundResource(R.drawable.btn_dark);
            }
        } else {
            cardGuard.setVisibility(View.VISIBLE);
            tvGuardName.setText("👤 نگهبان امشب انتخاب نشده");
            tvGuardShift.setText("برای انتخاب: منوی ☰ → نگهبان‌ها و شیفت");
            imgGuard.setImageDrawable(null);
            imgGuard.setBackgroundResource(R.drawable.btn_dark);
        }

        boolean tok = !Cfg.token(this).isEmpty();
        boolean chat = Cfg.chatId(this) != 0;
        boolean inv = Cfg.p(this).getBoolean("tokenInvalid", false);
        long lastSync = Cfg.p(this).getLong("lastSync", 0);
        int pend = new PatrolStore(this).pendingCount();

        StringBuilder s = new StringBuilder();
        s.append(tok && chat && !inv ? "🟢 اتصال به بله آماده است" : "🔴 اتصال به بله تنظیم نشده (منوی ☰ → تنظیمات)");
        if (inv) s.append(" — توکن نامعتبر!");
        s.append("\n📥 صف ارسال: ").append(Scheduler.fa(String.valueOf(pend))).append(" پیام");
        if (lastSync > 0) s.append("\n🔄 آخرین سینک: ").append(Scheduler.fa(Scheduler.hm(lastSync)));
        if (!Scheduler.exactOk(this)) s.append("\n⏰ اجازهٔ هشدار دقیق داده نشده (در تنظیمات)");
        tvStatus.setText(s.toString());

        JSONObject plan = Scheduler.getPlan(this);
        StringBuilder p = new StringBuilder();
        p.append("🌙 شب ").append(Scheduler.fa(plan.optString("date", "—"))).append("\n");
        p.append("\n");
        JSONArray js = plan.optJSONArray("patrols");
        long now = PatrolStore.now();
        if (js != null) {
            PatrolStore st = new PatrolStore(this);
            for (int i = 0; i < js.length(); i++) {
                JSONObject w = js.optJSONObject(i);
                if (w == null) continue;
                long a = w.optLong("s"), b2 = w.optLong("e");
                int got = st.distinctStations(a, b2);
                int total = Cfg.stations(this).length;
                p.append("🟦 گشت ").append(Scheduler.fa(String.valueOf(i + 1))).append(": ")
                 .append(Scheduler.fa(Scheduler.hm(a))).append(" تا ").append(Scheduler.fa(Scheduler.hm(b2))).append("  (")
                 .append(Scheduler.fa(String.valueOf(got))).append("/").append(Scheduler.fa(String.valueOf(total))).append(")\n");
                if (now >= a && now < b2) {
                    p.append("   ➡️ ایستگاه بعدی: ").append(nextTarget(w, this)).append("\n");
                }
            }
            st.close();
        }
        if (plan.has("challenge")) {
            long ch = plan.optLong("challenge");
            boolean done = plan.optBoolean("challengeDone", false);
            p.append("\n⏰ چالش بیداری: ").append(done ? "✅ انجام شد" : "زمانش اعلام نمی‌شود 😉");
            if (ch > 0 && ch > now) p.append(" — تا ").append(Scheduler.fa(Scheduler.hm(ch)));
            p.append("\n");
        }
        tvPlan.setText(p.toString());
    }

    static String nextTarget(JSONObject w, android.content.Context c) {
        PatrolStore st = new PatrolStore(c);
        JSONArray order = w.optJSONArray("order");
        try {
            if (order != null) {
                for (int k = 0; k < order.length(); k++) {
                    int id = order.optInt(k, -1);
                    if (id >= 0 && !scanned(st, w, id)) return Cfg.stationName(c, id);
                }
            }
        } finally { st.close(); }
        return "همه ثبت شد ✅";
    }

    private static boolean scanned(PatrolStore st, JSONObject w, int id) {
        java.util.ArrayList<long[]> scans = st.scansBetween(w.optLong("s"), w.optLong("e"));
        for (long[] sc : scans) if (sc[0] == id) return true;
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == 7 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, ScannerActivity.class));
        }
    }
}

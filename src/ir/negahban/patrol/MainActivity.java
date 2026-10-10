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

    /** سرویس وقتی این true است اعلان سیستمی نمی‌دهد (اپ خودش صدا می‌زند) */
    public static volatile boolean uiVisible = false;
    private final android.os.Handler notifHandler = new android.os.Handler();
    private Runnable notifTicker;
    private boolean chimed = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { Cfg.fixLongs(this); } catch (Exception ignored) { }
        CrashCatcher.install(this);
        // گیت ۱: به‌روزرسانی اجباری
        if (Updater.forceNeeded(this)) {
            startActivity(new Intent(this, ForceUpdateActivity.class));
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
        drawerSub.setText(Cfg.building(this) + " • نسخهٔ ۲٫۰");

        // 🔔 زنگولهٔ اعلان‌ها + نشان تعداد ندیده‌ها
        android.widget.FrameLayout bellBox = findViewById(R.id.bellBox);
        final android.widget.TextView bellBadge = findViewById(R.id.bellBadge);
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setColor(0xFFE53935);
        badgeBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bellBadge.setBackground(badgeBg);
        bellBox.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, NotificationsActivity.class)); }
        });
        updateBell();

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
                } else if (id == R.id.dRoofKey) {
                    closeDrawer();
                    startActivity(new Intent(MainActivity.this, ServiceDeskActivity.class)
                            .putExtra("kind", "ROOF").putExtra("emoji", "🔑").putExtra("title", "کلید پشت‌بام"));
                } else if (id == R.id.dElev) {
                    closeDrawer();
                    startActivity(new Intent(MainActivity.this, ServiceDeskActivity.class)
                            .putExtra("kind", "ELEV").putExtra("emoji", "🛗").putExtra("title", "آسانسور"));
                } else if (id == R.id.dMove) {
                    closeDrawer();
                    startActivity(new Intent(MainActivity.this, ServiceDeskActivity.class)
                            .putExtra("kind", "MOVE").putExtra("emoji", "🚚").putExtra("title", "اسباب‌کشی"));
                } else if (id == R.id.dGuards) {
                    closeDrawer();
                    askPinGuard();
                } else if (id == R.id.dSettings) {
                    closeDrawer();
                    askPin();
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
                } else if (id == R.id.dUpdate) {
                    closeDrawer();
                    UpdaterUi.run(MainActivity.this, false);
                } else if (id == R.id.dPage) {
                    closeDrawer();
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PushFeed.PAGE_URL))); }
                    catch (Exception e) { Toast.makeText(MainActivity.this, PushFeed.PAGE_URL, Toast.LENGTH_LONG).show(); }
                } else if (id == R.id.dDev) {
                    devDialog();
                }
            }
        };
        int[] items = {R.id.dHome, R.id.dScan, R.id.dSearchRes, R.id.dGuests, R.id.dPackages, R.id.dRoofKey, R.id.dElev, R.id.dMove, R.id.dGuards, R.id.dSettings, R.id.dLock, R.id.dSync, R.id.dUpdate, R.id.dPage, R.id.dDev};
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
        if (Updater.forceNeeded(this)) {
            startActivity(new Intent(this, ForceUpdateActivity.class));
            finish();
            return;
        }

        // 🔔 چک اعلان‌ها + تیکر هر ۲۰ ثانیه
        uiVisible = true;
        notifCheck();
        updateBell();
        startNotify();
        if (notifTicker == null) {
            notifTicker = new Runnable() {
                @Override public void run() { notifCheck(); updateBell(); notifHandler.postDelayed(this, 20000); }
            };
        }
        notifHandler.postDelayed(notifTicker, 20000);
    }

    /** اطلاع تأیید کلید پشت‌بام از هاب: به نگهبان گفته میشود کلید را تحویل بدهد */
    @Override
    protected void onPause() {
        super.onPause();
        uiVisible = false;
        notifHandler.removeCallbacks(notifTicker);
    }

    void notifCheck() {
        final String code = Hub.code(this);
        if (code.isEmpty()) return;
        long after = Cfg.p(this).getLong("hubAfter", 0);
        java.util.ArrayList<Hub.MsgItem> items;
        try {
            items = Hub.msgs(code, "negahban", after);
        } catch (Exception e) { return; }
        if (items.isEmpty()) return;
        final StringBuilder sb = new StringBuilder();
        final StringBuilder sb2 = new StringBuilder();
        final StringBuilder sb3 = new StringBuilder();
        final boolean[] hasNew = {false};
        PatrolStore st = new PatrolStore(this);
        try {
            for (Hub.MsgItem it : items) {
                String txt = null;
                try {
                    org.json.JSONObject j = new org.json.JSONObject(it.payload);
                    if ("ROOF_KEY_OK".equals(it.kind)) {
                        txt = "🔑 کلید پشت‌بام «" + j.optString("name", "ساکن") + "» (واحد " + j.optString("unit", "—") + ") تأیید شد — تحویل داده شود";
                        sb.append("• ").append(j.optString("name", "ساکن"))
                          .append(" (واحد ").append(j.optString("unit", "—")).append(")\n");
                    } else if ("MOVE_OK".equals(it.kind)) {
                        txt = "🚚 اسباب‌کشی «" + j.optString("name", "ساکن") + "» (واحد " + j.optString("unit", "—") + ") توسط مدیر تأیید شد — هماهنگی و راه باز";
                        sb3.append("• 🚚 ").append(j.optString("name", "ساکن"))
                          .append(" (واحد ").append(j.optString("unit", "—")).append(")\n");
                    } else if ("ELEV_OK".equals(it.kind)) {
                        txt = "🛗 استفاده از آسانسور توسط «" + j.optString("name", "ساکن") + "» (واحد " + j.optString("unit", "—") + ") تأیید شد";
                        sb3.append("• 🛗 ").append(j.optString("name", "ساکن"))
                          .append(" (واحد ").append(j.optString("unit", "—")).append(")\n");
                    } else if ("GU_OK".equals(it.kind) || "GU_NO".equals(it.kind)) {
                        boolean ok = "GU_OK".equals(it.kind);
                        String by = "modir".equals(j.optString("by", "")) ? " توسط مدیر" : "";
                        txt = (ok ? "✅ ورود مهمان «" : "⛔ درخواست مهمان «") + j.optString("guest", "") + "»" + (ok ? " تأیید شد" : " رد شد") + by;
                        if (!"guard".equals(j.optString("by", "")))
                            sb2.append("• ").append(j.optString("guest", "مهمان"))
                              .append(" — ").append(ok ? "تأیید شد ✅" : "رد شد ⛔").append("\n");
                    } else if ("NEWS".equals(it.kind)) {
                        txt = "📢 " + j.optString("title", "اطلاعیه") + ": " + j.optString("body", "");
                    } else if (!"GU_GUEST_REQ".equals(it.kind)) {
                        txt = "💬 پیام جدید از " + it.src;
                    }
                } catch (Exception e) {
                    txt = "💬 پیام جدید از " + it.src;
                }
                if (txt != null && st.addNotif(it.id, it.ts, it.kind, txt)) hasNew[0] = true;
            }
        } finally { st.close(); }
        if (Hub.sLastId > after)
            Cfg.p(this).edit().putLong("hubAfter", Hub.sLastId).apply();
        final boolean fNew = hasNew[0];
        runOnUiThread(new Runnable() {
            @Override public void run() {
                updateBell();
                if (fNew) { chime(); }
                if (sb.length() > 0) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("🔑 تحویل کلید پشت‌بام")
                            .setMessage("مدیر تأیید کرده — کلید پشت‌بام تحویل داده شود:\n\n" + sb)
                            .setPositiveButton("ثبت شد", null)
                            .show();
                }
                if (sb2.length() > 0) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("🚶 وضعیت مهمانِ ساکن")
                            .setMessage("نتیجهٔ درخواست‌های مهمان:\n\n" + sb2 + "\nورود/خروج را در بخش مهمان‌ها ثبت کن.")
                            .setPositiveButton("باشه", null)
                            .show();
                }
                if (sb3.length() > 0) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("🚚🛗 درخواست تأییدشدهٔ مدیر")
                            .setMessage("این موارد توسط مدیر تأیید شده:\n\n" + sb3 + "\nهماهنگی لازم انجام شود.")
                            .setPositiveButton("باشه", null)
                            .show();
                }
            }
        });
    }

    /** شروع سرویس پایش بلادرنگ + گرفتن اجازهٔ اعلان (اندروید ۱۳ به بالا) */
    void startNotify() {
        uiVisible = true;
        try {
            Intent i = new Intent(this, NotifyService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) { }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                       != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
            }
        } catch (Exception ignored) { }
    }

    /** 🔔 صدای کوتاه هنگام پیام تازه */
    void chime() {
        try {
            android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 500);
            new Thread(new Runnable() {
                @Override public void run() {
                    try { Thread.sleep(900); } catch (Exception ignored) { }
                    try { tg.release(); } catch (Exception ignored) { }
                }
            }).start();
        } catch (Exception ignored) { }
    }

    /** نشان تعداد ندیده‌ها روی زنگوله */
    void updateBell() {
        final android.widget.TextView bellBadge = findViewById(R.id.bellBadge);
        if (bellBadge == null) return;
        new Thread(new Runnable() {
            @Override public void run() {
                PatrolStore st = new PatrolStore(MainActivity.this);
                final int n;
                try { n = st.unseenNotifs(); } finally { st.close(); }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (n > 0) {
                            bellBadge.setVisibility(View.VISIBLE);
                            bellBadge.setText(Scheduler.fa(String.valueOf(Math.min(n, 99))));
                        } else {
                            bellBadge.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }).start();
    }

    /** چک خودکار: حداکثر یک‌بار در شبانه‌روز، بی‌صدا؛ فقط وقتی نسخهٔ جدید هست پنجره می‌آید */
    private void maybeAutoUpdate() {
        // هر باز شدن چک شود — مثل ساکن‌یار (بدون بازهٔ ۲۴ ساعته)
        new Thread(new Runnable() {
            @Override public void run() {
                Updater.Res r = Updater.check(MainActivity.this);
                if (r.ok) {
                    Updater.markChecked(MainActivity.this);
                    Updater.setForce(MainActivity.this, r.updateAvailable);
                    if (r.updateAvailable) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                try {
                                    startActivity(new Intent(MainActivity.this, ForceUpdateActivity.class));
                                    finish();
                                } catch (Exception ignored) {}
                            }
                        });
                        return;
                    }
                }
                // پوش نوتیفیکیشن سازنده
                final PushFeed.Item p = PushFeed.fetchUnseen(MainActivity.this, "negahban");
                if (p != null) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { try { PushFeed.show(MainActivity.this, p); } catch (Exception ignored) {} }
                    });
                }
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

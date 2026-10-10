package ir.negahban.modir;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

/** مدیر یار v2.0 — داشبورد یک‌نگاهی ساختمان + منوی ☰ */
public class MainActivity extends Activity {

    LinearLayout grid, drawer;
    View scrim;
    TextView tvPush, tvToday, tvFoot, drawerSub;

    // دادهٔ ابری برای داشبورد
    ArrayList<JSONObject> cloudCharges = new ArrayList<JSONObject>();
    ArrayList<JSONObject> cloudRoster = new ArrayList<JSONObject>();

    /** سرویس وقتی این true است اعلان سیستمی نمی‌دهد (اپ خودش نشان می‌دهد) */
    public static volatile boolean uiVisible = false;
    private final android.os.Handler bellHandler = new android.os.Handler();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { Cfg.fixLongs(this); } catch (Exception ignored) { }
        CrashCatcher.install(this);
        if (Updater.forceNeeded(this)) {
            startActivity(new Intent(this, ForceUpdateActivity.class));
            finish();
            return;
        }
        if (License.status(this) == 2) {
            startActivity(new Intent(this, LicenseActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        showLastCrash();
        grid = findViewById(R.id.grid);
        tvPush = findViewById(R.id.tvPush);
        tvToday = findViewById(R.id.tvToday);
        tvFoot = findViewById(R.id.tvFoot);
        scrim = findViewById(R.id.scrim);
        drawer = findViewById(R.id.drawer);
        drawerSub = findViewById(R.id.drawerSub);
        // 🔔 زنگولهٔ اعلان‌ها — کاملاً محافظت‌شده؛ هیچ خطایی نباید بازشدن اپ را خراب کند
        try {
            android.widget.FrameLayout bellBox = findViewById(R.id.bellBox);
            final android.widget.TextView bellBadge = findViewById(R.id.bellBadge);
            if (bellBox != null && bellBadge != null) {
                android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
                badgeBg.setColor(0xFFE53935);
                badgeBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                bellBadge.setBackground(badgeBg);
                bellBox.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, NotificationsActivity.class)); }
                });
            }
        } catch (Throwable ignored) { }
        try {
            uiVisible = true;
            updateBell();
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                       != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
            }
            final Runnable bellTick = new Runnable() {
                @Override public void run() {
                    if (isDestroyed() || isFinishing()) return;
                    try { updateBell(); } catch (Throwable ignored) { }
                    bellHandler.postDelayed(this, 10000);
                }
            };
            bellHandler.postDelayed(bellTick, 10000);
        } catch (Throwable ignored) { }

        findViewById(R.id.btnSetup).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDrawer(); }
        });
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); }
        });
        drawer.post(new Runnable() {
            @Override public void run() { drawer.setTranslationX(drawer.getWidth()); }
        });
        bindDrawer();
    }

    @Override
    protected void onResume() {
        uiVisible = true;
        super.onResume();
        if (License.status(this) == 2) {
            startActivity(new Intent(this, LicenseActivity.class));
            finish();
            return;
        }
        if (Updater.forceNeeded(this)) {
            startActivity(new Intent(this, ForceUpdateActivity.class));
            finish();
            return;
        }
        refresh();
        loadCloudData();
        try { backgroundChecks(); } catch (Throwable ignored) { }
    }

    // ================= منوی ☰ =================

    /** اتصال آیتم‌های منو (استاتیک در XML) */
    void bindDrawer() {
        int[] ids = {R.id.dDash, R.id.dFund, R.id.dCal, R.id.dInbox, R.id.dRes, R.id.dGuard,
                R.id.dPrint, R.id.dSettings, R.id.dLic, R.id.dUpdate, R.id.dBackup, R.id.dPage};
        for (int i = 0; i < ids.length; i++) {
            final int idx = i;
            findViewById(ids[i]).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { menu(idx); }
            });
        }
    }

    void menu(int idx) {
        closeDrawer();
        switch (idx) {
            case 0: refresh(); break;
            case 1: startActivity(new Intent(this, ChargesActivity.class)); break;
            case 2: startActivity(new Intent(this, CalendarActivity.class)); break;
            case 3: startActivity(new Intent(this, InboxActivity.class)); break;
            case 4: startActivity(new Intent(this, ResidentsActivity.class)); break;
            case 5: startActivity(new Intent(this, GuardActivity.class)); break;
            case 6:
                if (Cfg.stations(this).length == 0) { toast("اول در تنظیمات ایستگاه‌ها را وارد کن"); break; }
                PlaquePrinter.print(this);
                break;
            case 7: startActivity(new Intent(this, SettingsActivity.class)); break;
            case 8: startActivity(new Intent(this, LicenseActivity.class)); break;
            case 9: UpdaterUi.run(this, false); break;
            case 10: cloudBackupNow(); break;
            case 11:
                try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://negahbanyar.xo.je/"))); }
                catch (Exception ignored) { }
                break;
        }
    }

    void openDrawer() {
        String code = Cfg.p(this).getString("bldgCode", "").trim();
        drawerSub.setText(Cfg.building(this) + (code.isEmpty() ? "" : " • کد " + Scheduler.fa(code)));
        scrim.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0).setDuration(220).start();
    }

    void closeDrawer() {
        scrim.setVisibility(View.GONE);
        drawer.animate().translationX(drawer.getWidth()).setDuration(200).start();
    }

    // ================= داشبورد =================

    void refresh() {
        int[] jt = Jalali.fromMillis(PatrolStore.now());
        tvToday.setText("🏢 " + Cfg.building(this) + "   •   امروز: "
                + Scheduler.fa(String.valueOf(jt[2])) + " " + Jalali.MONTHS[jt[1] - 1] + " " + Scheduler.fa(String.valueOf(jt[0])));

        int guestsIn = 0;
        ArrayList<String[]> inside = new ArrayList<String[]>();
        int pkgs = 0, unseen = 0;
        String lastGuardMsg = "";
        long lastGuardTs = 0;
        PatrolStore st = new PatrolStore(this);
        try {
            guestsIn = st.guestsInsideCount();
            inside = st.guestsInside(4);
            pkgs = st.allPackages(500).size();
            unseen = st.unseenMsgCount();
            for (String[] m : st.msgs(60)) {
                if (m[2] != null && m[2].contains("negahban")) {
                    lastGuardMsg = m[4];
                    lastGuardTs = Long.parseLong(m[1]);
                    break;
                }
            }
        } finally {
            st.close();
        }

        grid.removeAllViews();

        // ۱) صندوق
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF0F3D56);
        gb.setCornerRadius(dp(18));
        LinearLayout fund = new LinearLayout(this);
        fund.setOrientation(LinearLayout.VERTICAL);
        fund.setBackground(gb);
        fund.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView f1 = new TextView(this);
        f1.setText("💰 موجودی صندوق ساختمان");
        f1.setTextColor(0xB3FFFFFF);
        f1.setTextSize(13);
        fund.addView(f1);
        TextView f2 = new TextView(this);
        f2.setText(money(sumOk()) + " تومان");
        f2.setTextColor(Color.WHITE);
        f2.setTextSize(28);
        f2.setTypeface(null, Typeface.BOLD);
        f2.setPadding(0, dp(6), 0, dp(4));
        fund.addView(f2);
        TextView f3 = new TextView(this);
        f3.setText(fundSub());
        f3.setTextColor(0xFF9FE8DE);
        f3.setTextSize(13);
        fund.addView(f3);
        fund.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, ChargesActivity.class)); }
        });
        grid.addView(fund);

        // ۲) پرداخت‌کننده‌ها / بدهکارهای این ماه
        LinearLayout two = new LinearLayout(this);
        two.setOrientation(LinearLayout.HORIZONTAL);
        two.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        two.setPadding(dp(5), dp(10), dp(5), 0);
        int[] pm = paidDebtThisMonth();
        mini(two, "✅ شارژ دادند (این ماه)", Scheduler.fa(String.valueOf(pm[0])) + " نفر",
                0xFFE8F5E9, new Runnable() {
                    @Override public void run() { startActivity(new Intent(MainActivity.this, ChargesActivity.class)); }
                });
        mini(two, "⛔ بدهکار (این ماه)", Scheduler.fa(String.valueOf(pm[1])) + " نفر",
                pm[1] == 0 ? 0xFFF1F8F5 : 0xFFFFEBEE, new Runnable() {
                    @Override public void run() { startActivity(new Intent(MainActivity.this, ChargesActivity.class)); }
                });
        grid.addView(two);

        // ۳) گشت و شیفت
        String guardLine;
        if (lastGuardTs == 0) {
            guardLine = "امروز خبری از نگهبان نیست";
        } else {
            long min = (PatrolStore.now() - lastGuardTs) / 60000L;
            guardLine = min < 90
                    ? "آخرین خبر: " + Scheduler.fa(String.valueOf(min)) + " دقیقه پیش ✓"
                    : "آخرین خبر: " + Scheduler.fa(String.valueOf(min / 60)) + " ساعت پیش";
            if (lastGuardMsg.contains("⚠") || lastGuardMsg.contains("پرچم")) guardLine += " • پرچم در گزارش!";
        }
        String head = lastGuardMsg;
        int cut = head.indexOf('\n');
        if (cut > 0) head = head.substring(0, cut);
        if (head.length() > 70) head = head.substring(0, 70) + "…";
        final String sub3 = guardLine + (lastGuardTs == 0 ? "" : "\n" + head);
        card("🛡 گشت و شیفت", sub3, lastGuardTs == 0 ? 0xFFFFFBEB : 0xFFFFFFFF, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, InboxActivity.class)); }
        });

        card("📅 تاریخچهٔ رویداد", "تقویم رنگی درخواست‌ها و گشت‌ها — مرور ماه‌های قبل", 0xFFFFFFFF, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, CalendarActivity.class)); }
        });

        // ۴) مهمان‌های داخل
        StringBuilder gnames = new StringBuilder();
        for (int i = 0; i < inside.size() && i < 3; i++) {
            if (gnames.length() > 0) gnames.append("، ");
            gnames.append(inside.get(i)[2]);
        }
        if (guestsIn > 3) gnames.append(" و …");
        card("🚶 مهمان داخل ساختمان", guestsIn == 0 ? "مهمانی داخل نیست"
                        : Scheduler.fa(String.valueOf(guestsIn)) + " نفر: " + gnames,
                guestsIn == 0 ? 0xFFFFFFFF : 0xFFFFF8E1, new Runnable() {
                    @Override public void run() { guestsDialog(); }
                });

        // ۵) بسته‌ها + خبر تازه
        LinearLayout two2 = new LinearLayout(this);
        two2.setOrientation(LinearLayout.HORIZONTAL);
        two2.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        two2.setPadding(dp(5), dp(10), dp(5), 0);
        final int fPkgs = pkgs, fUnseen = unseen;
        mini(two2, "📦 بسته در انتظار", Scheduler.fa(String.valueOf(pkgs)), 0xFFFFFFFF, new Runnable() {
            @Override public void run() { toast("جزئیات بسته‌ها در صندوق پیام‌ها و گزارش نگهبان است"); }
        });
        mini(two2, "📥 خبر تازه", Scheduler.fa(String.valueOf(unseen)),
                unseen == 0 ? 0xFFFFFFFF : 0xFFE3F2FD, new Runnable() {
                    @Override public void run() { startActivity(new Intent(MainActivity.this, InboxActivity.class)); }
                });
        grid.addView(two2);

        int pend = new PatrolStore(this).pendingCount();
        tvFoot.setText("🧭 سوئیت «یار» • مدیر یار v" + Scheduler.fa(Updater.localVersion(this))
                + (pend > 0 ? "\n📥 صف ارسال: " + Scheduler.fa(String.valueOf(pend)) + " پیام" : ""));
    }

    void guestsDialog() {
        PatrolStore st = new PatrolStore(this);
        ArrayList<String[]> inside;
        try { inside = st.guestsInside(30); } finally { st.close(); }
        if (inside.isEmpty()) { toast("مهمانی داخل نیست"); return; }
        StringBuilder sb = new StringBuilder();
        for (String[] g : inside) {
            sb.append("• ").append(g[2]);
            if (!g[5].isEmpty()) sb.append(" (کدملی ").append(Scheduler.fa(g[5])).append(")");
            sb.append(" — ورود: ").append(clock(Long.parseLong(g[1]))).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("🚶 مهمان‌های داخل ساختمان")
                .setMessage(sb.toString())
                .setPositiveButton("بستن", null)
                .show();
    }

    void backgroundChecks() {
        new Thread(new Runnable() {
            @Override public void run() {
                // 🔄 چک آپدیت در هر باز شدن — مثل ساکن‌یار: اگر نسخهٔ جدید بود، گیت اجباری باز می‌شود
                try {
                    Updater.Res ur = Updater.check(MainActivity.this);
                    if (ur.ok) {
                        Updater.markChecked(MainActivity.this);
                        Updater.setForce(MainActivity.this, ur.updateAvailable);
                        if (ur.updateAvailable) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    try {
                                        startActivity(new Intent(MainActivity.this, ForceUpdateActivity.class));
                                        finish();
                                    } catch (Exception ignored) { }
                                }
                            });
                            return;
                        }
                    }
                } catch (Exception ignored) { }
                try {
                    final PushFeed.Item it = PushFeed.fetchUnseen(MainActivity.this, "modir");
                    if (it != null) runOnUiThread(new Runnable() {
                        @Override public void run() {
                            tvPush.setVisibility(View.VISIBLE);
                            tvPush.setText(it.title + "\n" + it.body);
                        }
                    });
                } catch (Exception ignored) { }
            }
        }).start();
        Cloud.autoIfDue(this);
        HubService.start(this);
    }

    void cloudBackupNow() {
        if (Cfg.p(this).getString("bldgCode", "").trim().isEmpty()) {
            toast("اول کد ساختمان را در تنظیمات ثبت کن");
            return;
        }
        toast("در حال ارسال پشتیبان…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String blob = Cloud.encrypt(Cfg.token(MainActivity.this), Backup.make(MainActivity.this));
                    final Cloud.Res r = Cloud.save(MainActivity.this, blob);
                    if (r.ok) Cfg.p(MainActivity.this).edit().putLong("lastCloudAuto", PatrolStore.now()).apply();
                    runOnUiThread(new Runnable() {
                        @Override public void run() { toast(r.ok ? "✅ پشتیبان ذخیره شد" : "⛔ " + r.err); }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() { @Override public void run() { toast("⛔ خطا در پشتیبان"); }});
                }
            }
        }).start();
    }

    // ---------- دادهٔ ابری ----------

    void loadCloudData() {
        final String code = Cfg.p(this).getString("bldgCode", "").trim();
        if (code.isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                ArrayList<JSONObject> ch = new ArrayList<JSONObject>();
                Cloud.Res r1 = Cloud.hubDataGet(code, "charges");
                if (r1.ok) {
                    try {
                        JSONArray a = new JSONArray(r1.payload);
                        for (int i = 0; i < a.length(); i++) ch.add(a.getJSONObject(i));
                    } catch (Exception ignored) { }
                }
                ArrayList<JSONObject> ro = Cloud.hubResList(code, null);
                cloudCharges = ch;
                cloudRoster = ro;
                runOnUiThread(new Runnable() { @Override public void run() { refresh(); } });
            }
        }).start();
    }

    long sumOk() {
        long s = 0;
        for (JSONObject j : cloudCharges) if ("OK".equals(j.optString("status"))) s += j.optLong("amount", 0);
        return s;
    }

    String fundSub() {
        int ok = 0, pend = 0;
        for (JSONObject j : cloudCharges) {
            if ("OK".equals(j.optString("status"))) ok++; else pend++;
        }
        if (cloudCharges.isEmpty()) return "برای دادهٔ دقیق، اینترنت را وصل کن";
        return Scheduler.fa(String.valueOf(ok)) + " پرداخت تأییدشده"
                + (pend > 0 ? " • " + Scheduler.fa(String.valueOf(pend)) + " در انتظار تأیید" : "");
    }

    int[] paidDebtThisMonth() {
        int[] ym = Jalali.fromMillis(PatrolStore.now());
        String month = ym[0] + (ym[1] < 10 ? "/0" : "/") + ym[1];
        HashSet<String> paid = new HashSet<String>();
        for (JSONObject j : cloudCharges) {
            if ("OK".equals(j.optString("status")) && month.equals(j.optString("month", "")))
                paid.add(j.optString("n", "").trim());
        }
        int debt = 0;
        for (JSONObject r : cloudRoster) {
            String nm = r.optString("name", "").trim();
            if (!nm.isEmpty() && !paid.contains(nm)) debt++;
        }
        return new int[]{paid.size(), debt};
    }

    // ---------- helpers ----------

    void mini(LinearLayout row, String title, String big, int bg, final Runnable r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(14));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(g);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        box.setLayoutParams(lp);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(12);
        t1.setTextColor(0xFF607D8B);
        box.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(big);
        t2.setTextSize(19);
        t2.setTypeface(null, Typeface.BOLD);
        t2.setTextColor(0xFF212121);
        t2.setPadding(0, dp(3), 0, 0);
        box.addView(t2);
        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (r != null) r.run(); }
        });
        row.addView(box);
    }

    void card(String title, String sub, int bg, final Runnable r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(14));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(g);
        box.setPadding(dp(16), dp(13), dp(16), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        box.setLayoutParams(lp);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(16);
        t1.setTypeface(null, Typeface.BOLD);
        t1.setTextColor(0xFF212121);
        box.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextSize(13);
        t2.setTextColor(0xFF546E7A);
        t2.setPadding(0, dp(3), 0, 0);
        box.addView(t2);
        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (r != null) r.run(); }
        });
        grid.addView(box);
    }

    static String money(long v) {
        String s = String.format(java.util.Locale.US, "%,d", v).replace(',', '،');
        return Scheduler.fa(s);
    }

    static String clock(long ts) {
        java.util.Calendar ca = java.util.Calendar.getInstance();
        ca.setTimeInMillis(ts);
        String h = (ca.get(java.util.Calendar.HOUR_OF_DAY) < 10 ? "0" : "") + ca.get(java.util.Calendar.HOUR_OF_DAY);
        String m = (ca.get(java.util.Calendar.MINUTE) < 10 ? "0" : "") + ca.get(java.util.Calendar.MINUTE);
        return Scheduler.fa(h + ":" + m);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onPause() {
        super.onPause();
        uiVisible = false;
    }

    /** اگر اجرای قبلی خطا داده باشد، متنش را نشان بده (برای ارسال به سازنده) */
    void showLastCrash() {
        try {
            final String cr = CrashCatcher.lastCrash(this);
            if (cr == null || cr.trim().isEmpty()) return;
            final String txt = cr.length() > 1600 ? "…" + cr.substring(cr.length() - 1600) : cr;
            new AlertDialog.Builder(this)
                    .setTitle("🐞 خطای اجرای قبلی ثبت شد")
                    .setMessage(txt + "\n\nلطفاً با دکمهٔ کپی، متن را برای سازنده بفرستید.")
                    .setPositiveButton("📋 کپی متن", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            try {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("ngy-crash", cr));
                                Toast.makeText(MainActivity.this, "کپی شد ✅ — برای 09127285065 بفرستید", Toast.LENGTH_LONG).show();
                            } catch (Exception ignored) { }
                        }
                    })
                    .setNeutralButton("🗑 پاک کردن", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { CrashCatcher.clearCrash(MainActivity.this); }
                    })
                    .setNegativeButton("بستن", null)
                    .show();
        } catch (Exception ignored) { }
    }

    /** نشان تعداد ندیده‌ها روی زنگوله */
    void updateBell() {
        final android.widget.TextView bellBadge = findViewById(R.id.bellBadge);
        if (bellBadge == null) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    PatrolStore st = new PatrolStore(MainActivity.this);
                    final int n;
                    try { n = st.unseenNotifs(); } finally { st.close(); }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            try {
                                if (isDestroyed() || isFinishing()) return;
                                if (n > 0) {
                                    bellBadge.setVisibility(View.VISIBLE);
                                    bellBadge.setText(Scheduler.fa(String.valueOf(Math.min(n, 99))));
                                } else {
                                    bellBadge.setVisibility(View.GONE);
                                }
                            } catch (Throwable ignored) { }
                        }
                    });
                } catch (Throwable ignored) { }
            }
        }).start();
    }
}

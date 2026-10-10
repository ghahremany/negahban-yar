package ir.negahban.saken;

import android.app.Activity;
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

/** ساکن‌یار v1.2 — داشبورد اهالی + منوی ☰ سمت راست */
public class MainActivity extends Activity {

    LinearLayout grid, drawer;
    View scrim;
    TextView tvPush, tvHello, tvFoot, tvDrawerName, tvDrawerSub;

    ArrayList<JSONObject> pkgs = new ArrayList<JSONObject>();
    ArrayList<JSONObject> chgs = new ArrayList<JSONObject>();
    ArrayList<JSONObject> news = new ArrayList<JSONObject>();

    /** سرویس وقتی این true است اعلان سیستمی نمی‌دهد */
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
        setContentView(R.layout.activity_main);
        grid = findViewById(R.id.grid);
        tvPush = findViewById(R.id.tvPush);
        tvHello = findViewById(R.id.tvHello);
        tvFoot = findViewById(R.id.tvFoot);
        scrim = findViewById(R.id.scrim);
        drawer = findViewById(R.id.drawer);
        tvDrawerName = findViewById(R.id.tvDrawerName);
        tvDrawerSub = findViewById(R.id.tvDrawerSub);
        // 🔔 زنگولهٔ اعلان‌ها
        android.widget.FrameLayout bellBox = findViewById(R.id.bellBox);
        final android.widget.TextView bellBadge = findViewById(R.id.bellBadge);
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setColor(0xFFE53935);
        badgeBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bellBadge.setBackground(badgeBg);
        bellBox.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, NotificationsActivity.class)); }
        });
        uiVisible = true;
        updateBell();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                       != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
            }
        } catch (Exception ignored) { }
        try {
            Intent sv = new Intent(this, NotifyService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(sv);
            else startService(sv);
        } catch (Exception ignored) { }
        final Runnable bellTick = new Runnable() {
            @Override public void run() {
                if (isDestroyed() || isFinishing()) return;
                updateBell();
                bellHandler.postDelayed(this, 10000);
            }
        };
        bellHandler.postDelayed(bellTick, 10000);

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
        if (Updater.forceNeeded(this)) {
            startActivity(new Intent(this, ForceUpdateActivity.class));
            finish();
            return;
        }
        refresh();
        loadCloud();
        backgroundChecks();
    }

    // ================= منوی ☰ =================

    /** اتصال آیتم‌های منو (استاتیک در XML) */
    void bindDrawer() {
        int[] ids = {R.id.dDash, R.id.dPkgs, R.id.dGuest, R.id.dCharge, R.id.dRoof,
                R.id.dMove, R.id.dElev, R.id.dMsg, R.id.dJoin, R.id.dUpdate, R.id.dSettings};
        for (int i = 0; i < ids.length; i++) {
            final int idx = i;
            findViewById(ids[i]).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { menu(idx); }
            });
        }
    }

    void svcRequest(String kind, String title, String emoji, String okHint) {
        if (!Me.registered(this)) { needJoin(); return; }
        Intent i = new Intent(this, ServiceRequestActivity.class);
        i.putExtra("kind", kind);
        i.putExtra("title", title);
        i.putExtra("emoji", emoji);
        i.putExtra("okHint", okHint);
        startActivity(i);
    }

    void menu(int idx) {
        closeDrawer();
        switch (idx) {
            case 0: refresh(); loadCloud(); break;
            case 1: goIfReg(PackagesActivity.class); break;
            case 2: goIfReg(GuestRequestActivity.class); break;
            case 3: goIfReg(ChargeActivity.class); break;
            case 4: goIfReg(RoofKeyActivity.class); break;
            case 5: svcRequest("MOVE", "درخواست اسباب‌کشی", "🚚", "با هماهنگی نگهبانی، راه دسترسی باز میشود"); break;
            case 6: svcRequest("ELEV", "استفاده از آسانسور", "🛗", "برای جابه‌جایی اسباب، آسانسور در اختیارت قرار میگیرد"); break;
            case 7:
                if (!Me.registered(this)) { needJoin(); break; }
                messageToManager();
                break;
            case 8: startActivity(new Intent(this, GateActivity.class)); break;
            case 9: UpdaterUi.run(this, false); break;
            case 10: startActivity(new Intent(this, SettingsActivity.class)); break;
        }
    }

    void goIfReg(Class<?> a) {
        if (!Me.registered(this)) { needJoin(); return; }
        startActivity(new Intent(this, a));
    }

    void openDrawer() {
        if (Me.registered(this)) {
            tvDrawerName.setText("👋 " + Me.name(this));
            tvDrawerSub.setText("واحد " + Scheduler.fa(Me.unit(this)) + " • " + Cfg.building(this));
        } else {
            tvDrawerName.setText("🏠 ساکن‌یار");
            tvDrawerSub.setText("هنوز عضو نشده‌ای");
        }
        scrim.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0).setDuration(220).start();
    }

    void closeDrawer() {
        scrim.setVisibility(View.GONE);
        drawer.animate().translationX(drawer.getWidth()).setDuration(200).start();
    }

    // ================= داشبورد =================

    void refresh() {
        boolean reg = Me.registered(this);
        if (reg) {
            tvHello.setText("سلام " + Me.name(this) + " 👋   واحد " + Scheduler.fa(Me.unit(this))
                    + "   •   " + Cfg.building(this));
        } else {
            tvHello.setText("👋 خوش آمدی — برای استفاده از خدمات، عضو شو");
        }
        grid.removeAllViews();

        if (!reg) {
            card("📝 عضویت ساکن", "نام، همراه و واحدت را ثبت کن؛ با تأیید مدیر وارد میشوی",
                    0xFFFFF3CD, new Runnable() {
                        @Override public void run() { startActivity(new Intent(MainActivity.this, GateActivity.class)); }
                    });
        }

        if (Hub.code(this).isEmpty()) {
            card("🔑 اتصال به ساختمان", "برای اتصال، شمارهٔ همراه مدیر را در ⚙️ وارد کن",
                    0xFFFFEBEE, new Runnable() {
                        @Override public void run() { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
                    });
        }

        // ۱) بسته‌ها
        int mine = myPackages().size();
        if (mine > 0) {
            StringBuilder sb = new StringBuilder();
            ArrayList<JSONObject> mp = myPackages();
            for (int i = 0; i < mp.size() && i < 3; i++) {
                if (sb.length() > 0) sb.append("، ");
                sb.append(mp.get(i).optString("kind", "بسته"));
            }
            card("📦 " + Scheduler.fa(String.valueOf(mine)) + " بسته در نگهبانی منتظر توست",
                    sb.toString(), 0xFFFFF8E1, new Runnable() {
                        @Override public void run() { goIfReg(PackagesActivity.class); }
                    });
        } else {
            card("📦 بسته‌ها", "بسته‌ای در نگهبانی نداری", 0xFFFFFFFF, new Runnable() {
                @Override public void run() { goIfReg(PackagesActivity.class); }
            });
        }

        // ۲) شارژ
        int[] pm = myChargeThisMonth();
        if (pm[0] > 0) {
            card("💳 شارژ این ماه ✅", Scheduler.fa(String.valueOf(pm[0])) + " پرداخت ثبت کرده‌ای — همه تأییدشده",
                    0xFFE8F5E9, new Runnable() {
                        @Override public void run() { goIfReg(ChargeActivity.class); }
                    });
        } else {
            String extra = pm[1] > 0 ? "\nآخرین پرداختت: " + Scheduler.fa(String.valueOf(pm[1])) + " تومان (در انتظار تأیید)" : "";
            card("💳 شارژ این ماه", "⛔ هنوز پرداختی برای این ماه تأیید نشده" + extra,
                    0xFFFFEBEE, new Runnable() {
                        @Override public void run() { goIfReg(ChargeActivity.class); }
                    });
        }

        // ۳) اخبار ساختمان
        StringBuilder nb = new StringBuilder();
        for (int i = news.size() - 1, c = 0; i >= 0 && c < 3; i--, c++) {
            JSONObject n = news.get(i);
            if (nb.length() > 0) nb.append("\n");
            nb.append("📰 ").append(n.optString("title", ""));
        }
        card("📰 اخبار ساختمان", nb.length() == 0 ? "خبر تازه‌ای نیست" : nb.toString(),
                nb.length() == 0 ? 0xFFFFFFFF : 0xFFE3F2FD, new Runnable() {
                    @Override public void run() { newsDialog(); }
                });

        tvFoot.setText("🧭 سوئیت «یار» • ساکن‌یار v" + Scheduler.fa(Updater.localVersion(this)));
    }

    void newsDialog() {
        if (news.isEmpty()) { toast("خبر تازه‌ای نیست"); return; }
        StringBuilder sb = new StringBuilder();
        for (int i = news.size() - 1; i >= 0; i--) {
            JSONObject n = news.get(i);
            sb.append("📰 ").append(n.optString("title", "")).append("\n")
              .append(n.optString("body", "")).append("\n")
              .append(Scheduler.jalaliDate(n.optLong("ts", 0))).append("\n\n");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("📰 اخبار ساختمان")
                .setMessage(sb.toString())
                .setPositiveButton("بستن", null)
                .show();
    }

    void guests() { }

    ArrayList<JSONObject> myPackages() {
        ArrayList<JSONObject> out = new ArrayList<JSONObject>();
        String u = Me.unit(this), m = Me.mobile(this);
        for (JSONObject j : pkgs) {
            if (!"PENDING".equals(j.optString("status", "PENDING"))) continue;
            if (u.equals(j.optString("unit", "")) || m.equals(j.optString("mobile", ""))) out.add(j);
        }
        return out;
    }

    /** [تعدادپرداخت تأییدشدهٔ این ماه، آخرین مبلغ در انتظار] */
    int[] myChargeThisMonth() {
        int[] ym = Jalali.fromMillis(PatrolStore.now());
        String month = ym[0] + (ym[1] < 10 ? "/0" : "/") + ym[1];
        int ok = 0, pend = 0;
        String u = Me.unit(this), n = Me.name(this);
        for (JSONObject j : chgs) {
            if (!n.equals(j.optString("n", "")) && !u.equals(j.optString("unit", ""))) continue;
            if ("OK".equals(j.optString("status")) && month.equals(j.optString("month", ""))) ok++;
            else if (!"OK".equals(j.optString("status"))) pend = (int) j.optLong("amount", 0);
        }
        return new int[]{ok, pend};
    }

    void needJoin() {
        Toast.makeText(this, "اول عضو شو (📝)", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, GateActivity.class));
    }

    void messageToManager() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("پیامت را بنویس…");
        et.setMinLines(2);
        et.setGravity(android.view.Gravity.TOP);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        box.addView(et);
        new android.app.AlertDialog.Builder(this)
                .setTitle("✉️ پیام به مدیر ساختمان")
                .setView(box)
                .setPositiveButton("ارسال", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        final String msg = et.getText().toString().trim();
                        if (msg.isEmpty()) { toast("پیام خالی است"); return; }
                        toast("در حال ارسال…");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                String text = "✉️ پیام ساکن:\n👤 " + Me.name(MainActivity.this)
                                        + " — واحد " + Me.unit(MainActivity.this)
                                        + "\n📱 " + Scheduler.fa(Me.mobile(MainActivity.this))
                                        + "\n\n" + msg;
                                String code = Hub.code(MainActivity.this);
                                final boolean ok = !code.isEmpty()
                                        && Hub.msg(code, "modir", Me.inbox(MainActivity.this), "MSG", text).ok;
                                runOnUiThread(new Runnable() {
                                    @Override public void run() { toast(ok ? "✅ ارسال شد — مدیر پیامت را می‌بیند" : "⛔ ارسال نشد (اینترنت؟)"); }
                                });
                            }
                        }).start();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    // ---------- دادهٔ ابری ----------

    void loadCloud() {
        final String code = Hub.code(this);
        if (code.isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                ArrayList<JSONObject> p = new ArrayList<JSONObject>();
                Hub.Res r1 = Hub.dataGet(code, "packages");
                if (r1.ok) {
                    try {
                        JSONArray a = new JSONArray(r1.payload);
                        for (int i = 0; i < a.length(); i++) p.add(a.getJSONObject(i));
                    } catch (Exception ignored) { }
                }
                ArrayList<JSONObject> c = new ArrayList<JSONObject>();
                Hub.Res r2 = Hub.dataGet(code, "charges");
                if (r2.ok) {
                    try {
                        JSONArray a = new JSONArray(r2.payload);
                        for (int i = 0; i < a.length(); i++) c.add(a.getJSONObject(i));
                    } catch (Exception ignored) { }
                }
                ArrayList<JSONObject> nw = new ArrayList<JSONObject>();
                Hub.Res r3 = Hub.dataGet(code, "news");
                if (r3.ok) {
                    try {
                        JSONArray a = new JSONArray(r3.payload);
                        for (int i = 0; i < a.length(); i++) nw.add(a.getJSONObject(i));
                    } catch (Exception ignored) { }
                }
                pkgs = p;
                chgs = c;
                news = nw;
                runOnUiThread(new Runnable() { @Override public void run() { refresh(); } });
            }
        }).start();
    }

    // ---------- helpers ----------

    void card(String title, String sub, int bg, final Runnable open) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg == -1 ? Color.WHITE : bg);
        g.setCornerRadius(dp(14));
        card.setBackground(g);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(0xFF0F3D56);
        card.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(13);
        s.setTextColor(0xFF607D8B);
        card.addView(s);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open.run(); }
        });
        grid.addView(card);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // ---------- چک‌های پس‌زمینه ----------
    private void backgroundChecks() {
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
                final PushFeed.Item p = PushFeed.fetchUnseen(MainActivity.this, "saken");
                if (p != null) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            try { PushFeed.show(MainActivity.this, p); } catch (Exception ignored) {}
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiVisible = false;
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
                        if (isDestroyed() || isFinishing()) return;
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
}

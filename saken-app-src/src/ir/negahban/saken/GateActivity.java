package ir.negahban.saken;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * ثبت‌نام ساکن — چهار مرحله:
 * ۱) خودم: نام، همراه، واحد  ۲) وضعیت: مالک یا مستأجر  ۳) ملک: نفرات، خودرو، پلاک، پارکینگ، کد ملی
 * ۴) اتصال: شمارهٔ همراه مدیر → ارسال به هاب → انتظار تأیید مدیر
 */
public class GateActivity extends Activity {

    LinearLayout root, stepBox;
    int step = 1;
    volatile boolean stop = false;

    // ۱) خودم
    EditText eName, eMobile, eUnit;
    // ۲) وضعیت
    int role = -1; // 0=مالک 1=مستأجر
    TextView tOwner, tTenant;
    // ۳) ملک
    EditText eNres, eCar, ePlate, ePark, eNid;
    // ۴) اتصال
    EditText eMgr;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.WHITE);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(outer);

        // سربرگ
        TextView head = new TextView(this);
        head.setText("📝 ثبت‌نام ساکن");
        head.setTextSize(20);
        head.setTypeface(null, Typeface.BOLD);
        head.setTextColor(Color.WHITE);
        head.setBackgroundColor(0xFF0F3D56);
        head.setPadding(dp(16), dp(12), dp(16), dp(12));
        outer.addView(head);

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(20));
        sc.addView(root);
        outer.addView(sc);

        // نوار مراحل
        stepBox = new LinearLayout(this);
        stepBox.setOrientation(LinearLayout.HORIZONTAL);
        stepBox.setGravity(Gravity.CENTER);
        stepBox.setPadding(0, dp(10), 0, dp(6));
        outer.addView(stepBox, 1);

        render();
    }

    /** نوار پیشرفت: ۴ نقطه */
    void drawSteps() {
        stepBox.removeAllViews();
        for (int i = 1; i <= 4; i++) {
            TextView t = new TextView(this);
            t.setText(i < step ? "●" : i == step ? "◉" : "○");
            t.setTextSize(i == step ? 21 : 15);
            t.setTextColor(i <= step ? 0xFF12B5A5 : 0xFFB0BEC5);
            t.setPadding(dp(10), 0, dp(10), 0);
            stepBox.addView(t);
            if (i < 4) {
                View ln = new View(this);
                ln.setBackgroundColor(0xFFCFD8DC);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(1));
                lp.setMargins(0, 0, 0, dp(6));
                ln.setLayoutParams(lp);
                stepBox.addView(ln);
            }
        }
    }

    void render() {
        drawSteps();
        root.removeAllViews();
        if (step == 1) stepMe();
        else if (step == 2) stepRole();
        else if (step == 3) stepProperty();
        else stepConnect();
    }

    TextView cap(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(0xFF0F3D56);
        t.setPadding(0, dp(6), 0, dp(4));
        return t;
    }

    TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(0xFF607D8B);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    EditText field(String label, String value, String hintx, int type) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13.5f);
        tv.setPadding(0, dp(10), 0, dp(2));
        root.addView(tv);
        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hintx);
        et.setInputType(type);
        et.setTextSize(15);
        root.addView(et);
        return et;
    }

    // ---------- مرحله ۱: خودم ----------
    void stepMe() {
        root.addView(cap("۱ از ۴ — اطلاعات خودت"));
        root.addView(hint("نام و راه تماس و واحدت را وارد کن."));
        eName = field("نام و نام خانوادگی", Me.name(this), "مثلاً علی رضایی", InputType.TYPE_CLASS_TEXT);
        eMobile = field("شمارهٔ همراه", Me.mobile(this), "مثلاً 09121234567", InputType.TYPE_CLASS_PHONE);
        eUnit = field("واحد (شمارهٔ خانه/آپارتمان)", Me.unit(this), "مثلاً ۵", InputType.TYPE_CLASS_TEXT);
        nav(true, false);
    }

    // ---------- مرحله ۲: وضعیت ----------
    void stepRole() {
        root.addView(cap("۲ از ۴ — وضعیتت در این ملک"));
        root.addView(hint("مالک هستی یا مستأجر؟"));
        tOwner = pickCard("🏠 مالک", "سند ملک به نام من است", role == 0);
        tTenant = pickCard("🔑 مستأجر", "از مالک اجاره کرده‌ام", role == 1);
        nav(true, true);
    }

    TextView pickCard(String title, String sub, boolean sel) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(sel ? 0xFFE0F7F4 : 0xFFF7FAFB);
        g.setCornerRadius(dp(14));
        if (sel) g.setStroke(dp(2), 0xFF12B5A5); else g.setStroke(dp(1), 0xFFE4EDF2);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(g);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(15.5f);
        t1.setTypeface(null, Typeface.BOLD);
        t1.setTextColor(0xFF0F3D56);
        card.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextSize(12.5f);
        t2.setTextColor(0xFF607D8B);
        card.addView(t2);
        final int which = title.startsWith("🏠") ? 0 : 1;
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { role = which; render(); }
        });
        root.addView(card);
        return t1;
    }

    // ---------- مرحله ۳: ملک ----------
    void stepProperty() {
        root.addView(cap("۳ از ۴ — اطلاعات ملک"));
        root.addView(hint("این اطلاعات به مدیریت و نگهبانی کمک میکند (مثلاً پلاک خودرو برای ورود به پارکینگ)."));
        eNres = field("تعداد نفرات ساکن در واحد", "", "مثلاً ۳", InputType.TYPE_CLASS_NUMBER);
        eCar = field("نام خودرو (اختیاری)", "", "مثلاً پژو ۲۰۶", InputType.TYPE_CLASS_TEXT);
        ePlate = field("پلاک خودرو (اختیاری)", "", "مثلاً ۱۲ ب ۳۴۵", InputType.TYPE_CLASS_TEXT);
        ePark = field("شمارهٔ پارکینگ (اختیاری)", "", "مثلاً P۱۲", InputType.TYPE_CLASS_TEXT);
        eNid = field("کد ملی (اختیاری)", "", "۱۰ رقم", InputType.TYPE_CLASS_NUMBER);
        nav(true, true);
    }

    // ---------- مرحله ۴: اتصال ----------
    void stepConnect() {
        root.addView(cap("۴ از ۴ — اتصال به ساختمان"));
        root.addView(hint("شمارهٔ همراه مدیر ساختمان را وارد کن. درخواستت برایش ارسال میشود و با تأییدش وارد میشوی."));
        eMgr = field("شمارهٔ همراه مدیر ساختمان", Cfg.p(this).getString("bldgCode", ""), "مثلاً 09127285065", InputType.TYPE_CLASS_PHONE);
        if (Me.hasPending(this)) {
            Button chk = new Button(this);
            chk.setText("🔍 بررسی وضعیت تأیید ثبت‌نام قبلی");
            chk.setAllCaps(false);
            chk.setTextColor(Color.WHITE);
            chk.setBackgroundResource(R.drawable.btn_dark);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(46));
            clp.topMargin = dp(16);
            chk.setLayoutParams(clp);
            chk.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { checkOnly(); }
            });
            root.addView(chk);
        }
        TextView sum = new TextView(this);
        sum.setText("خلاصه: " + eName0() + " — واحد " + Scheduler.fa(eUnit0()) + " — " + roleText());
        sum.setTextSize(12.5f);
        sum.setTextColor(0xFF455A64);
        sum.setPadding(0, dp(12), 0, 0);
        root.addView(sum);
        nav(true, true);
    }

    // ---------- ناوبری ----------
    void nav(boolean next, boolean back) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(18);
        row.setLayoutParams(rlp);
        if (back) {
            Button bk = new Button(this);
            bk.setText("قبلی");
            bk.setAllCaps(false);
            bk.setTextColor(Color.WHITE);
            bk.setBackgroundResource(R.drawable.btn_dark);
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1);
            p1.setMargins(0, 0, dp(4), 0);
            bk.setLayoutParams(p1);
            bk.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { step--; render(); }
            });
            row.addView(bk);
        }
        Button nx = new Button(this);
        nx.setText(step == 4 ? "📨 ثبت و انتظار تأیید" : "بعدی");
        nx.setAllCaps(false);
        nx.setTextColor(Color.WHITE);
        nx.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -2, 1);
        p2.setMargins(dp(4), 0, 0, 0);
        nx.setLayoutParams(p2);
        nx.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onNext(); }
        });
        row.addView(nx);
        root.addView(row);
    }

    void onNext() {
        if (step == 1) {
            if (eName.getText().toString().trim().length() < 3) { toast("نام را کامل بنویس"); return; }
            if (SettingsActivity.normMobile(eMobile.getText().toString()).length() != 11) { toast("شمارهٔ همراه را درست وارد کن"); return; }
            if (eUnit.getText().toString().trim().isEmpty()) { toast("واحد را وارد کن"); return; }
            step = 2;
        } else if (step == 2) {
            if (role < 0) { toast("مالک هستی یا مستأجر؟"); return; }
            step = 3;
        } else if (step == 3) {
            step = 4;
        } else {
            submit();
            return;
        }
        render();
    }

    String eName0() { return eName == null ? Me.name(this) : eName.getText().toString().trim(); }
    String eUnit0() { return eUnit == null ? Me.unit(this) : eUnit.getText().toString().trim(); }
    String roleText() { return role == 0 ? "مالک" : role == 1 ? "مستأجر" : "—"; }

    // ---------- ارسال و انتظار ----------
    void submit() {
        final String code = SettingsActivity.normMobile(eMgr.getText().toString());
        if (code.length() != 11 || !code.startsWith("09")) { toast("شمارهٔ مدیر را درست وارد کن (11 رقم)"); return; }
        Cfg.set(this, "bldgCode", code);
        final String uid = Me.uid(this);
        final AlertDialog wait = progress("⏳ در حال ارسال ثبت‌نام…\nبعد از آن منتظر تأیید مدیر می‌مانیم.");
        stop = false;
        new Thread(new Runnable() {
            @Override public void run() {
                Hub.Res r = Hub.resReg(code, uid,
                        eName0(),
                        SettingsActivity.normMobile(eMobile.getText().toString()),
                        eUnit0(),
                        role == 0 ? "owner" : "tenant",
                        eCar == null ? "" : eCar.getText().toString().trim(),
                        ePlate == null ? "" : ePlate.getText().toString().trim(),
                        ePark == null ? "" : ePark.getText().toString().trim(),
                        eNid == null ? "" : eNid.getText().toString().trim(),
                        eNres == null ? 0 : parseInt0(eNres));
                Me.setPending(GateActivity.this, true);
                if (!r.ok && !r.err.contains("تعلق")) {
                    done(wait, "ERR", r.err);
                    return;
                }
                // انتظار برای تأیید: پرس‌وجوی وضعیت هر ۳ ثانیه
                long deadline = PatrolStore.now() + 120000;
                String state = "PENDING";
                while (PatrolStore.now() < deadline && !stop) {
                    Hub.Res2 st = Hub.resStatus(code, uid);
                    if (st.ok) {
                        state = st.status;
                        if ("OK".equals(state) || "NO".equals(state)) break;
                    }
                    try { Thread.sleep(3000); } catch (Exception e) { return; }
                }
                done(wait, stop ? "CANCEL" : state, "");
            }
        }).start();
    }

    int parseInt0(EditText e) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception x) { return 0; }
    }

    void done(final AlertDialog wait, final String state, final String err) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try { wait.dismiss(); } catch (Exception ignored) { }
                if ("CANCEL".equals(state)) return;
                if ("ERR".equals(state)) {
                    new AlertDialog.Builder(GateActivity.this)
                            .setTitle("ثبت نشد")
                            .setMessage("⛔ " + err + "\n\nاگر «این واحد قبلاً ثبت شده» است، با مدیریت ساختمان هماهنگ کن.")
                            .setPositiveButton("باشه", null).show();
                    return;
                }
                if ("OK".equals(state)) {
                    Me.setPending(GateActivity.this, false);
                    Me.save(GateActivity.this,
                            eName0(),
                            SettingsActivity.normMobile(eMobile.getText().toString()),
                            eUnit0(),
                            role == 0 ? "مالک" : "مستأجر");
                    new AlertDialog.Builder(GateActivity.this)
                            .setTitle("🎉 خوش آمدی")
                            .setMessage("مدیر ثبت‌نامت را تأیید کرد.\nحالا بسته‌ها، مهمان، شارژ، کلید پشت‌بام و پیام به مدیر همه از همین اپ انجام میشود.")
                            .setPositiveButton("شروع", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) { finish(); }
                            }).setCancelable(false).show();
                } else if ("NO".equals(state)) {
                    new AlertDialog.Builder(GateActivity.this)
                            .setTitle("ثبت‌نامت تأیید نشد")
                            .setMessage("مدیر درخواستت را تأیید نکرد. برای راهنمایی با مدیریت ساختمان صحبت کن و دوباره امتحان کن.")
                            .setPositiveButton("باشه", null).show();
                } else {
                    new AlertDialog.Builder(GateActivity.this)
                            .setTitle("⏳ در انتظار تأیید مدیر")
                            .setMessage("ثبت‌نامت رفت دست مدیریت.\nوقتی تأیید شد، دکمهٔ «بررسی وضعیت» در همین صفحه یا داشبورد جوابت را میدهد.")
                            .setPositiveButton("باشه", null).show();
                }
            }
        });
    }

    AlertDialog progress(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(20), dp(18), dp(20), dp(10));
        return new AlertDialog.Builder(this)
                .setTitle("📝 ثبت‌نام")
                .setView(tv)
                .setCancelable(false)
                .setNegativeButton("لغو انتظار", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { stop = true; }
                })
                .show();
    }

    /** بررسی وضعیت بدون ثبت دوباره (از منو) */
    void checkOnly() {
        final String code = Hub.code(this);
        if (code.isEmpty() || !Me.hasPending(this)) return;
        final AlertDialog wait = progress("در حال بررسی…");
        stop = false;
        new Thread(new Runnable() {
            @Override public void run() {
                long deadline = PatrolStore.now() + 20000;
                String state = "PENDING";
                while (PatrolStore.now() < deadline && !stop) {
                    Hub.Res2 st = Hub.resStatus(code, Me.uid(GateActivity.this));
                    if (st.ok && ("OK".equals(st.status) || "NO".equals(st.status))) { state = st.status; break; }
                    try { Thread.sleep(3000); } catch (Exception e) { return; }
                }
                done(wait, stop ? "CANCEL" : state, "");
            }
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

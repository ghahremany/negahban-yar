package ir.negahban.patrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** ورود مهمان: نام، همراه، پلاک خودرو، کد ملی — با ثبت خروج */
public class GuestActivity extends Activity {

    LinearLayout listBox;

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
        title.setText("🚶 ورود مهمان");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        final EditText eName = new EditText(this);
        final EditText eMobile = new EditText(this);
        final EditText eUnit = new EditText(this);
        final EditText eNid = new EditText(this);
        eName.setHint("نام و نام خانوادگی مهمان");
        eMobile.setHint("شمارهٔ همراه (09xxxxxxxxx)");
        eMobile.setInputType(InputType.TYPE_CLASS_PHONE);
        eUnit.setHint("شمارهٔ واحد (مثل 150)");
        eUnit.setInputType(InputType.TYPE_CLASS_NUMBER);
        eNid.setHint("کد ملی (۱۰ رقم) — اختیاری");
        eNid.setInputType(InputType.TYPE_CLASS_NUMBER);

        // پلاک چندقسمتی ایرانی: ۱۲ ب ۳۴۵ ایران ۱۱
        final EditText p1 = new EditText(this);
        final EditText p2 = new EditText(this);
        final EditText pPr = new EditText(this);
        final Spinner spLet = new Spinner(this);
        p1.setHint("۰۰"); p2.setHint("۰۰۰"); pPr.setHint("۰۰");
        String[] lets = {"ب","پ","ج","د","س","ش","ص","ط","ع","ق","ل","م","ن","ه","و"};
        ArrayAdapter<String> la = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, lets);
        la.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLet.setAdapter(la);
        spLet.setBackground(round(0xFFF2F4F7, dp(4)));

        LinearLayout plate = new LinearLayout(this);
        plate.setOrientation(LinearLayout.HORIZONTAL);
        plate.setGravity(android.view.Gravity.CENTER);
        plate.setBackground(plateBg());
        plate.setPadding(dp(6), dp(4), dp(6), dp(4));
        plate.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(60)));
        plate.addView(plateBox(p1, 2, 48));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(52), dp(42));
        slp.setMargins(dp(4), 0, dp(4), 0);
        spLet.setLayoutParams(slp);
        plate.addView(spLet);
        plate.addView(plateBox(p2, 3, 60));
        LinearLayout prov = new LinearLayout(this);
        prov.setOrientation(LinearLayout.VERTICAL);
        prov.setGravity(android.view.Gravity.CENTER);
        TextView ir = new TextView(this);
        ir.setText("ایران");
        ir.setTextSize(9);
        ir.setTextColor(0xFF333333);
        prov.addView(ir);
        prov.addView(plateBox(pPr, 2, 42));
        plate.addView(prov);
        View band = new View(this);
        android.graphics.drawable.GradientDrawable bbg = new android.graphics.drawable.GradientDrawable();
        bbg.setColor(0xFF1E4FA0);
        bbg.setCornerRadius(dp(4));
        band.setBackground(bbg);
        plate.addView(band, new LinearLayout.LayoutParams(dp(14), dp(44)));

        int pad = dp(10);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setBackground(round(0xFFF7F9FA, dp(12)));
        form.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-1, -2);
        fLp.topMargin = dp(12);
        form.setLayoutParams(fLp);
        TextView note = new TextView(this);
        note.setText("⚡️ فیلدهای ستاره‌دار اجباری هستند");
        note.setTextSize(11.5f);
        note.setTextColor(0xFF78909C);
        form.addView(note);
        form.addView(label("نام و نام خانوادگی مهمان ⭐", 8));
        form.addView(eName);
        form.addView(label("شمارهٔ همراه ⭐", 6));
        form.addView(eMobile);
        form.addView(label("واحد مقصد ⭐", 6));
        form.addView(eUnit);
        form.addView(label("پلاک خودرو ⭐", 6));
        form.addView(plate);
        form.addView(label("کد ملی (اختیاری)", 6));
        form.addView(eNid);
        root.addView(form);

        Button add = new Button(this);
        add.setText("✅ ثبت ورود");
        add.setTextColor(Color.WHITE);
        add.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(-1, -2);
        aLp.topMargin = dp(10);
        add.setLayoutParams(aLp);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // — صحت‌سنجی همهٔ فیلدها —
                String name = eName.getText().toString().trim();
                String mobileRaw = faToEn(eMobile.getText().toString().trim());
                String unit = faToEn(eUnit.getText().toString().trim());
                String nidRaw = faToEn(eNid.getText().toString().trim());
                String s1 = faToEn(p1.getText().toString().trim());
                String s2 = faToEn(p2.getText().toString().trim());
                String spr = faToEn(pPr.getText().toString().trim());
                Object letSel = spLet.getSelectedItem();
                String let = letSel == null ? "" : letSel.toString();

                if (name.length() < 3) { eName.setError("نام را کامل وارد کنید"); eName.requestFocus(); return; }
                String mobile = mobileRaw;
                if (mobile.startsWith("+98")) mobile = "0" + mobile.substring(3);
                else if (mobile.startsWith("0098")) mobile = "0" + mobile.substring(4);
                else if (mobile.startsWith("98") && mobile.length() == 12) mobile = "0" + mobile.substring(2);
                if (!mobile.matches("09\\d{9}")) { eMobile.setError("شمارهٔ همراه باید ۱۱ رقم و با 09 شروع شود"); eMobile.requestFocus(); return; }
                if (unit.isEmpty() || !unit.matches("\\d{1,4}") || Integer.parseInt(unit) < 1) { eUnit.setError("شمارهٔ واحد را درست وارد کنید"); eUnit.requestFocus(); return; }
                if (!s1.matches("\\d{2}") || !s2.matches("\\d{3}") || !spr.matches("\\d{2}")) {
                    toast("پلاک را کامل وارد کنید: ۲ رقم، حرف، ۳ رقم، ایران، ۲ رقم");
                    EditText bad = !s1.matches("\\d{2}") ? p1 : !s2.matches("\\d{3}") ? p2 : pPr;
                    bad.requestFocus();
                    return;
                }
                String nid = nidRaw;
                if (!nid.isEmpty() && !nid.matches("\\d{10}")) { eNid.setError("کد ملی باید ۱۰ رقم باشد"); eNid.requestFocus(); return; }
                String plate = s1 + let + s2 + "ایران" + spr;
                // — ثبت —
                long gts = PatrolStore.now();
                PatrolStore st = new PatrolStore(GuestActivity.this);
                st.addGuestTs(gts, name, mobile, plate, nid, 0, unit);
                // خبر ماشینی برای مدیر یار: همگام‌سازی NGHQ + گزارش خوانا
                st.enqK("NGHQ", "NGHQ|GUEST_IN|" + gts + "|" + name + "|" + mobile + "|" + plate + "|" + nid + "|" + unit);
                st.enqK("GUEST_IN", "👤 مهمان: " + name
                        + "\n🏠 واحد: " + Scheduler.fa(unit)
                        + "\n📱 همراه: " + Scheduler.fa(mobile)
                        + "\n🚗 پلاک: " + Scheduler.fa(plate)
                        + "\n🕐 " + Scheduler.jalaliDate(gts) + " — " + Scheduler.fa(Scheduler.hm(gts)));
                st.close();
                Sync.kick(GuestActivity.this);   // ارسال فوری به مدیر
                eName.setText(""); eMobile.setText(""); eUnit.setText(""); eNid.setText("");
                p1.setText(""); p2.setText(""); pPr.setText("");
                toast("ورود مهمان ثبت شد ✅ — خبر فوری به مدیر رفت");
                refresh();
            }
        });
        root.addView(add);

        TextView hist = new TextView(this);
        hist.setText("📜 مهمان‌های اخیر");
        hist.setTypeface(null, Typeface.BOLD);
        hist.setPadding(0, dp(18), 0, dp(4));
        root.addView(hist);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        PatrolStore st = new PatrolStore(this);
        final ArrayList<String[]> rows = st.allGuests(50);
        st.close();
        listBox.removeAllViews();

        // درخواست‌های مهمانِ ساکن‌ها (از سرور) — تأیید توسط نگهبان
        final String hCode = Hub.code(this);
        if (!hCode.isEmpty()) {
            final LinearLayout srvBox = new LinearLayout(this);
            srvBox.setOrientation(LinearLayout.VERTICAL);
            listBox.addView(srvBox);
            new Thread(new Runnable() {
                @Override public void run() {
                    final java.util.ArrayList<String[]> reqs = Hub.guestList(hCode);
                    if (reqs.isEmpty()) return;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            TextView h = new TextView(GuestActivity.this);
                            h.setText("🔔 درخواست مهمانِ ساکن‌ها (منتظر تأیید تو)");
                            h.setTypeface(null, Typeface.BOLD);
                            h.setTextColor(0xFFB71C1C);
                            h.setPadding(0, dp(6), 0, dp(2));
                            srvBox.addView(h);
                            for (final String[] q : reqs) {
                                LinearLayout row = new LinearLayout(GuestActivity.this);
                                row.setOrientation(LinearLayout.VERTICAL);
                                row.setBackground(round(0xFFFFF8E1, dp(10)));
                                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                                lp.topMargin = dp(6);
                                row.setLayoutParams(lp);
                                TextView t = new TextView(GuestActivity.this);
                                t.setText("👤 " + q[1] + "\n📞 " + Scheduler.fa(q[2])
                                        + (q[3].isEmpty() ? "" : "  |  🚗 " + q[3])
                                        + (q[4].isEmpty() ? "" : "\n🕐 " + q[4]));
                                t.setTextSize(13);
                                row.addView(t);
                                LinearLayout br = new LinearLayout(GuestActivity.this);
                                br.setOrientation(LinearLayout.HORIZONTAL);
                                Button ok = new Button(GuestActivity.this);
                                ok.setText("✅ تأیید ورود");
                                ok.setAllCaps(false);
                                ok.setTextColor(Color.WHITE);
                                ok.setBackgroundResource(R.drawable.btn_primary);
                                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1);
                                p1.setMargins(0, dp(6), dp(4), 0);
                                ok.setLayoutParams(p1);
                                ok.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        v.setEnabled(false);
                                        new Thread(new Runnable() {
                                            @Override public void run() {
                                                final Hub.Res r = Hub.guestDecide(hCode, Long.parseLong(q[0]), true);
                                                runOnUiThread(new Runnable() {
                                                    @Override public void run() {
                                                        Toast.makeText(GuestActivity.this, r.ok ? "✅ تأیید شد — ساکن و مدیر اطلاع گرفتند" : "⛔ انجام نشد", Toast.LENGTH_SHORT).show();
                                                        refresh();
                                                    }
                                                });
                                            }
                                        }).start();
                                    }
                                });
                                Button no = new Button(GuestActivity.this);
                                no.setText("✖️ رد");
                                no.setAllCaps(false);
                                no.setTextColor(Color.WHITE);
                                no.setBackgroundResource(R.drawable.btn_dark);
                                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -2, 1);
                                p2.setMargins(dp(4), dp(6), 0, 0);
                                no.setLayoutParams(p2);
                                no.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        v.setEnabled(false);
                                        new Thread(new Runnable() {
                                            @Override public void run() {
                                                final Hub.Res r = Hub.guestDecide(hCode, Long.parseLong(q[0]), false);
                                                runOnUiThread(new Runnable() {
                                                    @Override public void run() {
                                                        Toast.makeText(GuestActivity.this, r.ok ? "درخواست رد شد" : "⛔ انجام نشد", Toast.LENGTH_SHORT).show();
                                                        refresh();
                                                    }
                                                });
                                            }
                                        }).start();
                                    }
                                });
                                br.addView(ok);
                                br.addView(no);
                                row.addView(br);
                                srvBox.addView(row);
                            }
                        }
                    });
                }
            }).start();
        }

        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("هنوز مهمانی ثبت نشده");
            empty.setPadding(0, dp(20), 0, 0);
            empty.setGravity(android.view.Gravity.CENTER);
            listBox.addView(empty);
            return;
        }
        for (final String[] g : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(round(0xFFF7F9FA, dp(10)));
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            row.setLayoutParams(lp);

            boolean out = Long.parseLong(g[6]) > 0;
            String unit = g.length > 7 && g[7] != null ? g[7] : "";
            TextView name = new TextView(this);
            name.setText("👤 " + g[2] + (unit.isEmpty() ? "" : "  —  واحد " + Scheduler.fa(unit)) + (out ? "   (خارج شده)" : ""));
            name.setTypeface(null, Typeface.BOLD);
            name.setTextSize(15);
            row.addView(name);

            String nid = g[5];
            TextView info = new TextView(this);
            info.setText("🕐 ورود " + Scheduler.fa(Scheduler.hm(Long.parseLong(g[1])))
                    + (out ? " — خروج " + Scheduler.fa(Scheduler.hm(Long.parseLong(g[6]))) : "")
                    + "\n📞 " + Scheduler.fa(g[3] == null ? "" : g[3])
                    + (g[4] == null || g[4].isEmpty() ? "" : "  |  🚗 " + g[4])
                    + (nid == null || nid.isEmpty() ? "" : "  |  🆔 " + Scheduler.fa(nid)));
            info.setTextSize(13);
            info.setTextColor(0xFF455A64);
            row.addView(info);

            if (!out) {
                Button btnOut = new Button(this);
                btnOut.setText("🚪 ثبت خروج");
                btnOut.setTextColor(Color.WHITE);
                btnOut.setTextSize(13);
                btnOut.setBackgroundResource(R.drawable.btn_dark);
                LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(-2, -2);
                oLp.topMargin = dp(6);
                btnOut.setLayoutParams(oLp);
                btnOut.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        new AlertDialog.Builder(GuestActivity.this)
                                .setTitle("ثبت خروج")
                                .setMessage("خروج «" + g[2] + "» ثبت شود؟")
                                .setPositiveButton("ثبت", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        PatrolStore st = new PatrolStore(GuestActivity.this);
                                        st.markGuestOut(Long.parseLong(g[0]), PatrolStore.now());
                                        long ots = PatrolStore.now();
                                        st.enqK("NGHQ", "NGHQ|GUEST_OUT|" + ots + "|" + g[2] + "|" + g[5]);
                                        st.enqK("GUEST_OUT", "👤 مهمان: " + g[2]
                                                + "\n🕐 " + Scheduler.jalaliDate(ots) + " — " + Scheduler.fa(Scheduler.hm(ots)));
                                        st.close();
                                        Sync.kick(GuestActivity.this);   // ارسال فوری به مدیر
                                        refresh();
                                    }
                                })
                                .setNegativeButton("انصراف", null).show();
                    }
                });
                row.addView(btnOut);
            }
            listBox.addView(row);
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private android.graphics.drawable.Drawable round(int color, float radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** برچسب فیلد با ستارهٔ اجباری */
    private TextView label(String txt, int topDp) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextSize(12);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(0xFF455A64);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(topDp);
        t.setLayoutParams(lp);
        return t;
    }

    /** ظاهر پلاک: سفید با حاشیهٔ مشکی — مثل پلاک واقعی */
    private android.graphics.drawable.Drawable plateBg() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xFFFFFFFF);
        g.setCornerRadius(dp(6));
        g.setStroke(dp(2), 0xFF222222);
        return g;
    }

    /** خانهٔ عددی پلاک: مربع با گوشهٔ گرد */
    private EditText plateBox(EditText e, int max, int wDp) {
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        e.setTextSize(17);
        e.setTypeface(null, Typeface.BOLD);
        e.setGravity(android.view.Gravity.CENTER);
        e.setTextColor(0xFF111111);
        e.setBackground(round(0xFFF2F4F7, dp(4)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(wDp), dp(42));
        lp.setMargins(dp(4), 0, dp(4), 0);
        e.setLayoutParams(lp);
        return e;
    }

    /** تبدیل ارقام فارسی/عربی به لاتین */
    static String faToEn(String s) {
        if (s == null) return "";
        StringBuilder o = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '۰' && ch <= '۹') o.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') o.append((char) ('0' + (ch - '٠')));
            else o.append(ch);
        }
        return o.toString();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

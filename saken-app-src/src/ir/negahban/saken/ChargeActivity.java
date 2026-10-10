package ir.negahban.saken;

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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * پرداخت شارژ — بدون درگاه (بدون سرور!):
 * ساکن از کارت‌به‌کارت/کد SSF بانکی واریز میکند و شمارهٔ پیگیری/رسید را ثبت میکند؛
 * رسید در پایگاه‌دادهٔ محلی ذخیره و اطلاعش به مدیر و نگهبانی میرود تا تأیید کنند.
 * تأیید نهایی پرداخت با مدیر است (پیام «ثبت شد» یعنی در انتظار تأیید).
 */
public class ChargeActivity extends Activity {

    LinearLayout historyBox;
    TextView tvBalance;

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
        title.setText("💳 شارژ ساختمان");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        tvBalance = new TextView(this);
        tvBalance.setTextSize(14);
        tvBalance.setPadding(0, dp(12), 0, dp(4));
        root.addView(tvBalance);

        TextView hint = new TextView(this);
        hint.setText("مبلغ را به حساب ساختمان (که مدیر اعلام کرده) واریز کن — کارت‌به‌کارت یا سامانهٔ سه‌سانت — و شمارهٔ پیگیری واریز را اینجا ثبت کن. مدیر رسید را تأیید میکند.");
        hint.setTextSize(13);
        hint.setTextColor(0xFF607D8B);
        hint.setPadding(0, dp(8), 0, 0);
        root.addView(hint);

        final EditText eAmount = new EditText(this);
        eAmount.setHint("مبلغ (تومان، مثل 500000)");
        eAmount.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText eRef = new EditText(this);
        eRef.setHint("شمارهٔ پیگیری / رسید واریز");
        eRef.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText eMonth = new EditText(this);
        eMonth.setHint("بابت چه ماهی؟ (مثل مهر ۱۴۰۵)");

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF7F9FA);
        bg.setCornerRadius(dp(12));
        form.setBackground(bg);
        form.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(10);
        form.setLayoutParams(flp);
        form.addView(eAmount);
        form.addView(eRef);
        form.addView(eMonth);
        root.addView(form);

        Button pay = new Button(this);
        pay.setText("✅ ثبت پرداخت و اعلام به مدیر");
        pay.setTextColor(Color.WHITE);
        pay.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(52));
        plp.topMargin = dp(12);
        pay.setLayoutParams(plp);
        pay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pay(eAmount, eRef, eMonth); }
        });
        root.addView(pay);

        TextView h = new TextView(this);
        h.setText("📜 پرداخت‌های ثبت‌شدهٔ من");
        h.setTypeface(null, Typeface.BOLD);
        h.setPadding(0, dp(18), 0, dp(4));
        root.addView(h);

        historyBox = new LinearLayout(this);
        historyBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyBox);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void pay(final EditText eAmount, final EditText eRef, final EditText eMonth) {
        String amount = eAmount.getText().toString().trim();
        String ref = eRef.getText().toString().trim();
        String month = eMonth.getText().toString().trim();
        if (amount.isEmpty() || ref.isEmpty()) { toast("مبلغ و شمارهٔ پیگیری لازم است"); return; }

        long ts = PatrolStore.now();
        PatrolStore st = new PatrolStore(this);
        st.addCharge(ts, amount, ref, month, "PENDING");
        st.close();

        final String msg = "💳 پرداخت شارژ (در انتظار تأیید)\n👤 " + Me.name(this) + " — واحد " + Me.unit(this)
                + "\n💰 مبلغ: " + Scheduler.fa(toFaDigits(amount)) + " تومان"
                + "\n🔖 پیگیری: " + Scheduler.fa(ref)
                + (month.isEmpty() ? "" : "\n🗓 بابت: " + month)
                + "\n🕐 " + Scheduler.jalaliDate(ts) + " — " + Scheduler.fa(Scheduler.hm(ts));
        final String structured = "NGHQ|CHARGE|" + ts + "|" + Me.name(this) + "|" + Me.unit(this)
                + "|" + amount + "|" + ref + "|" + month;

        final String code = Hub.code(this);
        if (code.isEmpty()) {
            toast("اول کد ساختمان را در ⚙️ تنظیمات وارد کن");
            return;
        }
        final String fJson;
        try {
            org.json.JSONObject j = new org.json.JSONObject();
            j.put("ts", ts);
            j.put("n", Me.name(this));
            j.put("unit", Me.unit(this));
            j.put("amount", Long.parseLong(amount.replaceAll("[^۰-۹0-9]", "").replace("۰","0").replace("۱","1").replace("۲","2").replace("۳","3").replace("۴","4").replace("۵","5").replace("۶","6").replace("۷","7").replace("۸","8").replace("۹","9")));
            j.put("ref", ref);
            j.put("month", month);
            j.put("status", "PENDING");
            fJson = j.toString();
        } catch (Exception e) { toast("مبلغ نامناسب است"); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                Hub.Res r1 = Hub.dataPut(code, "charges", fJson, true);
                Hub.Res r2 = Hub.msg(code, "modir", Me.inbox(ChargeActivity.this), "CHARGE_PAY", fJson);
                final boolean fok = r1.ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (fok) {
                            eAmount.setText(""); eRef.setText(""); eMonth.setText("");
                            new AlertDialog.Builder(ChargeActivity.this)
                                    .setTitle("ثبت شد ✅")
                                    .setMessage("پرداختت ثبت شد و مدیر مطلع گردید.\nپس از تأیید رسید توسط مدیر، در وضعیت پرداخت‌هایت «تأییدشده» دیده میشود.")
                                    .setPositiveButton("باشه", null).show();
                            refresh();
                        } else {
                            toast("⛔ ثبت نشد — اینترنت را چک کن");
                        }
                    }
                });
            }
        }).start();
    }

    private String toFaDigits(String s) {
        return Scheduler.fa(s);
    }

    private void refresh() {
        final String code = Hub.code(this);
        if (code.isEmpty()) { renderLocal(); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                Hub.Res r = Hub.dataGet(code, "charges");
                final ArrayList<String[]> rows = new ArrayList<String[]>();
                if (r.ok) {
                    try {
                        org.json.JSONArray a = new org.json.JSONArray(r.payload);
                        for (int i = a.length() - 1; i >= 0; i--) {
                            org.json.JSONObject j = a.getJSONObject(i);
                            String n = j.optString("n", ""), u = j.optString("unit", "");
                            if (!n.equals(Me.name(ChargeActivity.this)) && !u.equals(Me.unit(ChargeActivity.this))) continue;
                            rows.add(new String[]{String.valueOf(j.optLong("ts", 0)), String.valueOf(j.optLong("amount", 0)),
                                    j.optString("ref", ""), j.optString("month", ""), j.optString("status", "PENDING")});
                        }
                    } catch (Exception ignored) { }
                }
                runOnUiThread(new Runnable() { @Override public void run() { render(rows); } });
            }
        }).start();
    }

    private void renderLocal() {
        PatrolStore st = new PatrolStore(this);
        ArrayList<String[]> rows = st.myCharges();
        st.close();
        render(rows);
    }

    private void render(ArrayList<String[]> rows) {
        int pending = 0;
        for (String[] r : rows) if ("PENDING".equals(r[4])) pending++;
        tvBalance.setText("📄 " + Scheduler.fa(String.valueOf(rows.size())) + " پرداخت ثبت‌شده"
                + (pending > 0 ? " — " + Scheduler.fa(String.valueOf(pending)) + " مورد در انتظار تأیید مدیر" : " — همه تأییدشده ✅"));

        historyBox.removeAllViews();
        if (rows.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("هنوز پرداختی ثبت نکرده‌ای");
            e.setPadding(0, dp(14), 0, 0);
            e.setTextColor(0xFF90A4AE);
            historyBox.addView(e);
            return;
        }
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable g = new GradientDrawable();
            g.setColor("OK".equals(r[4]) ? 0xFFE8F8F5 : 0xFFFFF8E1);
            g.setCornerRadius(dp(10));
            row.setBackground(g);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            row.setLayoutParams(lp);

            TextView head = new TextView(this);
            head.setText("💳 " + Scheduler.fa(r[1]) + " تومان " + ("OK".equals(r[4]) ? "✅ تأییدشده" : "🕐 در انتظار تأیید"));
            head.setTypeface(null, Typeface.BOLD);
            head.setTextSize(15);
            row.addView(head);

            TextView info = new TextView(this);
            info.setText("🔖 پیگیری: " + Scheduler.fa(r[2])
                    + (r[3].isEmpty() ? "" : "  |  🗓 " + Scheduler.fa(r[3]))
                    + "\n🕐 " + Scheduler.jalaliDate(Long.parseLong(r[0])));
            info.setTextSize(13);
            info.setTextColor(0xFF455A64);
            row.addView(info);
            historyBox.addView(row);
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

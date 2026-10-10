package ir.negahban.modir;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

/** صندوق ساختمان: موجودی، پرداخت‌ها، تأیید شارژ، بدهکارهای ماه */
public class ChargesActivity extends Activity {

    LinearLayout root, list;
    TextView tvFund, tvSub, tvDebt;
    ArrayList<JSONObject> charges = new ArrayList<JSONObject>();
    ArrayList<JSONObject> roster = new ArrayList<JSONObject>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.WHITE);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(root);
        setContentView(sc);

        title("💳 صندوق ساختمان");

        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF0F3D56);
        gb.setCornerRadius(dp(18));
        LinearLayout fund = new LinearLayout(this);
        fund.setOrientation(LinearLayout.VERTICAL);
        fund.setBackground(gb);
        fund.setPadding(dp(18), dp(18), dp(18), dp(18));
        tvFund = new TextView(this);
        tvFund.setText("💰 …");
        tvFund.setTextColor(Color.WHITE);
        tvFund.setTextSize(24);
        tvFund.setTypeface(null, Typeface.BOLD);
        tvFund.setGravity(Gravity.CENTER);
        fund.addView(tvFund);
        tvSub = new TextView(this);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setTextColor(0xB3FFFFFF);
        tvSub.setTextSize(13);
        tvSub.setPadding(0, dp(6), 0, 0);
        fund.addView(tvSub);
        root.addView(fund);

        root.addView(section("💳 پرداخت‌های ثبت‌شده"));
        root.addView(hint2("پرداخت‌هایی که ساکن‌ها ثبت می‌کنند اول «در انتظار تأیید» است؛ با تأیید تو به موجودی صندوق اضافه می‌شود."));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        root.addView(section("⛔ بدهکارهای این ماه"));
        tvDebt = new TextView(this);
        tvDebt.setText("…");
        tvDebt.setTextSize(14);
        tvDebt.setPadding(dp(4), dp(2), dp(4), dp(8));
        root.addView(tvDebt);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    void reload() {
        final String code = Cfg.p(this).getString("bldgCode", "").trim();
        if (code.isEmpty()) {
            tvFund.setText("💰 —");
            tvSub.setText("اول کد ساختمان را در ⚙️ تنظیمات ثبت کن");
            return;
        }
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
                ArrayList<JSONObject> ro = new ArrayList<JSONObject>();
                Cloud.Res r2 = Cloud.hubDataGet(code, "roster");
                if (r2.ok) {
                    try {
                        JSONArray a = new JSONArray(r2.payload);
                        for (int i = 0; i < a.length(); i++) ro.add(a.getJSONObject(i));
                    } catch (Exception ignored) { }
                }
                charges = ch;
                roster = ro;
                runOnUiThread(new Runnable() { @Override public void run() { render(); } });
            }
        }).start();
    }

    void render() {
        long fund = 0;
        int okCount = 0, pendCount = 0;
        int[] ym = Jalali.fromMillis(PatrolStore.now());
        String month = ym[0] + (ym[1] < 10 ? "/0" : "/") + ym[1];
        HashSet<String> paid = new HashSet<String>();
        for (JSONObject j : charges) {
            if ("OK".equals(j.optString("status"))) {
                fund += j.optLong("amount", 0);
                okCount++;
                if (month.equals(j.optString("month", ""))) paid.add(j.optString("n", "").trim());
            } else pendCount++;
        }
        tvFund.setText("💰 " + money(fund) + " تومان");
        tvSub.setText(Scheduler.fa(String.valueOf(okCount)) + " پرداخت تأییدشده"
                + (pendCount > 0 ? " • " + Scheduler.fa(String.valueOf(pendCount)) + " در انتظار تأیید" : ""));

        list.removeAllViews();
        boolean any = false;
        for (int i = 0; i < charges.size(); i++) {
            final JSONObject j = charges.get(i);
            final boolean ok = "OK".equals(j.optString("status"));
            GradientDrawable g = new GradientDrawable();
            g.setColor(ok ? 0xFFF1F8F5 : 0xFFFFFBEB);
            g.setCornerRadius(dp(12));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(g);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            row.setLayoutParams(lp);

            TextView t1 = new TextView(this);
            t1.setText("👤 " + j.optString("n", "—") + " (واحد " + Scheduler.fa(j.optString("unit", "—")) + ")"
                    + (ok ? "  ✅" : "  ⏳"));
            t1.setTextSize(15);
            t1.setTypeface(null, Typeface.BOLD);
            row.addView(t1);
            TextView t2 = new TextView(this);
            t2.setText(money(j.optLong("amount", 0)) + " تومان • ماه " + Scheduler.fa(j.optString("month", "—"))
                    + " • رسید: " + Scheduler.fa(j.optString("ref", "—")));
            t2.setTextSize(13);
            t2.setTextColor(0xFF455A64);
            row.addView(t2);

            if (!ok) {
                Button okBtn = new Button(this);
                okBtn.setText("✅ تأیید پرداخت");
                okBtn.setAllCaps(false);
                okBtn.setTextColor(Color.WHITE);
                okBtn.setBackgroundResource(R.drawable.btn_primary);
                okBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { confirm(j); }
                });
                row.addView(okBtn);
            }
            list.addView(row);
            any = true;
        }
        if (!any) {
            TextView e = new TextView(this);
            e.setText("هنوز پرداختی ثبت نشده است");
            e.setTextColor(0xFF90A4AE);
            e.setPadding(dp(4), dp(10), dp(4), dp(4));
            list.addView(e);
        }

        StringBuilder db = new StringBuilder();
        int n = 0;
        for (JSONObject r : roster) {
            String nm = r.optString("name", "").trim();
            if (nm.isEmpty() || paid.contains(nm)) continue;
            if (db.length() > 0) db.append("، ");
            db.append(nm).append(" (واحد ").append(Scheduler.fa(r.optString("unit", "—"))).append(")");
            n++;
        }
        tvDebt.setText(n == 0 ? "🎉 همه شارژ این ماه را داده‌اند"
                : Scheduler.fa(String.valueOf(n)) + " نفر: " + db);
    }

    void confirm(final JSONObject j) {
        toastSmall("در حال ثبت تأیید…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    j.put("status", "OK");
                    j.put("okTs", PatrolStore.now());
                    JSONArray a = new JSONArray();
                    for (JSONObject x : charges) a.put(x);
                    final Cloud.Res r = Cloud.hubDataPut(Cfg.p(ChargesActivity.this).getString("bldgCode", "").trim(),
                            "charges", a.toString(), false);
                    runOnUiThread(new Runnable() { @Override public void run() {
                        if (r.ok) { toastSmall("✅ تأیید شد"); reload(); }
                        else toastSmall("⛔ " + r.err);
                    }});
                } catch (Exception e) {
                    runOnUiThread(new Runnable() { @Override public void run() { toastSmall("خطا"); }});
                }
            }
        }).start();
    }

    // ---------- helpers ----------
    private TextView hint2(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(0xFF607D8B);
        t.setPadding(dp(4), 0, dp(4), dp(8));
        return t;
    }

    private TextView section(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(0xFF0F3D56);
        tv.setPadding(0, dp(18), 0, dp(6));
        root.addView(tv);
        return tv;
    }

    private void title(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(20);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(0xFF0F3D56);
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(tv);
    }

    static String money(long v) {
        String s = String.format(java.util.Locale.US, "%,d", v).replace(',', '،');
        return Scheduler.fa(s);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private void toastSmall(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}

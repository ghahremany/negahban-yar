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
import android.widget.LinearLayout;
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
        final EditText ePlate = new EditText(this);
        final EditText eNid = new EditText(this);
        eName.setHint("نام و نام خانوادگی مهمان");
        eMobile.setHint("شمارهٔ همراه (مثل 0912…)");
        eMobile.setInputType(InputType.TYPE_CLASS_PHONE);
        ePlate.setHint("شمارهٔ خودرو (پلاک) — اختیاری");
        eNid.setHint("کد ملی (۱۰ رقم) — اختیاری");
        eNid.setInputType(InputType.TYPE_CLASS_NUMBER);
        eNid.setInputType(InputType.TYPE_CLASS_NUMBER);

        int pad = dp(10);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setBackground(round(0xFFF7F9FA, dp(12)));
        form.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-1, -2);
        fLp.topMargin = dp(12);
        form.setLayoutParams(fLp);
        form.addView(eName);
        form.addView(eMobile);
        form.addView(ePlate);
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
                String name = eName.getText().toString().trim();
                String mobile = eMobile.getText().toString().trim();
                if (name.isEmpty()) { toast("نام مهمان لازم است"); return; }
                String nid = eNid.getText().toString().trim();
                if (!nid.isEmpty() && nid.length() != 10) { toast("کد ملی باید ۱۰ رقم باشد"); return; }
                long gts = PatrolStore.now();
                PatrolStore st = new PatrolStore(GuestActivity.this);
                st.addGuestTs(gts, name, mobile,
                        ePlate.getText().toString().trim(), nid, 0);
                // خبر ماشینی برای مدیر یار
                st.enq("NGHQ|GUEST_IN|" + gts + "|" + name + "|" + mobile + "|"
                        + ePlate.getText().toString().trim() + "|" + nid);
                st.close();
                eName.setText(""); eMobile.setText(""); ePlate.setText(""); eNid.setText("");
                toast("ورود ثبت شد ✅");
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
            TextView name = new TextView(this);
            name.setText("👤 " + g[2] + (out ? "   (خارج شده)" : ""));
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
                                        st.close();
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

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

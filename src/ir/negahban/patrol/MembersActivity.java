package ir.negahban.patrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** تأیید/رد درخواست‌های عضویت که از طریق ربات بله ثبت شده‌اند — PIN‌دار */
public class MembersActivity extends Activity {

    LinearLayout listPending, listApproved;
    TextView tvSummary;

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
        title.setText("✅ تأیید اعضا");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("درخواست‌های عضویتی که ساکنین از طریق ربات بله فرستاده‌اند. با تأیید، ساکن از طریق ربات به بسته‌هایش دسترسی پیدا می‌کند.");
        hint.setTextSize(13);
        hint.setTextColor(0xFF607D8B);
        hint.setPadding(0, dp(10), 0, 0);
        root.addView(hint);

        tvSummary = new TextView(this);
        tvSummary.setTextSize(14);
        tvSummary.setTypeface(null, Typeface.BOLD);
        tvSummary.setPadding(0, dp(10), 0, 0);
        root.addView(tvSummary);

        TextView h1 = new TextView(this);
        h1.setText("🕐 در انتظار تأیید");
        h1.setTypeface(null, Typeface.BOLD);
        h1.setPadding(0, dp(14), 0, dp(4));
        root.addView(h1);

        listPending = new LinearLayout(this);
        listPending.setOrientation(LinearLayout.VERTICAL);
        root.addView(listPending);

        TextView h2 = new TextView(this);
        h2.setText("✅ تأییدشده‌ها");
        h2.setTypeface(null, Typeface.BOLD);
        h2.setPadding(0, dp(16), 0, dp(4));
        root.addView(h2);

        listApproved = new LinearLayout(this);
        listApproved.setOrientation(LinearLayout.VERTICAL);
        root.addView(listApproved);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        PatrolStore st = new PatrolStore(this);
        final ArrayList<String[]> pend = st.reqsByStatus("PENDING", 100);
        final ArrayList<String[]> appr = st.reqsByStatus("APPROVED", 50);
        int resTotal = st.resCount();
        st.close();

        tvSummary.setText("تأییدشده: " + Scheduler.fa(String.valueOf(appr.size()))
                + "  |  در انتظار: " + Scheduler.fa(String.valueOf(pend.size()))
                + "  |  کل ساکنین ثبت‌شده: " + Scheduler.fa(String.valueOf(resTotal)));

        listPending.removeAllViews();
        if (pend.isEmpty()) {
            listPending.addView(emptyText("درخواستی در انتظار نیست"));
        }
        for (final String[] r : pend) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(round(0xFFFFF8E1, dp(10)));
            card.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            card.setLayoutParams(lp);

            TextView head = new TextView(this);
            head.setText("👤 " + r[3]);
            head.setTypeface(null, Typeface.BOLD);
            card.addView(head);

            TextView info = new TextView(this);
            info.setText("📱 " + Scheduler.fa(r[4]) + "  |  🏠 " + r[5]
                    + "\n🕐 درخواست: " + Scheduler.jalaliDate(Long.parseLong(r[1])));
            info.setTextSize(13);
            info.setTextColor(0xFF455A64);
            card.addView(info);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = dp(6);
            row.setLayoutParams(rl);

            Button ok = new Button(this);
            ok.setText("✅ تأیید");
            ok.setTextColor(Color.WHITE);
            ok.setBackgroundResource(R.drawable.btn_primary);
            LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(0, -2, 1f);
            oLp.setMargins(0, 0, dp(4), 0);
            ok.setLayoutParams(oLp);
            ok.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { decide(Long.parseLong(r[0]), Long.parseLong(r[2]), r[3], true); }
            });
            row.addView(ok);

            Button no = new Button(this);
            no.setText("✖️ رد");
            no.setTextColor(Color.WHITE);
            no.setBackgroundResource(R.drawable.btn_dark);
            LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, -2, 1f);
            nLp.setMargins(dp(4), 0, 0, 0);
            no.setLayoutParams(nLp);
            no.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { decide(Long.parseLong(r[0]), Long.parseLong(r[2]), r[3], false); }
            });
            row.addView(no);

            card.addView(row);
            listPending.addView(card);
        }

        listApproved.removeAllViews();
        if (appr.isEmpty()) {
            listApproved.addView(emptyText("هنوز عضوی از طریق ربات تأیید نشده"));
        }
        for (String[] r : appr) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(round(0xFFE8F8F5, dp(10)));
            card.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            card.setLayoutParams(lp);
            TextView head = new TextView(this);
            head.setText("✅ " + r[3] + " — " + Scheduler.fa(r[4]) + "  |  🏠 " + r[5]);
            head.setTextSize(14);
            card.addView(head);
            listApproved.addView(card);
        }
    }

    private View emptyText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, dp(14), 0, 0);
        t.setTextColor(0xFF90A4AE);
        return t;
    }

    private void decide(final long reqId, final long chatId, final String name, final boolean approve) {
        PatrolStore st = new PatrolStore(this);
        ArrayList<String[]> pend = st.reqsByStatus("PENDING", 200);
        String mobile = "", unit = "";
        for (String[] r : pend) if (Long.parseLong(r[0]) == reqId) { mobile = r[4]; unit = r[5]; }
        st.close();

        if (approve) {
            PatrolStore db = new PatrolStore(this);
            long resId = db.addRes(name, mobile, "", "", unit);
            // اتصال chat بله
            android.content.ContentValues dummy = null;
            db.setReqStatus(reqId, "APPROVED");
            db.close();
            linkChat(resId, chatId);
            String tk = Cfg.token(this);
            if (!tk.isEmpty()) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        Bale.send(Cfg.token(MembersActivity.this), chatId,
                                "🎉 تبریک " + name + "!\nمدیر عضویتت را تأیید کرد.\nاز حالا با نوشتن «بسته» می‌توانی بسته‌های در انتظارت را ببینی.");
                    }
                }).start();
            }
            Toast.makeText(this, "تأیید شد ✅ — عضو به ساکنین اضافه شد", Toast.LENGTH_SHORT).show();
        } else {
            PatrolStore db = new PatrolStore(this);
            db.setReqStatus(reqId, "REJECTED");
            db.close();
            String tk = Cfg.token(this);
            if (!tk.isEmpty()) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        Bale.send(Cfg.token(MembersActivity.this), chatId,
                                "متأسفانه درخواست عضویتت تأیید نشد. برای پیگیری با مدیریت ساختمان تماس بگیر.");
                    }
                }).start();
            }
            Toast.makeText(this, "رد شد", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void linkChat(long resId, long chatId) {
        try {
            PatrolStore db = new PatrolStore(this);
            android.database.sqlite.SQLiteDatabase w = db.getWritableDatabase();
            android.content.ContentValues v = new android.content.ContentValues();
            v.put("chatId", chatId);
            w.update("resident", v, "id=?", new String[]{String.valueOf(resId)});
            w.close();
            db.close();
        } catch (Exception ignored) {}
    }

    private android.graphics.drawable.Drawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

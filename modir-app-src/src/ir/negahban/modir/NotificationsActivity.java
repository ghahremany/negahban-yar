package ir.negahban.modir;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** 🔔 فهرست اعلان‌های مدیر (عضویت، کلید، مهمان، پرداخت، اطلاعیه…) — باز کردن = خوانده‌شدن */
public class NotificationsActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("🔔 اعلان‌ها");
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        final String code = Cfg.p(this).getString("bldgCode", "").trim();
        TextView codeLine = new TextView(this);
        codeLine.setText(code.isEmpty()
                ? "⛔ وصل نیستی — اول ساختمان را در ⚙️ تنظیمات ثبت کن"
                : "📡 متصل به ساختمان: " + Scheduler.fa(code));
        codeLine.setTextSize(12.5f);
        codeLine.setTextColor(code.isEmpty() ? 0xFFB71C1C : 0xFF455A64);
        codeLine.setPadding(dp(14), dp(8), dp(14), 0);
        root.addView(codeLine);

        Button check = new Button(this);
        check.setText("🔄 بررسی پیام‌های تازه");
        check.setAllCaps(false);
        check.setTextColor(Color.WHITE);
        check.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(-1, dp(44));
        chlp.setMargins(dp(14), dp(10), dp(14), 0);
        check.setLayoutParams(chlp);
        check.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (code.isEmpty()) { Toast.makeText(NotificationsActivity.this, "اول ساختمان را ثبت کن", Toast.LENGTH_LONG).show(); return; }
                v.setEnabled(false);
                ((Button) v).setText("در حال بررسی…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        long after = Cfg.p(NotificationsActivity.this).getLong("hubAfter", 0);
                        java.util.ArrayList<Cloud.MsgItem> items = Cloud.hubMsgs(code, "modir", after);
                        int added = 0;
                        PatrolStore st = new PatrolStore(NotificationsActivity.this);
                        try {
                            for (Cloud.MsgItem it : items) {
                                String txt = HubService.notifText(it.kind, it.payload);
                                if (txt != null && st.addNotif(it.id, it.ts, it.kind, txt)) added++;
                                st.addMsg(it.ts, it.src, it.kind, it.payload);
                            }
                        } finally { st.close(); }
                        if (Cloud.sLastId > after)
                            Cfg.p(NotificationsActivity.this).edit().putLong("hubAfter", Cloud.sLastId).apply();
                        final int fAdded = added;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(NotificationsActivity.this,
                                        fAdded > 0 ? "🔔 " + Scheduler.fa(String.valueOf(fAdded)) + " پیام تازه آمد" : "پیام تازه‌ای نبود",
                                        Toast.LENGTH_SHORT).show();
                                recreate();
                            }
                        });
                    }
                }).start();
            }
        });
        root.addView(check);

        Button readAll = new Button(this);
        readAll.setText("✓ خواندن همه");
        readAll.setAllCaps(false);
        readAll.setTextColor(Color.WHITE);
        readAll.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(42));
        rlp.setMargins(dp(14), dp(6), dp(14), 0);
        readAll.setLayoutParams(rlp);
        readAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                PatrolStore st = new PatrolStore(NotificationsActivity.this);
                try { st.markNotifsSeen(); } finally { st.close(); }
                finish();
            }
        });
        root.addView(readAll);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(8), dp(14), dp(30));
        root.addView(list);

        PatrolStore st = new PatrolStore(this);
        java.util.ArrayList<String[]> rows;
        try { rows = st.notifs(100); } finally { st.close(); }

        if (rows.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("فعلاً اعلانی نیست\nبا «بررسی پیام‌های تازه» چک کن");
            e.setPadding(0, dp(30), 0, 0);
            e.setGravity(android.view.Gravity.CENTER);
            e.setTextColor(0xFF90A4AE);
            e.setTextSize(14);
            list.addView(e);
        }

        for (String[] r : rows) {
            boolean unseen = "0".equals(r[4]);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(unseen ? 0xFFFFF8E1 : 0xFFF7F9FA);
            bg.setCornerRadius(dp(10));
            card.setBackground(bg);
            card.setPadding(dp(12), dp(9), dp(12), dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(7);
            card.setLayoutParams(lp);

            TextView txt = new TextView(this);
            txt.setText(r[3]);
            txt.setTextSize(13.5f);
            txt.setTextColor(unseen ? 0xFF212121 : 0xFF546E7A);
            if (unseen) txt.setTypeface(null, Typeface.BOLD);
            card.addView(txt);

            TextView t = new TextView(this);
            t.setText("🕓 " + Scheduler.fa(Scheduler.hm(Long.parseLong(r[1]))) + " — " + Scheduler.fa(Scheduler.jalaliDate(Long.parseLong(r[1]))));
            t.setTextSize(11);
            t.setTextColor(0xFF90A4AE);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.topMargin = dp(3);
            t.setLayoutParams(tlp);
            card.addView(t);

            list.addView(card);
        }

        PatrolStore st2 = new PatrolStore(this);
        try { st2.markNotifsSeen(); } finally { st2.close(); }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}

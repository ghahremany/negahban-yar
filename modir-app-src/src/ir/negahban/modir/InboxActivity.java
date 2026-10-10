package ir.negahban.modir;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** صندوق پیام‌های ساختمان: درخواست عضویت (تأیید/رد)، مهمان، شارژ، پیام‌ها و گزارش‌ها */
public class InboxActivity extends Activity {

    LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFFF4F7F9);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(root);
        setContentView(sc);

        TextView title = new TextView(this);
        title.setText("📥 صندوق پیام‌ها");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        Button newsBtn = new Button(this);
        newsBtn.setText("📢 ارسال خبر به اهالی");
        newsBtn.setAllCaps(false);
        newsBtn.setTextColor(Color.WHITE);
        newsBtn.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.setMargins(dp(14), dp(10), dp(14), 0);
        newsBtn.setLayoutParams(nlp);
        newsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { newsComposer(); }
        });
        root.addView(newsBtn);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(14), dp(14), dp(40));
        root.addView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    void render() {
        list.removeAllViews();
        PatrolStore st = new PatrolStore(this);
        ArrayList<String[]> rows;
        try {
            rows = st.msgs(200);
        } finally {
            st.close();
        }
        if (rows.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("صندوق خالی است — خبری تازه نیست");
            e.setTextColor(0xFF90A4AE);
            e.setPadding(dp(4), dp(24), dp(4), dp(4));
            e.setGravity(android.view.Gravity.CENTER);
            list.addView(e);
            return;
        }
        for (final String[] r : rows) {
            final long id = Long.parseLong(r[0]);
            long ts = Long.parseLong(r[1]);
            String src = r[2], kind = r[3], payload = r[4], seen = r[5];
            if ("NGHQ".equals(kind)) continue;   // ردیف همگام‌سازی خام — نسخهٔ خوانا جدا می‌آید
            boolean isNew = "0".equals(seen);
            boolean handled = "2".equals(seen);

            String icon = "💬", head = "پیام";
            String name = "", unit = "", mobile = "", line2 = "", extra = "";
            boolean nghq = payload != null && payload.startsWith("NGHQ|");
            if (nghq) {
                // فرمت قدیمی نگهبان: NGHQ|TYPE|ts|... — همان گزارش نگهبان است ولی عنوان درست دارد
                String[] pp = payload.split("\\|");
                String t = pp.length > 1 ? pp[1] : "";
                if ("GUEST_IN".equals(t)) {
                    icon = "🚶"; head = "ورود مهمان";
                    name = pp.length > 3 ? pp[3] : "";
                    line2 = (pp.length > 4 && !pp[4].isEmpty() ? "همراه " + Scheduler.fa(pp[4]) : "")
                            + (pp.length > 5 && !pp[5].isEmpty() ? (line2.isEmpty() ? "" : " • ") + "پلاک " + Scheduler.fa(pp[5]) : "");
                } else if ("GUEST_OUT".equals(t)) {
                    icon = "🚪"; head = "خروج مهمان";
                    name = pp.length > 3 ? pp[3] : "";
                } else if ("PKG".equals(t)) {
                    icon = "📦"; head = "دریافت بسته";
                    name = pp.length > 5 ? pp[5] : "";
                    line2 = (pp.length > 7 && !pp[7].isEmpty() ? "واحد " + Scheduler.fa(pp[7]) : "");
                    extra = (pp.length > 3 && !pp[3].isEmpty() ? "📦 نوع: " + pp[3] : "")
                            + (pp.length > 4 && !pp[4].isEmpty() ? (extra.isEmpty() ? "" : " • ") + "🔖 بارکد: " + Scheduler.fa(pp[4]) : "");
                } else {
                    icon = "🔗"; head = "همگام‌سازی";
                }
            } else
            try {
                JSONObject j = new JSONObject(payload);
                if ("REG_REQ".equals(kind)) {
                    icon = "📝";
                    head = "درخواست عضویت";
                    name = j.optString("name", ""); unit = j.optString("unit", ""); mobile = j.optString("mobile", "");
                    String rl = "owner".equals(j.optString("role", "")) ? "مالک" : "مستأجر";
                    line2 = "واحد " + Scheduler.fa(unit) + " • " + rl + " • همراه " + Scheduler.fa(mobile);
                    StringBuilder ex = new StringBuilder();
                    if (!j.optString("car", "").isEmpty()) ex.append("🚗 ").append(j.optString("car", ""));
                    if (!j.optString("plate", "").isEmpty()) ex.append(ex.length() > 0 ? " — " : "").append("پلاک ").append(Scheduler.fa(j.optString("plate", "")));
                    if (!j.optString("park", "").isEmpty()) ex.append(ex.length() > 0 ? " — " : "").append("پارکینگ ").append(Scheduler.fa(j.optString("park", "")));
                    if (j.optInt("nres", 0) > 0) ex.append(ex.length() > 0 ? " — " : "").append(Scheduler.fa(String.valueOf(j.optInt("nres", 0)))).append(" نفر");
                    extra = ex.toString();
                } else if ("GU_GUEST_REQ".equals(kind)) {
                    icon = "🚶";
                    head = "درخواست ورود مهمان (ساکن)";
                    name = j.optString("guest", "");
                    line2 = (j.optString("when", "").isEmpty() ? "" : "زمان: " + j.optString("when", "") + " • ")
                            + "همراه " + Scheduler.fa(j.optString("mobile", ""))
                            + (j.optString("plate", "").isEmpty() ? "" : " • پلاک " + Scheduler.fa(j.optString("plate", "")));
                } else if ("GUEST_REQ".equals(kind)) {
                    icon = "🚶";
                    head = "درخواست حضور مهمان";
                    name = j.optString("guest", j.optString("name", ""));
                    line2 = "میزبان: " + j.optString("host", "—") + " (واحد " + Scheduler.fa(j.optString("unit", "—")) + ")"
                            + (j.optString("when", "").isEmpty() ? "" : " • " + j.optString("when", ""));
                } else if ("CHARGE_PAY".equals(kind)) {
                    icon = "💳";
                    head = "پرداخت شارژ ثبت شد";
                    name = j.optString("n", j.optString("name", ""));
                    line2 = j.optString("amount", "") + " تومان • ماه " + j.optString("month", "") + " • رسید " + Scheduler.fa(j.optString("ref", ""));
                } else if ("ROOF_KEY_REQ".equals(kind)) {
                    icon = "🔑";
                    head = "درخواست کلید پشت‌بام";
                    name = j.optString("name", "");
                    line2 = "واحد " + Scheduler.fa(j.optString("unit", "—"))
                            + (j.optString("mobile", "").isEmpty() ? "" : " • همراه " + Scheduler.fa(j.optString("mobile", "")));
                } else if ("MOVE_REQ".equals(kind)) {
                    icon = "🚚";
                    head = "درخواست اسباب‌کشی";
                    name = j.optString("name", "");
                    line2 = "واحد " + Scheduler.fa(j.optString("unit", "—"))
                            + (j.optString("mobile", "").isEmpty() ? "" : " • همراه " + Scheduler.fa(j.optString("mobile", "")));
                } else if ("ELEV_REQ".equals(kind)) {
                    icon = "🛗";
                    head = "درخواست استفاده از آسانسور";
                    name = j.optString("name", "");
                    line2 = "واحد " + Scheduler.fa(j.optString("unit", "—"))
                            + (j.optString("mobile", "").isEmpty() ? "" : " • همراه " + Scheduler.fa(j.optString("mobile", "")));
                } else if ("REPORT".equals(kind)) {
                    icon = "🌙";
                    head = "گزارش نگهبان";
                }
            } catch (Exception e2) {
                // payload متن خام است
                if ("REPORT".equals(kind) && !nghq) { icon = "🌙"; head = "گزارش نگهبان"; }
                else if ("GUEST_IN".equals(kind)) { icon = "🚶"; head = "ورود مهمان"; }
                else if ("GUEST_OUT".equals(kind)) { icon = "🚪"; head = "خروج مهمان"; }
                else if ("PKG_IN".equals(kind)) { icon = "📦"; head = "دریافت بسته"; }
                else if ("PKG_OUT".equals(kind)) { icon = "✅"; head = "تحویل بسته"; }
                else if ("NGHQ".equals(kind)) { icon = "🔗"; head = "همگام‌سازی"; }
            }

            GradientDrawable g = new GradientDrawable();
            g.setColor(isNew ? 0xFFFFFFFF : 0xFFEDF1F4);
            g.setCornerRadius(dp(12));
            if (isNew) g.setStroke(dp(1), 0xFF12B5A5);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(g);
            card.setPadding(dp(12), dp(10), dp(12), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            card.setLayoutParams(lp);

            TextView t1 = new TextView(this);
            t1.setText(icon + " " + head + (name.isEmpty() ? "" : ": " + name) + (isNew ? "  🔴" : ""));
            t1.setTextSize(15);
            t1.setTypeface(null, Typeface.BOLD);
            t1.setTextColor(0xFF212121);
            card.addView(t1);
            if (!line2.isEmpty()) {
                TextView t2 = new TextView(this);
                t2.setText(line2);
                t2.setTextSize(13);
                t2.setTextColor(0xFF455A64);
                card.addView(t2);
            }
            if (!extra.isEmpty()) {
                TextView t3 = new TextView(this);
                t3.setText(extra);
                t3.setTextSize(12.5f);
                t3.setTextColor(0xFF607D8B);
                card.addView(t3);
            }
            if (!nghq && ("REPORT".equals(kind) || "MSG".equals(kind) || "GUEST_IN".equals(kind)
                    || "GUEST_OUT".equals(kind) || "PKG_IN".equals(kind) || "PKG_OUT".equals(kind))) {
                TextView body = new TextView(this);
                String txt = payload;
                if (txt.length() > 400) txt = txt.substring(0, 400) + "…";
                body.setText(txt);
                body.setTextSize(13);
                body.setTextColor(0xFF37474F);
                body.setPadding(0, dp(6), 0, 0);
                card.addView(body);
            }
            TextView tt = new TextView(this);
            tt.setText(Scheduler.jalaliDate(ts) + " — " + clock(ts));
            tt.setTextSize(11);
            tt.setTextColor(0xFF90A4AE);
            tt.setPadding(0, dp(6), 0, 0);
            card.addView(tt);

            if ("ROOF_KEY_REQ".equals(kind) && !handled) {
                LinearLayout br = new LinearLayout(this);
                br.setOrientation(LinearLayout.HORIZONTAL);
                Button ok2 = new Button(this);
                ok2.setText("✅ تأیید کلید");
                ok2.setAllCaps(false);
                ok2.setTextColor(Color.WHITE);
                ok2.setBackgroundResource(R.drawable.btn_primary);
                LinearLayout.LayoutParams q1 = new LinearLayout.LayoutParams(0, -2, 1);
                q1.setMargins(0, dp(8), dp(4), 0);
                ok2.setLayoutParams(q1);
                ok2.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { decideRoof(id, true); }
                });
                Button no2 = new Button(this);
                no2.setText("✖️ رد");
                no2.setAllCaps(false);
                no2.setTextColor(Color.WHITE);
                no2.setBackgroundResource(R.drawable.btn_dark);
                LinearLayout.LayoutParams q2 = new LinearLayout.LayoutParams(0, -2, 1);
                q2.setMargins(dp(4), dp(8), 0, 0);
                no2.setLayoutParams(q2);
                no2.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { decideRoof(id, false); }
                });
                br.addView(ok2);
                br.addView(no2);
                card.addView(br);
            }

            if (("MOVE_REQ".equals(kind) || "ELEV_REQ".equals(kind)) && !handled) {
                LinearLayout br = new LinearLayout(this);
                br.setOrientation(LinearLayout.HORIZONTAL);
                boolean isMove = "MOVE_REQ".equals(kind);
                Button ok3 = new Button(this);
                ok3.setText(isMove ? "✅ تأیید اسباب‌کشی" : "✅ تأیید آسانسور");
                ok3.setAllCaps(false);
                ok3.setTextColor(Color.WHITE);
                ok3.setBackgroundResource(R.drawable.btn_primary);
                LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(0, -2, 1);
                r1.setMargins(0, dp(8), dp(4), 0);
                ok3.setLayoutParams(r1);
                ok3.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { decideSvc(id, isMove ? "MOVE" : "ELEV", true); }
                });
                Button no3 = new Button(this);
                no3.setText("✖️ رد");
                no3.setAllCaps(false);
                no3.setTextColor(Color.WHITE);
                no3.setBackgroundResource(R.drawable.btn_dark);
                LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(0, -2, 1);
                r2.setMargins(dp(4), dp(8), 0, 0);
                no3.setLayoutParams(r2);
                no3.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { decideSvc(id, isMove ? "MOVE" : "ELEV", false); }
                });
                br.addView(ok3);
                br.addView(no3);
                card.addView(br);
            }

            if ("GU_GUEST_REQ".equals(kind) && !handled) {
                LinearLayout br = new LinearLayout(this);
                br.setOrientation(LinearLayout.HORIZONTAL);
                Button ok = new Button(this);
                ok.setText("✅ تأیید ورود مهمان");
                ok.setAllCaps(false);
                ok.setTextColor(Color.WHITE);
                ok.setBackgroundResource(R.drawable.btn_primary);
                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1);
                p1.setMargins(0, dp(8), dp(4), 0);
                ok.setLayoutParams(p1);
                ok.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { decideGuest(id, true); }
                });
                Button no = new Button(this);
                no.setText("✖️ رد");
                no.setAllCaps(false);
                no.setTextColor(Color.WHITE);
                no.setBackgroundResource(R.drawable.btn_dark);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -2, 1);
                p2.setMargins(dp(4), dp(8), 0, 0);
                no.setLayoutParams(p2);
                no.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { decideGuest(id, false); }
                });
                br.addView(ok);
                br.addView(no);
                card.addView(br);
            }

            if ("REG_REQ".equals(kind) && !handled) {
                LinearLayout br = new LinearLayout(this);
                br.setOrientation(LinearLayout.HORIZONTAL);
                Button ok = new Button(this);
                ok.setText("✅ تأیید عضویت");
                ok.setAllCaps(false);
                ok.setTextColor(Color.WHITE);
                ok.setBackgroundResource(R.drawable.btn_primary);
                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1);
                p1.setMargins(0, dp(8), dp(4), 0);
                ok.setLayoutParams(p1);
                ok.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { decide(id, true); }
                });
                Button no = new Button(this);
                no.setText("✖️ رد");
                no.setAllCaps(false);
                no.setTextColor(Color.WHITE);
                no.setBackgroundResource(R.drawable.btn_dark);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -2, 1);
                p2.setMargins(dp(4), dp(8), 0, 0);
                no.setLayoutParams(p2);
                no.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { decide(id, false); }
                });
                br.addView(ok);
                br.addView(no);
                card.addView(br);
            }

            list.addView(card);
        }
        PatrolStore st2 = new PatrolStore(this);
        try { st2.markMsgsSeen(); } finally { st2.close(); }
    }

    /** تأیید/رد درخواست عضویت (در پایگاه‌دادهٔ ساختمان روی هاب ثبت میشود) */
    void decide(final long id, final boolean approve) {
        PatrolStore st = new PatrolStore(this);
        String payload = null;
        try {
            for (String[] r : st.msgs(200)) {
                if (Long.parseLong(r[0]) == id) { payload = r[4]; break; }
            }
        } finally { st.close(); }
        if (payload == null) return;
        try {
            final JSONObject j = new JSONObject(payload);
            final String code = Cfg.p(this).getString("bldgCode", "").trim();
            final String uid = j.optString("uid", "");
            final String name = j.optString("name", "");
            final String mobile = j.optString("mobile", "");
            final InboxActivity self = this;
            new Thread(new Runnable() {
                @Override public void run() {
                    if (approve) {
                        // در جدول اهالیِ ساختمان (هاب) تأیید میشود + پیام REG_OK خودکار میرود
                        Cloud.hubResDecide(code, uid, true);
                        PatrolStore st = new PatrolStore(self);
                        try { st.addRes(name, mobile, "", "", ""); } finally { st.close(); }
                    } else {
                        Cloud.hubResDecide(code, uid, false);
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            PatrolStore st = new PatrolStore(self);
                            try { st.markMsgHandled(id); } finally { st.close(); }
                            Toast.makeText(self, approve ? "✅ عضویت تأیید شد" : "رد شد", Toast.LENGTH_SHORT).show();
                            render();
                        }
                    });
                }
            }).start();
        } catch (Exception ignored) { }
    }

    /** تأیید/رد درخواست مهمان ساکن — از طریق هاب؛ نگهبان و ساکن هم‌زمان اطلاع می‌گیرند */
    void decideGuest(final long id, final boolean approve) {
        PatrolStore st = new PatrolStore(this);
        String payload = null;
        try {
            for (String[] r : st.msgs(200)) {
                if (Long.parseLong(r[0]) == id) { payload = r[4]; break; }
            }
        } finally { st.close(); }
        if (payload == null) return;
        try {
            final JSONObject j = new JSONObject(payload);
            final String code = Cfg.p(this).getString("bldgCode", "").trim();
            final long gid = j.optLong("id", 0);
            final InboxActivity self = this;
            new Thread(new Runnable() {
                @Override public void run() {
                    Cloud.hubGuestDecide(code, gid, approve);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            PatrolStore st = new PatrolStore(self);
                            try {
                                st.setGuestApprovedByReq(gid, approve);
                                st.markMsgHandled(id);
                            } finally { st.close(); }
                            Toast.makeText(self, approve ? "✅ ورود مهمان تأیید شد" : "درخواست مهمان رد شد", Toast.LENGTH_SHORT).show();
                            render();
                        }
                    });
                }
            }).start();
        } catch (Exception ignored) { }
    }

    /** تأیید/رد درخواست کلید پشت‌بام: پاسخ به ساکن + اطلاع به نگهبانی */
    void decideRoof(final long id, final boolean approve) {
        PatrolStore st = new PatrolStore(this);
        String payload = null, src = "";
        try {
            for (String[] r : st.msgs(200)) {
                if (Long.parseLong(r[0]) == id) { payload = r[4]; src = r[2]; break; }
            }
        } finally { st.close(); }
        if (payload == null) return;
        try {
            final JSONObject j = new JSONObject(payload);
            final String code = Cfg.p(this).getString("bldgCode", "").trim();
            final String uid = j.optString("uid", "");
            final String name = j.optString("name", "");
            final String unit = j.optString("unit", "");
            final String dst = src.isEmpty() ? ("saken:" + uid) : src;
            final InboxActivity self = this;
            new Thread(new Runnable() {
                @Override public void run() {
                    JSONObject rep = new JSONObject();
                    JSONObject gn = new JSONObject();
                    try {
                        rep.put("uid", uid); rep.put("name", name); rep.put("unit", unit);
                        gn.put("uid", uid); gn.put("name", name); gn.put("unit", unit);
                        gn.put("ts", PatrolStore.now());
                    } catch (Exception ignored) { }
                    if (approve) {
                        Cloud.hubMsg(code, dst, "modir", "ROOF_KEY_OK", rep.toString());
                        if (!"negahban".equals(dst)) Cloud.hubMsg(code, "negahban", "modir", "ROOF_KEY_OK", gn.toString());
                    } else {
                        Cloud.hubMsg(code, dst, "modir", "ROOF_KEY_NO", rep.toString());
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            PatrolStore st = new PatrolStore(self);
                            try { st.markMsgHandled(id); } finally { st.close(); }
                            Toast.makeText(self, approve ? "✅ تأیید شد — به ساکن و نگهبانی اطلاع داده شد" : "رد شد",
                                    Toast.LENGTH_SHORT).show();
                            render();
                        }
                    });
                }
            }).start();
        } catch (Exception ignored) { }
    }

    /** ارسال خبر به همهٔ اهالی (روی داشبورد ساکن‌یار دیده میشود) */
    void newsComposer() {
        final EditText etTitle = new EditText(this);
        etTitle.setHint("عنوان خبر");
        final EditText etBody = new EditText(this);
        etBody.setHint("متن خبر");
        etBody.setMinLines(2);
        etBody.setGravity(android.view.Gravity.TOP);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        box.addView(etTitle);
        box.addView(etBody);
        new AlertDialog.Builder(this)
                .setTitle("📢 خبر به اهالی")
                .setView(box)
                .setPositiveButton("ارسال", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        final String t = etTitle.getText().toString().trim();
                        final String b = etBody.getText().toString().trim();
                        if (t.isEmpty()) { Toast.makeText(self2(), "عنوان خالی است", Toast.LENGTH_SHORT).show(); return; }
                        new Thread(new Runnable() {
                            @Override public void run() {
                                String code = Cfg.p(InboxActivity.this).getString("bldgCode", "").trim();
                                JSONObject j = new JSONObject();
                                try {
                                    j.put("ts", PatrolStore.now());
                                    j.put("title", t);
                                    j.put("body", b);
                                } catch (Exception ignored) { }
                                final boolean ok = !code.isEmpty()
                                        && Cloud.hubDataPut(code, "news", j.toString(), true).ok;
                                // پیام بلادرنگ به زنگولهٔ همهٔ ساکن‌ها + اعلان سیستمی
                                final boolean bell = !code.isEmpty() && Cloud.hubMsg(code, "saken", "modir", "NEWS",
                                        j.optJSONObject("x") == null ? j.toString() : j.toString()).ok;
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        Toast.makeText(InboxActivity.this, ok ? (bell ? "✅ خبر رفت + اعلان به همهٔ ساکن‌ها" : "✅ خبر رفت (اعلان فوری نرسید)") : "⛔ ارسال نشد",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    InboxActivity self2() { return this; }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    static String clock(long ts) {
        java.util.Calendar ca = java.util.Calendar.getInstance();
        ca.setTimeInMillis(ts);
        String h = (ca.get(java.util.Calendar.HOUR_OF_DAY) < 10 ? "0" : "") + ca.get(java.util.Calendar.HOUR_OF_DAY);
        String m = (ca.get(java.util.Calendar.MINUTE) < 10 ? "0" : "") + ca.get(java.util.Calendar.MINUTE);
        return Scheduler.fa(h + ":" + m);
    }

    /** تصمیم برای درخواست‌های خدماتی (MOVE/ELEV): تأیید → ساکن + نگهبان؛ رد → فقط ساکن */
    void decideSvc(final long id, final String kind, final boolean approve) {
        PatrolStore st = new PatrolStore(this);
        String payload = null, src = "";
        try {
            for (String[] r : st.msgs(200)) {
                if (Long.parseLong(r[0]) == id) { payload = r[4]; src = r[2]; break; }
            }
        } finally { st.close(); }
        if (payload == null) return;
        try {
            final JSONObject j = new JSONObject(payload);
            final String code = Cfg.p(this).getString("bldgCode", "").trim();
            final String uid = j.optString("uid", "");
            final String name = j.optString("name", "");
            final String unit = j.optString("unit", "");
            final String dst = src.isEmpty() ? ("saken:" + uid) : src;
            final InboxActivity self = this;
            new Thread(new Runnable() {
                @Override public void run() {
                    JSONObject rep = new JSONObject();
                    JSONObject gn = new JSONObject();
                    try {
                        rep.put("uid", uid); rep.put("name", name); rep.put("unit", unit);
                        gn.put("uid", uid); gn.put("name", name); gn.put("unit", unit);
                        gn.put("ts", PatrolStore.now());
                    } catch (Exception ignored) { }
                    if (approve) {
                        Cloud.hubMsg(code, dst, "modir", kind + "_OK", rep.toString());
                        if (!"negahban".equals(dst)) Cloud.hubMsg(code, "negahban", "modir", kind + "_OK", gn.toString());
                    } else {
                        Cloud.hubMsg(code, dst, "modir", kind + "_NO", rep.toString());
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            PatrolStore st2 = new PatrolStore(self);
                            try { st2.markMsgHandled(id); } finally { st2.close(); }
                            String lbl = "MOVE".equals(kind) ? "اسباب‌کشی" : "آسانسور";
                            Toast.makeText(self, approve ? ("✅ تأیید شد — به ساکن و نگهبانی اطلاع داده شد (" + lbl + ")") : ("درخواست " + lbl + " رد شد"),
                                    Toast.LENGTH_SHORT).show();
                            render();
                        }
                    });
                }
            }).start();
        } catch (Exception ignored) { }
    }

}

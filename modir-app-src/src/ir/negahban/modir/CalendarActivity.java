package ir.negahban.modir;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 📅 تاریخچهٔ رویداد — تقویم فارسی زیبا با حلقهٔ رنگی هر روز:
 * قرمز=مشکل/ردشده، نارنجی=در انتظار، سبز=تأییدشده، آبی=گزارش نگهبان، خاکستری=بدون رویداد
 * لمس هر روز → فهرست رویدادهای همان روز (درخواست‌ها + گزارش گشت‌ها)
 * منبع: صندوق پیام‌های ذخیره‌شده (msg) + رویدادهای محلی
 */
public class CalendarActivity extends Activity {

    private static final int C_PROBLEM = 0xFFE53935;   // قرمز
    private static final int C_PENDING = 0xFFF57C00;   // نارنجی
    private static final int C_OK = 0xFF2E7D32;        // سبز
    private static final int C_REPORT = 0xFF1565C0;    // آبی
    private static final int C_NONE = 0xFF90A4AE;      // خاکستری

    private int viewJy, viewJm;
    private LinearLayout gridBox;
    private TextView tvMonth, tvSummary;
    /** کلید روز (شبانه) → فهرست رویدادها [icon, title, detail, hhmm, color] */
    private Map<Long, List<String[]>> byDay = new HashMap<>();
    private boolean fetching;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFFF4F6F9);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(30));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // هدر
        TextView title = new TextView(this);
        title.setText("📅 تاریخچهٔ رویداد");
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(title);

        // خلاصهٔ ماه
        tvSummary = new TextView(this);
        tvSummary.setTextSize(13);
        tvSummary.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(Color.WHITE);
        sg.setCornerRadius(dp(12));
        tvSummary.setBackground(sg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(10);
        tvSummary.setLayoutParams(slp);
        root.addView(tvSummary);

        // ناوبری ماه
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(12), 0, dp(4));
        TextView prev = navBtn("◀");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shiftMonth(-1); }
        });
        TextView next = navBtn("▶");
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shiftMonth(1); }
        });
        TextView btnToday = navBtn("امروز");
        btnToday.setTextSize(13);
        btnToday.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int[] t = Jalali.fromMillis(PatrolStore.now());
                viewJy = t[0]; viewJm = t[1];
                loadMonth();
            }
        });
        tvMonth = new TextView(this);
        tvMonth.setTextSize(18);
        tvMonth.setTypeface(null, Typeface.BOLD);
        tvMonth.setTextColor(0xFF0F3D56);
        tvMonth.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
        nav.addView(prev);
        nav.addView(tvMonth, mp);
        nav.addView(next);
        root.addView(nav);

        // سرستون روزهای هفته
        String[] wd = {"ش", "ی", "د", "س", "چ", "پ", "ج"};
        LinearLayout wh = new LinearLayout(this);
        wh.setOrientation(LinearLayout.HORIZONTAL);
        for (String w : wd) {
            TextView t = new TextView(this);
            t.setText(w);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(13);
            t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(0xFF78909C);
            wh.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        root.addView(wh);

        // شبکهٔ روزها
        gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gg = new GradientDrawable();
        gg.setColor(Color.WHITE);
        gg.setCornerRadius(dp(14));
        gridBox.setBackground(gg);
        gridBox.setPadding(dp(8), dp(10), dp(8), dp(12));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = dp(6);
        gridBox.setLayoutParams(glp);
        root.addView(gridBox);

        // راهنمای رنگ‌ها
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(0, dp(12), 0, 0);
        legend.addView(legendDot(C_PROBLEM, "مشکل/رد"));
        legend.addView(legendDot(C_PENDING, "در انتظار"));
        legend.addView(legendDot(C_OK, "تأیید"));
        legend.addView(legendDot(C_REPORT, "گزارش نگهبان"));
        legend.addView(legendDot(C_NONE, "بی‌رویداد"));
        root.addView(legend);

        int[] today = Jalali.fromMillis(PatrolStore.now());
        viewJy = today[0];
        viewJm = today[1];
        // ⬅ مهم: root باید داخل ScrollView باشد وگرنه صفحه خالی می‌ماند
        sv.addView(root);
        setContentView(sv);
        loadMonth();
        refreshFromHub();
    }

    /** دریافت آرشیو ۴۰۰ روزه از هاب در پس‌زمینه و رندر دوباره */
    private void refreshFromHub() {
        if (fetching) return;
        final String code = Cfg.p(this).getString("bldgCode", "").trim();
        if (code.isEmpty()) return;
        fetching = true;
        new Thread(new Runnable() {
            @Override public void run() {
                PatrolStore st = new PatrolStore(CalendarActivity.this);
                try {
                    java.util.ArrayList<Cloud.MsgItem> items = Cloud.hubEvents(code, st.evarcMaxId());
                    if (!items.isEmpty()) {
                        st.evarcAdd(items);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (!isFinishing()) loadMonth();
                            }
                        });
                    }
                } catch (Exception ignored) {
                } finally {
                    fetching = false;
                }
            }
        }, "evFetch").start();
    }

    private TextView navBtn(String txt) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextSize(15);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF12B5A5);
        g.setCornerRadius(dp(20));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(38));
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout legendDot(int color, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(6), 0, dp(6), 0);
        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setShape(GradientDrawable.OVAL);
        dot.setBackground(g);
        box.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(10.5f);
        t.setTextColor(0xFF546E7A);
        box.addView(t);
        return box;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void shiftMonth(int delta) {
        viewJm += delta;
        if (viewJm > 12) { viewJm = 1; viewJy++; }
        if (viewJm < 1) { viewJm = 12; viewJy--; }
        loadMonth();
    }

    private void loadMonth() {
        try {
            loadMonthInner();
        } catch (Exception e) {
            try { CrashCatcher.addReport(this, "تقویم رویداد", e); } catch (Exception ignored) { }
            tvSummary.setText("⛔ خطا در بارگیری تاریخچه — از تنظیمات ⇐ «📩 پشتیبانی (بله)» گزارش را بفرست");
        }
    }

    /** آیکون و رنگ هر نوع پیام */
    private static String[] kindStyle(String kind, String payload) {
        if (kind == null) return new String[]{"💬", String.valueOf(C_NONE)};
        switch (kind) {
            case "REG_REQ":  return new String[]{"📝", String.valueOf(C_PENDING)};
            case "ROOF_KEY_REQ": return new String[]{"🔑", String.valueOf(C_PENDING)};
            case "MOVE_REQ": return new String[]{"🚚", String.valueOf(C_PENDING)};
            case "ELEV_REQ": return new String[]{"🛗", String.valueOf(C_PENDING)};
            case "GU_GUEST_REQ": case "GUEST_REQ": return new String[]{"🚶", String.valueOf(C_PENDING)};
            case "CHARGE_PAY": return new String[]{"💳", String.valueOf(C_OK)};
            case "ROOF_KEY_OK": case "MOVE_OK": case "ELEV_OK": case "GU_OK": case "REG_OK":
                return new String[]{"✅", String.valueOf(C_OK)};
            case "ROOF_KEY_NO": case "MOVE_NO": case "ELEV_NO": case "GU_NO": case "REG_NO":
            case "CHALLENGE_FAIL": case "PATROL_MISS": case "BADPLAQUE": case "TIMEFLAG":
                return new String[]{"⛔", String.valueOf(C_PROBLEM)};
            case "GUEST_IN": return new String[]{"🚶", String.valueOf(C_REPORT)};
            case "GUEST_OUT": return new String[]{"🚪", String.valueOf(C_REPORT)};
            case "PKG_IN": return new String[]{"📦", String.valueOf(C_REPORT)};
            case "PKG_OUT": return new String[]{"✅", String.valueOf(C_REPORT)};
            case "NEWS": return new String[]{"📢", String.valueOf(C_OK)};
            case "REPORT": return new String[]{"🌙", String.valueOf(C_REPORT)};
            default: return new String[]{"💬", String.valueOf(C_NONE)};
        }
    }

    /** یک خط توضیح از پیام برای نمایش در روز */
    private static String briefOf(String kind, String payload) {
        try {
            org.json.JSONObject j = new org.json.JSONObject(payload);
            String n = j.optString("name", j.optString("guest", ""));
            String u = j.optString("unit", "");
            String who = n + (u.isEmpty() ? "" : " — واحد " + u);
            if ("REG_REQ".equals(kind)) return "درخواست عضویت: " + who;
            if ("ROOF_KEY_REQ".equals(kind)) return "کلید پشت‌بام: " + who;
            if ("MOVE_REQ".equals(kind)) return "اسباب‌کشی: " + who;
            if ("ELEV_REQ".equals(kind)) return "آسانسور: " + who;
            if ("GU_GUEST_REQ".equals(kind) || "GUEST_REQ".equals(kind)) return "مهمان: " + who;
            if ("CHARGE_PAY".equals(kind)) return "پرداخت شارژ: " + who;
            if (kind.endsWith("_OK")) return who.isEmpty() ? "تأیید شد" : "تأیید شد: " + who;
            if (kind.endsWith("_NO")) return who.isEmpty() ? "رد شد" : "رد شد: " + who;
            if ("GU_OK".equals(kind)) return "ورود مهمان تأیید شد";
            if ("GU_NO".equals(kind)) return "ورود مهمان رد شد";
            if ("NEWS".equals(kind)) return "اطلاعیه: " + j.optString("title", "");
        } catch (Exception e) { }
        if (payload != null && payload.startsWith("NGHQ|")) {
            String[] pp = payload.split("\\|");
            String t = pp.length > 1 ? pp[1] : "";
            String nm = pp.length > 3 ? pp[3] : "";
            if ("GUEST_IN".equals(t)) return "ورود مهمان: " + nm;
            if ("GUEST_OUT".equals(t)) return "خروج مهمان: " + nm;
            if ("PKG".equals(t)) return "ثبت بسته: " + (pp.length > 5 ? pp[5] : "");
            // گزارش گشت: متن انسانی انتهای رکورد — هیچ‌وقت خام نمایش داده نمی‌شود
            String txt = pp.length > 0 ? pp[pp.length - 1] : "";
            return "گشت شبانه: " + (txt.isEmpty() ? "گزارش ثبت شد" : txt);
        }
        if (payload != null && payload.startsWith("📦")) return "دریافت بسته";
        if (payload != null && payload.startsWith("👤")) return "مهمان/بسته";
        return "پیام";
    }

    /** نیمه‌شبِ محلیِ همان روز — بدون جابه‌جاییِ شبِ گشت */
    private static long dayKeyOf(long ts) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static void addEvent(Map<Long, List<String[]>> byDay, HashSet<String> seen,
                                 String tsStr, String kind, String payload) {
        long ts;
        try { ts = Long.parseLong(tsStr); } catch (Exception e) { return; }
        String key = ts + "|" + kind + "|" + String.valueOf(payload).hashCode();
        if (!seen.add(key)) return;
        long dayKey = dayKeyOf(ts);
        List<String[]> l = byDay.get(dayKey);
        if (l == null) { l = new ArrayList<String[]>(); byDay.put(dayKey, l); }
        String[] sty = kindStyle(kind, payload);
        l.add(new String[]{sty[0], briefOf(kind, payload), kind,
                Scheduler.hm(ts), sty[1]});
    }

    private void loadMonthInner() {
        int len = Jalali.daysInMonth(viewJy, viewJm);
        long firstTs = Jalali.jToMillis(viewJy, viewJm, 1);
        long monthEnd = firstTs + len * 86400000L;

        tvMonth.setText(Jalali.MONTHS[viewJm - 1] + " " + Scheduler.fa(String.valueOf(viewJy)));

        byDay.clear();
        HashSet<String> seen = new HashSet<>();
        PatrolStore st = new PatrolStore(this);
        try {
            // ۱) آرشیو کامل هاب (کش محلی) — منبع اصلی ماه‌های قبل
            for (String[] m : st.evarc(5000)) {
                addEvent(byDay, seen, m[1], m[3], m[4]);
            }
            // ۲) صندوق محلی (برای پیام‌هایی که هنوز در آرشیو نیامده) — بدون تکرار
            for (String[] m : st.msgs(400)) {
                addEvent(byDay, seen, m[1], m[3], m[4]);
            }
        } finally {
            st.close();
        }

        gridBox.removeAllViews();
        java.util.Calendar firstCal = java.util.Calendar.getInstance();
        firstCal.setTimeInMillis(firstTs);
        int firstCol = firstCal.get(java.util.Calendar.DAY_OF_WEEK) % 7; // شنبه=۰

        int nOk = 0, nBad = 0, nPend = 0, nRep = 0;
        LinearLayout row = null;
        for (int i = 0; i < firstCol; i++) row = addEmptyCell(row);
        long now = PatrolStore.now();
        long todayKey = dayKeyOf(now);

        for (int day = 1; day <= len; day++) {
            if (firstCol + day - 1 != 0 && (firstCol + day - 1) % 7 == 0) {
                gridBox.addView(row);
                row = null;
            }
            if (row == null) row = newRow();
            long ts = firstTs + (day - 1) * 86400000L;
            List<String[]> evs = byDay.get(ts);

            int color = C_NONE;
            String dot = "";
            if (evs != null && !evs.isEmpty()) {
                boolean bad = false, pend = false, ok = false, rep = false;
                for (String[] e : evs) {
                    int c = Integer.parseInt(e[4]);
                    if (c == C_PROBLEM) bad = true;
                    else if (c == C_PENDING) pend = true;
                    else if (c == C_REPORT) rep = true;
                    else ok = true;
                }
                if (bad) { color = C_PROBLEM; nBad++; }
                else if (pend) { color = C_PENDING; nPend++; }
                else if (rep) { color = C_REPORT; nRep++; }
                else { color = C_OK; nOk++; }
                dot = String.valueOf(evs.size());
            }

            boolean isToday = ts == todayKey;
            boolean future = ts > now;
            row.addView(makeDayCell(day, color, dot, isToday, future, ts, evs));
        }
        if (row != null) {
            int filled = (firstCol + len) % 7;
            int pad = (filled == 0) ? 0 : 7 - filled;
            for (int i = 0; i < pad; i++) row = addEmptyCell(row);
            gridBox.addView(row);
        }

        String stat = "این ماه:  " + Scheduler.fa(String.valueOf(nBad)) + " روز با مشکل ⛔   "
                + Scheduler.fa(String.valueOf(nPend)) + " در انتظار ⏳   "
                + Scheduler.fa(String.valueOf(nOk)) + " تأیید ✅   "
                + Scheduler.fa(String.valueOf(nRep)) + " گزارش 🌙";
        tvSummary.setText(stat);
    }

    private LinearLayout newRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout addEmptyCell(LinearLayout row) {
        if (row == null) row = newRow();
        LinearLayout c = new LinearLayout(this);
        c.addView(new TextView(this), new LinearLayout.LayoutParams(0, 0, 1f));
        row.addView(c, new LinearLayout.LayoutParams(0, dp(46), 1f));
        return row;
    }

    private View makeDayCell(final int day, final int ringColor, final String count,
                             final boolean isToday, final boolean future, final long ts,
                             final List<String[]> evs) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(evs == null || evs.isEmpty() ? 0xFFF7F9FA : 0xFFFFFFFF);
        bg.setCornerRadius(dp(12));
        if (ringColor != C_NONE) bg.setStroke(dp(2), ringColor);
        if (isToday) bg.setColor(0xFFE8F0FE);
        bubble.setBackground(bg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(44));
        cell.addView(bubble, blp);

        TextView num = new TextView(this);
        num.setText(Scheduler.fa(String.valueOf(day)));
        num.setTextSize(14);
        num.setTypeface(null, Typeface.BOLD);
        num.setGravity(Gravity.CENTER);
        num.setTextColor(future ? 0xFFB0BEC5 : 0xFF263238);
        bubble.addView(num);

        if (count != null && !count.isEmpty()) {
            TextView c = new TextView(this);
            c.setText(Scheduler.fa(count));
            c.setTextSize(9);
            c.setGravity(Gravity.CENTER);
            c.setTextColor(ringColor);
            bubble.addView(c);
        }

        if (evs != null && !evs.isEmpty() && !future) {
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showDay(ts, day, evs); }
            });
        }
        return cell;
    }

    /** پنجرهٔ رویدادهای یک روز */
    private void showDay(long ts, int day, List<String[]> evs) {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(4), dp(6), dp(6));
        sc.addView(box);

        TextView head = new TextView(this);
        head.setText("📅 " + Scheduler.fa(String.valueOf(day)) + " " + Jalali.MONTHS[viewJm - 1]
                + " — " + Scheduler.fa(String.valueOf(evs.size())) + " رویداد");
        head.setTextSize(15);
        head.setTypeface(null, Typeface.BOLD);
        head.setTextColor(0xFF0F3D56);
        head.setGravity(Gravity.CENTER);
        head.setPadding(0, dp(4), 0, dp(8));
        box.addView(head);

        for (String[] e : evs) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFFFFFFFF);
            g.setCornerRadius(dp(10));
            g.setStroke(dp(1), Color.parseColor("#" + String.format("%06X", 0xFFFFFF & Integer.parseInt(e[4]))));
            card.setBackground(g);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            card.setLayoutParams(lp);

            TextView t1 = new TextView(this);
            t1.setText(e[0] + "  " + e[1]);
            t1.setTextSize(13.5f);
            t1.setTypeface(null, Typeface.BOLD);
            t1.setTextColor(0xFF263238);
            card.addView(t1);

            TextView t2 = new TextView(this);
            t2.setText("🕐 " + Scheduler.fa(e[3]));
            t2.setTextSize(11);
            t2.setTextColor(0xFF90A4AE);
            LinearLayout.LayoutParams t2lp = new LinearLayout.LayoutParams(-2, -2);
            t2lp.topMargin = dp(2);
            t2.setLayoutParams(t2lp);
            card.addView(t2);

            box.addView(card);
        }

        new AlertDialog.Builder(this)
                .setTitle("رویدادهای روز")
                .setView(sc)
                .setPositiveButton("بستن", null)
                .show();
    }
}

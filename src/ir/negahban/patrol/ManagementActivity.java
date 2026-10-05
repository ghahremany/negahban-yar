package ir.negahban.patrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * پنل مدیریت (محافظت‌شده با PIN):
 * تقویم جلالیِ گشت — هر شب سبز (سالم) / قرمز (مشکل‌دار) / خاکستری (بدون ثبت) / آبی (ثبت پراکنده)
 * لمس هر روز → جزئیات همان شب + ارسال دوبارهٔ گزارش در بله
 */
public class ManagementActivity extends Activity {

    private static final int C_GREEN = 0xFFC8E6C9;
    private static final int C_RED = 0xFFFFCDD2;
    private static final int C_GRAY = 0xFFECEFF1;
    private static final int C_BLUE = 0xFFBBDEFB;
    private static final int C_NONE = 0xFFFFFFFF;

    private int viewJy, viewJm;
    private LinearLayout gridBox;
    private TextView tvMonth, tvSummary;
    private Map<Long, List<String[]>> byNight = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        gateWithPin();
    }

    /** ورود فقط با PIN مدیر */
    private void gateWithPin() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("PIN مدیر");
        new AlertDialog.Builder(this)
                .setTitle("📊 پنل مدیریت")
                .setMessage("برای ورود، PIN مدیر را وارد کنید")
                .setView(et)
                .setCancelable(false)
                .setPositiveButton("ورود", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (Cfg.pin(ManagementActivity.this).equals(et.getText().toString().trim())) {
                            buildUi();
                        } else {
                            Toast.makeText(ManagementActivity.this, "PIN اشتباه است", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                })
                .setNegativeButton("انصراف", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { finish(); }
                })
                .show();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(30));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("📊 پنل مدیریت گشت");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // خلاصهٔ وضعیت
        tvSummary = new TextView(this);
        tvSummary.setTextSize(14);
        tvSummary.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(0xFFE8F0FE);
        sg.setCornerRadius(dp(10));
        tvSummary.setBackground(sg);
        root.addView(tvSummary);

        // نوار ماه
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(14), 0, dp(6));
        Button prev = new Button(this);
        prev.setText("ماه قبل ◀");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shiftMonth(-1); }
        });
        Button next = new Button(this);
        next.setText("▶ ماه بعد");
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shiftMonth(1); }
        });
        tvMonth = new TextView(this);
        tvMonth.setTextSize(18);
        tvMonth.setTypeface(null, Typeface.BOLD);
        tvMonth.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nav.addView(prev);
        nav.addView(tvMonth, mp);
        nav.addView(next);
        root.addView(nav);

        // سرستون هفته
        String[] wd = {"ش", "ی", "د", "س", "چ", "پ", "ج"};
        LinearLayout wh = new LinearLayout(this);
        wh.setOrientation(LinearLayout.HORIZONTAL);
        for (String w : wd) {
            TextView t = new TextView(this);
            t.setText(w);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(13);
            t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(0xFF666666);
            wh.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(wh);

        // شبکهٔ تقویم
        gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(gridBox);

        // راهنما
        TextView legend = new TextView(this);
        legend.setText("🟩 گشت کامل و سالم   🟥 مشکل‌دار (ناقص/چالش/پلاک جعلی/ساعت)\n⬜ بدون ثبت (دستگاه خاموش؟)   🟦 ثبت پراکنده خارج از گشت\nبرای جزئیات، روز را لمس کنید");
        legend.setTextSize(12);
        legend.setPadding(dp(6), dp(14), dp(6), 0);
        root.addView(legend);

        int[] today = Jalali.fromMillis(PatrolStore.now());
        viewJy = today[0];
        viewJm = today[1];
        loadMonth();
    }

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
            tvSummary.setText("خطا در نمایش تقویم: " + e);
        }
    }

    private void loadMonthInner() {
        int len = Jalali.daysInMonth(viewJy, viewJm);
        long firstTs = Jalali.jToMillis(viewJy, viewJm, 1);
        long monthEnd = Jalali.jToMillis(viewJy, viewJm, len) + 86400000L;

        tvMonth.setText(Jalali.MONTHS[viewJm - 1] + " " + Scheduler.fa(String.valueOf(viewJy)));

        // دسته‌بندی رویدادها بر اساس «شب»
        byNight.clear();
        PatrolStore st = new PatrolStore(this);
        List<String[]> evs = st.evBetween(firstTs - 12 * 3600000L, monthEnd + 12 * 3600000L);
        st.close();
        for (String[] e : evs) {
            long key = Jalali.nightKey(Long.parseLong(e[3]));
            List<String[]> l = byNight.get(key);
            if (l == null) { l = new ArrayList<>(); byNight.put(key, l); }
            l.add(e);
        }

        gridBox.removeAllViews();
        Calendar firstCal = Calendar.getInstance();
        firstCal.setTimeInMillis(firstTs);
        int firstCol = firstCal.get(Calendar.DAY_OF_WEEK) % 7; // شنبه=ستون ۰

        int green = 0, red = 0, other = 0;
        LinearLayout row = null;
        for (int i = 0; i < firstCol; i++) row = addEmptyCell(row);
        long now = PatrolStore.now();
        long todayStart = Jalali.nightKey(now);

        for (int day = 1; day <= len; day++) {
            if (firstCol + day - 1 != 0 && (firstCol + day - 1) % 7 == 0) {
                gridBox.addView(row);
                row = null;
            }
            if (row == null) row = newRow();
            long ts = firstTs + (day - 1) * 86400000L;
            List<String[]> dayEvs = byNight.get(ts);
            int status = statusOf(dayEvs, ts > now);
            if (status == C_GREEN) green++;
            else if (status == C_RED) red++;
            else if (status != C_NONE) other++;

            boolean isToday = ts == todayStart;
            row.addView(makeDayCell(day, status, isToday, ts, dayEvs));
        }
        if (row != null) {
            int filled = (firstCol + len) % 7;
            int pad = (filled == 0) ? 0 : 7 - filled;
            for (int i = 0; i < pad; i++) row = addEmptyCell(row);
            gridBox.addView(row);
        }

        String stat = "این ماه: " + Scheduler.fa(String.valueOf(green)) + " شب سبز ✅  |  "
                + Scheduler.fa(String.valueOf(red)) + " شب قرمز ⛔  |  "
                + Scheduler.fa(String.valueOf(other)) + " شب بدون برنامه/خالی";
        tvSummary.setText(stat + "\n" + queueLine());
    }

    private String queueLine() {
        PatrolStore st = new PatrolStore(this);
        int pend = st.pendingCount();
        st.close();
        long lastSync = Cfg.p(this).getLong("lastSync", 0);
        String s = "📥 صف ارسال به بله: " + Scheduler.fa(String.valueOf(pend)) + " پیام";
        if (lastSync > 0) s += "  |  آخرین سینک: " + Scheduler.fa(Scheduler.hm(lastSync));
        return s;
    }

    /** وضعیت یک شب از روی رویدادهایش */
    private static int statusOf(List<String[]> evs, boolean future) {
        if (evs == null || evs.isEmpty()) return future ? C_NONE : C_GRAY;
        boolean red = false, anyPatrol = false, anyScan = false;
        for (String[] e : evs) {
            String t = e[0];
            if (t.equals("PATROL_MISS") || t.equals("CHALLENGE_FAIL") || t.equals("TIMEFLAG") || t.equals("BADPLAQUE")
                    || (t.equals("SCAN") && "GAP".equals(e[2]))) red = true;
            if (t.equals("PATROL_OK") || t.equals("PATROL_MISS")) anyPatrol = true;
            if (t.equals("SCAN") || t.equals("CHALLENGE_OK")) anyScan = true;
        }
        if (red) return C_RED;
        if (anyPatrol) return C_GREEN;
        if (anyScan) return C_BLUE;
        return C_GRAY; // فقط رویدادهای بی‌اثر
    }

    private LinearLayout newRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout addEmptyCell(LinearLayout row) {
        if (row == null) row = newRow();
        View v = new View(this);
        row.addView(v, new LinearLayout.LayoutParams(0, dp(52), 1f));
        return row;
    }

    private View makeDayCell(final int day, final int color, final boolean isToday, final long ts, final List<String[]> evs) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        if (isToday) {
            bg.setStroke(dp(2), 0xFF1A5276);
        }
        cell.setBackground(bg);

        TextView num = new TextView(this);
        num.setText(Scheduler.fa(String.valueOf(day)));
        num.setTextSize(15);
        num.setTypeface(null, Typeface.BOLD);
        num.setGravity(Gravity.CENTER);
        cell.addView(num);

        String mark = "";
        if (color == C_GREEN) mark = "✅";
        else if (color == C_RED) mark = "⛔";
        else if (color == C_BLUE) mark = "🔵";
        else if (color == C_GRAY) mark = "—";
        TextView mk = new TextView(this);
        mk.setText(mark);
        mk.setTextSize(10);
        mk.setGravity(Gravity.CENTER);
        cell.addView(mk);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
        cell.setLayoutParams(lp);

        if (color != C_NONE && evs != null && !evs.isEmpty()) {
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showDay(ts); }
            });
        }
        return cell;
    }

    /** جزئیات یک شب + ارسال دوبارهٔ گزارش */
    private void showDay(final long ts) {
        final String report = Report.buildDay(this, ts);
        int[] j = Jalali.fromMillis(ts);
        new AlertDialog.Builder(this)
                .setTitle("شب " + Scheduler.fa(String.valueOf(j[2])) + " " + Jalali.MONTHS[j[1] - 1] + " " + Scheduler.fa(String.valueOf(j[0])))
                .setMessage(report)
                .setPositiveButton("📨 ارسال دوباره در بله", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        PatrolStore st = new PatrolStore(ManagementActivity.this);
                        st.enq(report);
                        st.close();
                        Toast.makeText(ManagementActivity.this, "به صف ارسال اضافه شد", Toast.LENGTH_SHORT).show();
                        new Thread(new Runnable() {
                            @Override public void run() { Sync.drain(ManagementActivity.this); }
                        }).start();
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }
}

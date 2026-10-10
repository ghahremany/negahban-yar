package ir.negahban.patrol;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

/** ساخت متن گزارش صبح برای بله */
public class Report {

    public static String build(android.content.Context c) {
        JSONObject plan = Scheduler.getPlan(c);
        StringBuilder sb = new StringBuilder();

        sb.append("📋 گزارش گشت شبانه\n")
          .append("🏢 ").append(Cfg.building(c)).append("\n")
          .append("شب ").append(Scheduler.fa(plan.optString("date", "")))
          .append(" (").append(Scheduler.fa(hhmm(Cfg.nightStart(c))))
          .append(" تا ").append(Scheduler.fa(hhmm(Cfg.nightEnd(c)))).append(")\n\n");

        JSONArray js = plan.optJSONArray("patrols");
        long from = plan.optLong("base", 0);
        long to = plan.optLong("morning", PatrolStore.now());

        int totalScans = 0;
        if (js != null) {
            for (int i = 0; i < js.length(); i++) {
                JSONObject w = js.optJSONObject(i);
                if (w == null) continue;
                long s = w.optLong("s"), e = w.optLong("e");
                PatrolStore st = new PatrolStore(c);
                int got = st.distinctStations(s, e);
                ArrayList<long[]> scans = st.scansBetween(s, e);
                st.close();
                totalScans += got;

                int total = Cfg.stations(c).length;
                boolean complete = got >= total && total > 0;
                sb.append(complete ? "🟢" : "🔴").append(" گشت ").append(Scheduler.fa(String.valueOf(i + 1)))
                  .append(" (").append(Scheduler.fa(Scheduler.hm(s))).append(" تا ").append(Scheduler.fa(Scheduler.hm(e))).append("): ");
                if (complete) sb.append("✅ کامل (").append(Scheduler.fa(String.valueOf(got))).append("/").append(Scheduler.fa(String.valueOf(total))).append(")\n");
                else sb.append("⚠️ ناقص — ").append(Scheduler.fa(String.valueOf(got))).append("/").append(Scheduler.fa(String.valueOf(total))).append(" ایستگاه\n");

                // ایستگاه‌ها به ترتیب اسکن واقعی + فاصلهٔ زمانی مشکوک
                HashSet<Long> seen = new HashSet<>();
                JSONArray order = w.optJSONArray("order");
                long prevTs = 0;
                for (long[] sc : scans) {
                    long stId = sc[0], ts = sc[1];
                    String gapNote = "";
                    if (prevTs > 0 && (ts - prevTs) < Cfg.minGapSec(c) * 1000L) gapNote = " ⚡فاصلهٔ مشکوک";
                    sb.append("   • ").append(Cfg.stationName(c, (int) stId))
                      .append(" — ").append(Scheduler.fa(Scheduler.hm(ts))).append(gapNote).append("\n");
                    seen.add(stId);
                    prevTs = ts;
                }
                // ایستگاه‌های نرفته
                StringBuilder missing = new StringBuilder();
                if (order != null) {
                    for (int k = 0; k < order.length(); k++) {
                        long id = order.optLong(k, -1);
                        if (id >= 0 && !seen.contains(id)) {
                            if (missing.length() > 0) missing.append("، ");
                            missing.append(Cfg.stationName(c, (int) id));
                        }
                    }
                }
                if (missing.length() > 0) sb.append("   ✖️ نرفته: ").append(missing).append("\n");
                sb.append("\n");
            }
        }

        // چالش بیداری
        if (plan.has("challenge")) {
            long ch = plan.optLong("challenge", 0);
            if (plan.optBoolean("challengeDone", false)) {
                boolean ok = plan.optBoolean("challengeOk", false);
                sb.append("⏰ چالش بیداری (").append(Scheduler.fa(Scheduler.hm(ch))).append("): ")
                  .append(ok ? "✅ پاسخ داد" : "❌ پاسخ نداد").append("\n");
            } else {
                sb.append("⏰ چالش بیداری: ⚠️ انجام نشد (دستگاه خاموش بود؟)\n");
            }
        }

        // پرچم‌ها
        PatrolStore st = new PatrolStore(c);
        int timeFlags = st.countEv("TIMEFLAG", from, to);
        int bad = st.countEv("BADPLAQUE", from, to);
        st.close();
        boolean hasFlags = false;
        if (timeFlags > 0) {
            sb.append("🚩 مغایرت ساعت دستگاه با ساعت بله دیده شد (").append(Scheduler.fa(String.valueOf(timeFlags))).append(" بار)\n");
            hasFlags = true;
        }
        if (bad > 0) {
            sb.append("🚩 تلاش برای اسکن پلاک نامعتبر/جعلی: ").append(Scheduler.fa(String.valueOf(bad))).append(" بار\n");
            hasFlags = true;
        }
        if (!hasFlags) sb.append("✅ بدون پرچم مشکوک\n");

        sb.append("\n— نگهبان‌یار v۲٫۰");
        return sb.toString();
    }

    private static String hhmm(int minOfDay) {
        return String.format(java.util.Locale.US, "%02d:%02d", minOfDay / 60, minOfDay % 60);
    }

    /** گزارش یک شبِ مشخص (برای پنل مدیریت و ارسال دوباره) — dayStart = نیمه‌شبِ آن تاریخ */
    public static String buildDay(android.content.Context c, long dayStart) {
        long a = dayStart, b = dayStart + 86400000L;
        PatrolStore st = new PatrolStore(c);
        ArrayList<String[]> evs = st.evBetween(a, b);
        st.close();

        int[] j = Jalali.fromMillis(a);
        StringBuilder sb = new StringBuilder();
        sb.append("📋 گزارش گشت شب ")
          .append(Scheduler.fa(String.valueOf(j[2]))).append(" ")
          .append(Jalali.MONTHS[j[1] - 1]).append(" ")
          .append(Scheduler.fa(String.valueOf(j[0]))).append("\n");
        org.json.JSONObject guard = Cfg.activeGuard(c);
        if (guard != null) {
            sb.append("👤 نگهبان: ").append(guard.optString("name", "—"))
              .append(" (").append(Cfg.guardShiftText(guard)).append(") — ")
              .append(Scheduler.fa(guard.optString("phone", ""))).append("\n");
        }

        if (evs.isEmpty()) {
            sb.append("⚪ هیچ ثبت‌ی نداشت (دستگاه خاموش بود؟)");
            return sb.toString();
        }

        // وضعیت کلی
        boolean red = false, anyPatrol = false, anyScan = false;
        for (String[] e : evs) {
            String t = e[0];
            if (t.equals("PATROL_MISS") || t.equals("CHALLENGE_FAIL") || t.equals("TIMEFLAG") || t.equals("BADPLAQUE")
                    || (t.equals("SCAN") && "GAP".equals(e[2]))) red = true;
            if (t.equals("PATROL_OK") || t.equals("PATROL_MISS")) anyPatrol = true;
            if (t.equals("SCAN")) anyScan = true;
        }
        sb.append(red ? "🔴 وضعیت: مشکل‌دار" : (anyPatrol ? "🟢 وضعیت: سالم" : "🔵 ثبت پراکنده (بدون برنامهٔ گشت)")).append("\n\n");

        int badPlaques = 0;
        for (String[] e : evs) {
            String t = e[0], flag = e[2], extra = e[4] == null ? "" : e[4];
            long ts = Long.parseLong(e[3]);
            switch (t) {
                case "SCAN":
                    sb.append("• ").append(Cfg.stationName(c, Integer.parseInt(e[1])))
                      .append(" — ").append(Scheduler.fa(Scheduler.hm(ts)));
                    if ("GAP".equals(flag)) sb.append(" ⚡فاصلهٔ مشکوک");
                    sb.append("\n");
                    break;
                case "PATROL_OK":
                    sb.append("✅ گشت کامل (").append(Scheduler.fa(flag)).append(")\n");
                    break;
                case "PATROL_MISS":
                    sb.append("🔴 گشت ناقص (").append(Scheduler.fa(flag)).append(")");
                    if (!extra.isEmpty()) sb.append(" — نرفته: ").append(extra);
                    sb.append("\n");
                    break;
                case "CHALLENGE_OK":
                    sb.append("⏰ چالش بیداری: ✅ موفق (").append(Scheduler.fa(extra.replace("secs=", ""))).append(" ثانیه)\n");
                    break;
                case "CHALLENGE_FAIL":
                    sb.append("⏰ چالش بیداری: ❌ ناموفق/بی‌پاسخ");
                    if (!extra.isEmpty()) sb.append(" (").append(extra).append(")");
                    sb.append("\n");
                    break;
                case "TIMEFLAG":
                    String mm = extra.replace("offsetMin=", "");
                    sb.append("🚩 مغایرت ساعت گوشی (").append(Scheduler.fa(mm)).append(" دقیقه)\n");
                    break;
                case "BADPLAQUE":
                    badPlaques++;
                    break;
                default:
                    break;
            }
        }
        if (badPlaques > 0) sb.append("🚩 تلاش برای پلاک جعلی: ").append(Scheduler.fa(String.valueOf(badPlaques))).append(" بار\n");
        return sb.toString();
    }
}

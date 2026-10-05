package ir.negahban.patrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Executors;

/** همهٔ زمان‌بندی‌ها اینجا می‌رسند: پایان پنجره، چالش، گزارش صبح، سینک، قرعه‌کشی */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context c, final Intent i) {
        final PendingResult pr = goAsync();
        final String action = i == null ? "" : (i.getAction() == null ? "" : i.getAction());
        final int idx = i == null ? 0 : i.getIntExtra("idx", 0);
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override public void run() {
                try {
                    if (Scheduler.A_SYNC.equals(action)) {
                        Sync.drain(c);
                    } else if (Scheduler.A_WEND.equals(action)) {
                        windowEnd(c, idx);
                    } else if (Scheduler.A_CHAL.equals(action)) {
                        challenge(c);
                    } else if (Scheduler.A_MORN.equals(action)) {
                        morning(c);
                    } else if (Scheduler.A_PLAN.equals(action)) {
                        Scheduler.ensurePlan(c);
                    }
                } catch (Exception ignored) {
                } finally {
                    pr.finish();
                }
            }
        });
    }

    /** پایان پنجرهٔ گشت: نتیجه ثبت می‌شود (گزارش کامل صبح می‌رود) */
    private void windowEnd(Context c, int idx) {
        JSONObject plan = Scheduler.getPlan(c);
        JSONArray js = plan.optJSONArray("patrols");
        if (js == null || idx >= js.length()) return;
        JSONObject w = js.optJSONObject(idx);
        if (w == null) return;
        long s = w.optLong("s"), e = w.optLong("e");

        PatrolStore st = new PatrolStore(c);
        int got = st.distinctStations(s, e);
        int total = Cfg.stations(c).length;
        st.close();

        try {
            w.put("done", true);
            plan.put("patrols", js);
            Scheduler.savePlan(c, plan);
        } catch (Exception ignored) {}

        PatrolStore st2 = new PatrolStore(c);
        if (total > 0 && got < total) {
            StringBuilder missing = new StringBuilder();
            JSONArray order = w.optJSONArray("order");
            java.util.ArrayList<long[]> scans = st2.scansBetween(s, e);
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            for (long[] sc : scans) seen.add(sc[0]);
            if (order != null) {
                for (int k = 0; k < order.length(); k++) {
                    long id = order.optLong(k, -1);
                    if (id >= 0 && !seen.contains(id)) {
                        if (missing.length() > 0) missing.append("، ");
                        missing.append(Cfg.stationName(c, (int) id));
                    }
                }
            }
            st2.addEv("PATROL_MISS", null, got + "/" + total, missing.toString());
        } else {
            st2.addEv("PATROL_OK", null, got + "/" + total, "");
        }
        st2.close();
        Sync.drain(c);
    }

    /** چالش بیداری */
    private void challenge(Context c) {
        JSONObject plan = Scheduler.getPlan(c);
        if (plan.optBoolean("challengeDone", false)) return;
        long ch = plan.optLong("challenge", 0);
        long now = PatrolStore.now();
        if (ch == 0) return;

        if (Math.abs(now - ch) <= 5 * 60000L) {
            try {
                Intent it = new Intent(c, ChallengeActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(it);
            } catch (Exception e) {
                // اگر سیستم اجازهٔ بازکردن اکتیویتی نداد، در گزارش «انجام نشد» می‌آید
                Scheduler.markChallengeDone(c, false);
                PatrolStore st = new PatrolStore(c);
                st.addEv("CHALLENGE_FAIL", null, null, "nolaunch");
                st.close();
            }
        } else if (now > ch + 5 * 60000L) {
            Scheduler.markChallengeDone(c, false);
            PatrolStore st = new PatrolStore(c);
            st.addEv("CHALLENGE_FAIL", null, null, "missed");
            st.close();
        }
        // اگر زودتر آمد، کاری نمی‌کنیم (آلارم دقیق معمولاً سر وقت می‌آید)
    }

    /** گزارش صبح */
    private void morning(Context c) {
        JSONObject plan = Scheduler.getPlan(c);
        if (plan.optBoolean("reported", false)) return;
        try {
            plan.put("reported", true);
            Scheduler.savePlan(c, plan);
        } catch (Exception ignored) {}
        PatrolStore st = new PatrolStore(c);
        st.enq(Report.build(c));
        st.close();
        Sync.drain(c);
    }
}

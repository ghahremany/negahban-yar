package ir.negahban.patrol;

import android.content.Context;

import java.util.ArrayList;

/** خالی‌کردن صف پیام‌ها به مدیریت (از طریق هاب) — هر بار که اینترنت در دسترس باشد اجرا میشود */
public class Sync {

    /** ارسال فوری صف در پس‌زمینه — بلافاصله بعد از ثبت مهمان/بسته صدا زده می‌شود */
    public static void kick(final Context c) {
        try {
            new Thread(new Runnable() {
                @Override public void run() { try { drain(c); } catch (Exception ignored) { } }
            }, "sync-kick").start();
        } catch (Exception ignored) { }
    }

    public static synchronized void drain(Context c) {
        String code = Hub.code(c);
        if (code.isEmpty()) return;

        PatrolStore st = new PatrolStore(c);
        try {
            ArrayList<String[]> rows = st.pending();
            for (String[] row : rows) {
                Hub.Res r = Hub.msg(code, "modir", "negahban", row.length > 2 ? row[2] : "REPORT", row[1]);
                if (r.ok) {
                    st.markSent(Long.parseLong(row[0]));
                } else if (r.err.contains("اینترنت") || r.err.contains("پاسخ: 5")) {
                    break; // خطای شبکه/سرور — دفعهٔ بعد
                } else if (r.err.equals("nobldg") || r.err.equals("auth")) {
                    break; // کد ساختمان خراب — در UI دیده میشود
                } else {
                    st.markSent(Long.parseLong(row[0])); // خطای دائمی محتمل — گیر نکنیم
                }
            }
        } finally {
            st.close();
        }
        Cfg.set(c, "lastSync", PatrolStore.now());
    }
}

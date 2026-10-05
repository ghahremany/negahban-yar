package ir.negahban.patrol;

import android.content.Context;

import java.util.ArrayList;

/** خالی‌کردن صف پیام‌ها به بله — هر بار که اینترنت در دسترس باشد اجرا می‌شود */
public class Sync {

    public static void drain(Context c) {
        String tk = Cfg.token(c);
        long ch = Cfg.chatId(c);
        if (tk.isEmpty() || ch == 0) return;

        PatrolStore st = new PatrolStore(c);
        try {
            ArrayList<String[]> rows = st.pending();
            for (String[] row : rows) {
                Bale.Res r = Bale.send(tk, ch, row[1]);
                if (r.ok) {
                    st.markSent(Long.parseLong(row[0]));
                    Bale.checkClock(c, r);
                } else if (r.code == 401 || r.code == 403) {
                    // توکن خراب — بی‌فایده است ادامه بدهیم؛ در UI دیده می‌شود
                    Cfg.set(c, "tokenInvalid", true);
                    break;
                } else if (r.code >= 500 || r.code == 0) {
                    break; // خطای شبکه/سرور — دفعهٔ بعد
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

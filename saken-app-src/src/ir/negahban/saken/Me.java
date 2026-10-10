package ir.negahban.saken;

import android.content.Context;

import java.util.UUID;

/**
 * هویت ساکن: شناسهٔ یکتای گوشی + نام + همراه + واحد
 * عضویت با تأیید مدیر (از ساکن‌یارِ مدیر) انجام میشود و از فهرست اهالی خوانده میشود.
 */
public class Me {

    public static boolean registered(Context c) {
        return Cfg.p(c).getBoolean("regOk", false);
    }

    /** شناسهٔ یکتای این گوشی (برای صندوق پیام هاب) */
    public static String uid(Context c) {
        String u = Cfg.p(c).getString("myUid", "");
        if (u.isEmpty()) {
            u = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            Cfg.set(c, "myUid", u);
        }
        return u;
    }

    /** نشانی صندوق پیام این گوشی روی هاب */
    public static String inbox(Context c) {
        return "saken:" + uid(c);
    }

    public static String mobile(Context c) { return Cfg.p(c).getString("myMobile", ""); }
    public static String name(Context c) { return Cfg.p(c).getString("myName", ""); }
    public static String unit(Context c) { return Cfg.p(c).getString("myUnit", ""); }

    public static void save(Context c, String name, String mobile, String unit) {
        save(c, name, mobile, unit, role(c));
    }

    public static void save(Context c, String name, String mobile, String unit, String roleTxt) {
        Cfg.set(c, "myName", name);
        Cfg.set(c, "myMobile", mobile);
        Cfg.set(c, "myUnit", unit);
        Cfg.set(c, "myRole", roleTxt == null ? "" : roleTxt);
        Cfg.set(c, "regOk", true);
        Cfg.set(c, "regPending", false);
    }

    public static String role(Context c) { return Cfg.p(c).getString("myRole", ""); }

    public static boolean hasPending(Context c) { return Cfg.p(c).getBoolean("regPending", false); }

    public static void setPending(Context c, boolean v) { Cfg.set(c, "regPending", v); }

    public static void clear(Context c) {
        Cfg.set(c, "regOk", false);
    }
}

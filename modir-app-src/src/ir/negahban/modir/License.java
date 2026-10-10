package ir.negahban.modir;

import android.content.Context;

/**
 * لایسنس آفلاین نگهبان‌یار — حفاظت از حقوق سازنده
 * کد امضاشده با HMAC: NGY-<روزِ انقضا به پایهٔ ۳۶>-<۸ کاراکتر امضا>
 * بدون نیاز به اینترنت اعتبارسنجی میشود؛ جعل کد بدون کلید محرمانه ممکن نیست.
 */
public class License {

    /** کلید محرمانهٔ امضای لایسنس (جدا از کلید پلاک‌ها) */
    static final String SECRET = "NGY-LIC-7d21a9c4e5b83f016a2d84c7f3b59e2a";
    /** دورهٔ آزمایشی رایگان (روز) از اولین اجرا */
    public static final int TRIAL_DAYS = 14;

    /** اعتبارسنجی کد؛ معتبر → مِیلی‌ثانیهٔ انقضا، نامعتبر → ۰ */
    public static long expiryOf(String code) {
        try {
            code = code.trim().toUpperCase().replace(" ", "").replace("۰", "0");
            if (!code.startsWith("NGY-")) return 0;
            String[] p = code.split("-");
            if (p.length != 3 || p[1].isEmpty()) return 0;
            long days = Long.parseLong(p[1], 36);
            String sig = Crypto.hmacHex(SECRET, "LIC|" + p[1]).substring(0, 8);
            if (!sig.equalsIgnoreCase(p[2])) return 0;
            if (days * 86400000L < 1600000000000L) return 0; // تاریخ نامعقول
            return days * 86400000L;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void apply(Context c, long expiryMs) {
        Cfg.set(c, "licExpiry", expiryMs);
    }

    /** خواندن long با تحمل مقادیر intِ قدیمی */
    static long longSafe(Context c, String k) {
        try { return Cfg.p(c).getLong(k, 0); }
        catch (ClassCastException e) {
            try { return Cfg.p(c).getInt(k, 0) & 0xFFFFFFFFL; } catch (Exception e2) { return 0; }
        }
    }

    public static long expiry(Context c) {
        long ex = longSafe(c, "licExpiry");
        return (ex > 0 && ex < 1600000000000L) ? 0 : ex;
    }

    public static long installTs(Context c) {
        long t = longSafe(c, "installTs");
        if (t < 1600000000000L) {
            t = PatrolStore.now();
            Cfg.set(c, "installTs", t);
        }
        return t;
    }

    public static long trialEnd(Context c) {
        return installTs(c) + TRIAL_DAYS * 86400000L;
    }

    /** جلوی عقبراندن ساعت: همیشه بیشترین زمانِ دیده‌شده ملاک است */
    public static long effectiveNow(Context c) {
        long seen = longSafe(c, "licSeenMax");
        long now = PatrolStore.now();
        if (now > seen) {
            Cfg.set(c, "licSeenMax", now);
            return now;
        }
        return seen;
    }

    /** وضعیت: ۰=لایسنس فعال، ۱=دورهٔ آزمایشی، ۲=منقضی (نیاز به کد) */
    public static int status(Context c) {
        long now = effectiveNow(c);
        long ex = expiry(c);
        if (ex > 0 && ex >= now) return 0;
        if (now < trialEnd(c)) return 1;
        return 2;
    }

    /** روزهای باقی‌مانده (لایسنس یا آزمایشی) */
    public static long daysLeft(Context c) {
        long now = effectiveNow(c);
        long end = expiry(c) > 0 ? expiry(c) : trialEnd(c);
        return Math.max(0, (end - now) / 86400000L);
    }
}

package ir.negahban.modir;

import java.util.Calendar;

/** تبدیل تقویم میلادی↔جلالی (الگوریتم استاندارد jdf) — بدون وابستگی به اندروید */
public class Jalali {

    public static final String[] MONTHS = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };

    /** میلادی → جلالی؛ خروجی [سال، ماه ۱..۱۲، روز] — الگوریتم jdf */
    public static int[] g2j(long gy, long gm, long gd) {
        long[] gdm = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        long jy;
        if (gy >= 1600) { jy = 979; gy -= 1600; } else { jy = 0; gy -= 621; }
        long gy2 = (gm > 2) ? (gy + 1) : gy;
        long days = 365L * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
                - 80 + gd + gdm[(int) gm - 1];
        jy += 33L * (days / 12053);
        days %= 12053;
        jy += 4L * (days / 1461);
        days %= 1461;
        jy += (days - 1) / 365;
        if (days > 365) days = (days - 1) % 365;
        long jm = (days < 186) ? 1 + days / 31 : 7 + (days - 186) / 30;
        long jd = 1 + ((days < 186) ? days % 31 : (days - 186) % 30);
        return new int[]{(int) jy, (int) jm, (int) jd};
    }

    /** جلالی → میلادی؛ خروجی [سال، ماه ۱..۱۲، روز] — الگوریتم jdf */
    public static int[] j2g(long jy, long jm, long jd) {
        long gy;
        if (jy >= 979) { gy = 1600; jy -= 979; } else { gy = 621; }
        long days = 365L * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + 78 + jd
                + ((jm < 7) ? (jm - 1) * 31 : (jm - 7) * 30 + 186);
        gy += 400L * (days / 146097);
        days %= 146097;
        if (days > 36524) {
            gy += 100L * ((days - 1) / 36524);
            days = (days - 1) % 36524;
            if (days >= 365) days++;
        }
        gy += 4L * (days / 1461);
        days %= 1461;
        if (days > 365) {
            gy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        long gd = days + 1;
        boolean leap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0);
        long[] sa = {0, 31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int gm = 0;
        while (gm < 12 && gd > sa[gm + 1]) {
            gd -= sa[gm + 1];
            gm++;
        }
        return new int[]{(int) gy, gm + 1, (int) gd};
    }

    /** تعداد روزهای یک ماه جلالی */
    public static int daysInMonth(int jy, int jm) {
        if (jm <= 6) return 31;
        if (jm <= 11) return 30;
        // اسفند: فاصلهٔ اول اسفند تا اول فروردین سال بعد
        return diffDays(jy, 12, 1, jy + 1, 1, 1);
    }

    /** فاصلهٔ دو تاریخ جلالی به روز */
    public static int diffDays(int jy1, int jm1, int jd1, int jy2, int jm2, int jd2) {
        long t1 = jToMillis(jy1, jm1, jd1);
        long t2 = jToMillis(jy2, jm2, jd2);
        return (int) Math.round((t2 - t1) / 86400000.0);
    }

    /** مِیلی‌ثانیهٔ نیمه‌شبِ یک تاریخ جلالی (به وقت محلی) */
    public static long jToMillis(int jy, int jm, int jd) {
        int[] g = j2g(jy, jm, jd);
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(g[0], g[1] - 1, g[2], 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** تاریخ جلالی یک مِیلی‌ثانیه؛ خروجی [سال، ماه، روز] */
    public static int[] fromMillis(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return g2j(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * کلید «شب»: رویدادهای قبل از ظهر به شبِ روزِ قبل تعلق دارند.
     * یعنی اسکن ۰۰:۵۱ صبح ۱۳ مهر = «شب ۱۲ مهر» — مطابق ذهنیت مدیر.
     */
    public static long nightKey(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        if (c.get(Calendar.HOUR_OF_DAY) < 12) c.add(Calendar.HOUR_OF_DAY, -12);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}

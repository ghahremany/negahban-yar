package ir.negahban.saken;

import java.util.Calendar;
import java.util.Locale;

/** ابزارهای نمایش مشترک سوئیت (نسخهٔ ساکن: بدون زمان‌بند) */
public class Scheduler {

    /** اعداد لاتین → فارسی */
    public static String fa(String s) {
        String[] d = {"۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"};
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '0' && ch <= '9') sb.append(d[ch - '0']);
            else sb.append(ch);
        }
        return sb.toString();
    }

    public static String hm(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        return String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /** تاریخ شمسیِ یک لحظه، مثل «۱۲ مهر ۱۴۰۵» */
    public static String jalaliDate(long ts) {
        int[] j = Jalali.fromMillis(ts);
        return fa(String.valueOf(j[2])) + " " + Jalali.MONTHS[j[1] - 1] + " " + fa(String.valueOf(j[0]));
    }
}

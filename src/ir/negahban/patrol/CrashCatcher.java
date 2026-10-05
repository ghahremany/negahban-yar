package ir.negahban.patrol;

import android.content.Context;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * ضبط‌کنندهٔ خطاهای پیش‌بینی‌نشده:
 * به‌جای بسته‌شدن بی‌صدا، متن کامل خطا در فایل ذخیره می‌شود تا دفعهٔ بعد باز شود،
 * کاربر بتواند آن را کپی/اسکرین‌شات کند و برای توسعه‌دهنده بفرستد.
 */
public class CrashCatcher implements Thread.UncaughtExceptionHandler {

    private final Context ctx;
    private final Thread.UncaughtExceptionHandler previous;

    private CrashCatcher(Context c) {
        ctx = c.getApplicationContext();
        previous = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context c) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashCatcher(c));
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String txt = "زمان: " + Scheduler.hm(PatrolStore.now()) + "\n"
                    + Scheduler.hm(PatrolStore.now()) + " | " + t.getName() + "\n" + sw;
            File f = new File(ctx.getExternalFilesDir(null), "crash-latest.txt");
            PrintWriter pw = new PrintWriter(f, "UTF-8");
            pw.write(txt.length() > 6000 ? txt.substring(txt.length() - 6000) : txt);
            pw.close();
        } catch (Exception ignored) {}
        if (previous != null) previous.uncaughtException(t, e);
    }

    /** متن آخرین خطا (یا null) */
    public static String lastCrash(Context c) {
        try {
            File f = new File(c.getExternalFilesDir(null), "crash-latest.txt");
            if (!f.exists()) return null;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = r.readLine()) != null) sb.append(ln).append('\n');
            r.close();
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearCrash(Context c) {
        try {
            new File(c.getExternalFilesDir(null), "crash-latest.txt").delete();
        } catch (Exception ignored) {}
    }
}

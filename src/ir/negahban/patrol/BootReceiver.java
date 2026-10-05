package ir.negahban.patrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.Executors;

/** بعد از ری‌استارت دستگاه: برنامهٔ شب را بازسازی و آلارم‌ها را دوباره بچین */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context c, Intent i) {
        final PendingResult pr = goAsync();
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override public void run() {
                try {
                    Scheduler.ensurePlan(c);
                    Sync.drain(c);
                } catch (Exception ignored) {
                } finally {
                    pr.finish();
                }
            }
        });
    }
}

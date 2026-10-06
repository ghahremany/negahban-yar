package ir.negahban.patrol;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** گیت به‌روزرسانی اجباری — تا نصب نسخهٔ جدید، برنامه بسته میماند */
public class ForceUpdateActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(0xFF0F3D56);
        setContentView(root);

        TextView logo = new TextView(this);
        logo.setText("🔄");
        logo.setTextSize(52);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);

        TextView t = new TextView(this);
        t.setText("نسخهٔ جدید نگهبان‌یار منتشر شده");
        t.setTextSize(22);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(6));
        root.addView(t);

        TextView s = new TextView(this);
        s.setText("نسخهٔ نصب‌شده: v" + Scheduler.fa(Updater.localVersion(this))
                + "\nنسخهٔ جدید: v" + Scheduler.fa(Cfg.p(this).getString("forceTag", "?"))
                + "\n\nبرای ادامه باید به‌روزرسانی کنی.\nداده‌های تو همگی حفظ می‌شوند.");
        s.setTextSize(15);
        s.setTextColor(0xCCFFFFFF);
        s.setGravity(Gravity.CENTER);
        root.addView(s);

        Button go = new Button(this);
        go.setText("⬇️ دانلود و نصب نسخهٔ جدید");
        go.setTextColor(Color.WHITE);
        go.setBackgroundResource(ir.negahban.patrol.R.drawable.btn_primary);
        go.setAllCaps(false);
        go.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(22);
        go.setLayoutParams(lp);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                UpdaterUi.run(ForceUpdateActivity.this, false);
                // بعد از بستهشدن دیالوگ دوباره چک میکنیم؛ اگر نصب شد، این اکتیویتی دیگر باز نمیشود
            }
        });
        root.addView(go);

        TextView note = new TextView(this);
        note.setText("پس از نصب، برنامه به‌طور خودکار با نسخهٔ جدید بالا می‌آید.");
        note.setTextSize(12);
        note.setTextColor(0x80FFFFFF);
        note.setPadding(0, dp(14), 0, 0);
        root.addView(note);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

package ir.negahban.modir;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** صفحهٔ فعال‌سازی: وضعیت، ورود کد، ارتباط برای خرید — گیت اصلی برنامه */
public class LicenseActivity extends Activity {

    public static final String PAGE_URL = "https://negahbanyar.xo.je/";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { Cfg.fixLongs(this); } catch (Exception ignored) { }
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(0xFF0F3D56);
        setContentView(root);

        TextView logo = new TextView(this);
        logo.setText("🛡 نگهبان‌یار");
        logo.setTextSize(30);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);

        TextView sub = new TextView(this);
        sub.setText("سامانهٔ گشت شبانهٔ QR + گزارش خودکار در بله");
        sub.setTextColor(0xB3FFFFFF);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        // کارت وضعیت
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        int st = License.status(this);
        bg.setColor(st == 2 ? 0xFFFFEBEE : Color.WHITE);
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);

        TextView status = new TextView(this);
        if (st == 0) {
            status.setText("✅ فعال‌سازی کامل تا " + Scheduler.jalaliDate(License.expiry(this))
                    + "\n(" + Scheduler.fa(String.valueOf(License.daysLeft(this))) + " روز باقی‌مانده)");
        } else if (st == 1) {
            status.setText("🎁 دورهٔ آزمایشی رایگان — " + Scheduler.fa(String.valueOf(License.daysLeft(this)))
                    + " روز باقی‌مانده\n\nبرای استفادهٔ بی‌وقفه، کد فعال‌سازی تهیه کن.");
        } else {
            status.setText("⛔ دورهٔ استفاده به پایان رسیده\n\nبرای ادامه، کد فعال‌سازی جدید تهیه و وارد کن.");
        }
        status.setTextSize(16);
        status.setTextColor(st == 2 ? 0xFFB71C1C : 0xFF212121);
        status.setGravity(Gravity.CENTER);
        card.addView(status);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(8);
        card.setLayoutParams(clp);
        root.addView(card);

        // ورود کد
        TextView lbl = new TextView(this);
        lbl.setText("کد فعال‌سازی:");
        lbl.setTextColor(Color.WHITE);
        lbl.setPadding(0, dp(18), 0, dp(4));
        root.addView(lbl);

        final EditText et = new EditText(this);
        et.setHint("NGY-XXXX-XXXXXXXX");
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0x66FFFFFF);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        et.setTextSize(16);
        root.addView(et);

        Button act = new Button(this);
        act.setText("فعال‌سازی");
        act.setTextColor(Color.WHITE);
        act.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(52));
        alp.topMargin = dp(10);
        act.setLayoutParams(alp);
        act.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                long ex = License.expiryOf(et.getText().toString());
                if (ex <= 0) {
                    Toast.makeText(LicenseActivity.this, "⛔ کد نامعتبر است — دوباره چک کن", Toast.LENGTH_LONG).show();
                    return;
                }
                License.apply(LicenseActivity.this, ex);
                Toast.makeText(LicenseActivity.this, "🎉 فعال شد تا " + Scheduler.jalaliDate(ex), Toast.LENGTH_LONG).show();
                finish();
            }
        });
        root.addView(act);

        // خرید کد فعال‌سازی
        Button buy = new Button(this);
        buy.setText("📞 تهیهٔ کد فعال‌سازی (ماهانه / سالانه)");
        buy.setTextColor(Color.WHITE);
        buy.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(52));
        blp.topMargin = dp(10);
        buy.setLayoutParams(blp);
        buy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(LicenseActivity.this)
                        .setTitle("تهیهٔ کد فعال‌سازی")
                        .setMessage("فعال‌سازی مدیر یار ماهانه و سالانه از توسعه‌دهنده تهیه می‌شود:\n\nمحمد جواد قهرمانی\n" + Scheduler.fa("09127285065"))
                        .setPositiveButton("📞 تماس", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:09127285065"))); } catch (Exception ignored) {}
                            }
                        })
                        .setNegativeButton("بستن", null).show();
            }
        });
        root.addView(buy);

        // صفحهٔ محصول
        Button page = new Button(this);
        page.setText("🌐 صفحهٔ معرفی محصول");
        page.setTextColor(Color.WHITE);
        page.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(48));
        plp.topMargin = dp(10);
        page.setLayoutParams(plp);
        page.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PAGE_URL))); } catch (Exception ignored) {}
            }
        });
        root.addView(page);

        TextView foot = new TextView(this);
        foot.setText("ساخته‌شده با ❤ توسط محمد جواد قهرمانی\nاستفادهٔ تجاری این نرم‌افزار بدون فعال‌سازی مجاز نیست");
        foot.setTextColor(0x80FFFFFF);
        foot.setTextSize(12);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(20), 0, 0);
        root.addView(foot);
    }

    @Override
    public void onBackPressed() {
        // اگر فعال است/آزمایشی است، اجازهٔ خروج عادی؛ وگرنه هم در همین صفحه میماند
        if (License.status(this) != 2) finish();
        else Toast.makeText(this, "برای استفاده از برنامه، فعال‌سازی لازم است", Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

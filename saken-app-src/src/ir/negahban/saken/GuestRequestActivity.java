package ir.negahban.saken;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * درخواست مهمان از سمت ساکن — از طریق سرور:
 * ساکن ثبت می‌کند ← نگهبان (یا مدیر) تأیید می‌کند ← هم‌زمان مدیر و نگهبان در جریان‌اند.
 * نتیجه با GU_OK / GU_NO به صندوق همین ساکن برمی‌گردد و اینجا پیگیری می‌شود.
 */
public class GuestRequestActivity extends Activity {

    EditText eName, eMobile, ePlate, eWhen;
    TextView tvState;
    Button btnCheck;
    volatile boolean stop = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.WHITE);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("🚶 درخواست مهمان");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("مشخصات مهمانت را ثبت کن؛ درخواست برای نگهبانی و مدیر می‌رود و بعد از تأیید، خبرش را همین‌جا می‌بینی.");
        hint.setTextSize(13);
        hint.setTextColor(0xFF607D8B);
        hint.setPadding(0, dp(10), 0, 0);
        root.addView(hint);

        // وضعیت درخواست قبلی
        tvState = new TextView(this);
        tvState.setTextSize(14);
        tvState.setPadding(dp(4), dp(10), dp(4), 0);
        root.addView(tvState);
        renderState();

        btnCheck = new Button(this);
        btnCheck.setText("🔄 بررسی وضعیت");
        btnCheck.setAllCaps(false);
        btnCheck.setTextColor(Color.WHITE);
        btnCheck.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(46));
        clp.topMargin = dp(6);
        btnCheck.setLayoutParams(clp);
        btnCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkOnly(); }
        });
        root.addView(btnCheck);
        refreshButtons();

        int pad = dp(10);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF7F9FA);
        bg.setCornerRadius(dp(12));
        form.setBackground(bg);
        form.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(12);
        form.setLayoutParams(flp);

        eName = new EditText(this);
        eName.setHint("نام و نام خانوادگی مهمان");
        eMobile = new EditText(this);
        eMobile.setHint("شمارهٔ همراه مهمان (مثل 0912…)");
        eMobile.setInputType(InputType.TYPE_CLASS_PHONE);
        ePlate = new EditText(this);
        ePlate.setHint("شمارهٔ خودرو (اختیاری)");
        eWhen = new EditText(this);
        eWhen.setHint("زمان تقریبی حضور (مثل امشب ۸ یا فردا ظهر)");
        form.addView(eName);
        form.addView(eMobile);
        form.addView(ePlate);
        form.addView(eWhen);
        root.addView(form);

        Button send = new Button(this);
        send.setText("✅ ثبت درخواست و اطلاع به نگهبانی");
        send.setAllCaps(false);
        send.setTextColor(Color.WHITE);
        send.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(52));
        slp.topMargin = dp(12);
        send.setLayoutParams(slp);
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendRequest(); }
        });
        root.addView(send);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop = true;
    }

    // ---------- وضعیت فعلی ----------
    long guId()   { return Cfg.p(this).getLong("guId", 0); }
    String guName() { return Cfg.p(this).getString("guName", ""); }
    String guStatus() { return Cfg.p(this).getString("guStatus", ""); }

    void renderState() {
        String st = guStatus();
        if (guId() == 0 || st.isEmpty()) { tvState.setText(""); return; }
        if ("PENDING".equals(st)) {
            tvState.setText("⏳ درخواست «" + guName() + "» در انتظار تأیید نگهبانی است.");
            tvState.setTextColor(0xFF8D6E00);
        } else if ("OK".equals(st)) {
            tvState.setText("✅ «" + guName() + "» تأیید شد — نگهبانی در جریان است.");
            tvState.setTextColor(0xFF1B5E20);
        } else if ("NO".equals(st)) {
            tvState.setText("⛔ درخواست «" + guName() + "» تأیید نشد — با نگهبانی هماهنگ کن.");
            tvState.setTextColor(0xFFB71C1C);
        }
    }

    void refreshButtons() {
        boolean pending = guId() != 0 && "PENDING".equals(guStatus());
        btnCheck.setVisibility(pending ? View.VISIBLE : View.GONE);
    }

    // ---------- ارسال ----------
    void sendRequest() {
        final String name = eName.getText().toString().trim();
        final String mobile = eMobile.getText().toString().trim();
        if (name.isEmpty()) { toast("نام مهمان لازم است"); return; }
        if (mobile.length() < 10) { toast("شمارهٔ همراه معتبر نیست"); return; }
        final String plate = ePlate.getText().toString().trim();
        final String when = eWhen.getText().toString().trim();

        final String code = Hub.code(this);
        if (code.isEmpty()) { toast("اول کد ساختمان را در ⚙️ تنظیمات وارد کن"); return; }
        final String uid = Me.uid(this);
        if (uid.isEmpty()) { toast("اول در ⚙️ ثبت‌نام کن"); return; }

        toast("در حال ارسال…");
        final AlertDialog wait = progress("⏳ درخواستت ثبت شد و دست نگهبانی است…\nمنتظر تأیید می‌مانیم (تا ۲ دقیقه).");
        stop = false;
        new Thread(new Runnable() {
            @Override public void run() {
                Hub.Res r = Hub.guestReq(code, uid, name, mobile, plate, when);
                long id = 0;
                if (r.ok) {
                    try { id = new JSONObject(r.payload).optLong("id", 0); } catch (Exception ignored) { }
                }
                if (id <= 0) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            try { wait.dismiss(); } catch (Exception ignored) { }
                            Toast.makeText(GuestRequestActivity.this, "⛔ ثبت نشد — اینترنت را چک کن", Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                Cfg.p(GuestRequestActivity.this).edit()
                        .putLong("guId", id).putString("guName", name)
                        .putString("guStatus", "PENDING").apply();
                pollLoop(code, wait, 120000);
            }
        }).start();
    }

    void checkOnly() {
        final String code = Hub.code(this);
        if (code.isEmpty()) return;
        final AlertDialog wait = progress("در حال بررسی…");
        stop = false;
        new Thread(new Runnable() {
            @Override public void run() { pollLoop(code, wait, 20000); }
        }).start();
    }

    void pollLoop(String code, final AlertDialog wait, long ms) {
        long deadline = PatrolStore.now() + ms;
        long after = Cfg.p(this).getLong("guAfter", 0);
        final long myId = guId();
        String decided = "";
        while (PatrolStore.now() < deadline && !stop) {
            java.util.ArrayList<Hub.MsgItem> items = Hub.msgs(code, Me.inbox(this), after);
            for (Hub.MsgItem it : items) {
                after = Math.max(after, it.id);
                if ("GU_OK".equals(it.kind) || "GU_NO".equals(it.kind)) {
                    try {
                        JSONObject j = new JSONObject(it.payload);
                        if (j.optLong("id", -1) == myId) { decided = it.kind; break; }
                    } catch (Exception ignored) { }
                }
            }
            if (!decided.isEmpty()) break;
            try { Thread.sleep(3000); } catch (Exception e) { return; }
        }
        final long fAfter = after;
        final String fDecided = decided;
        Cfg.p(this).edit().putLong("guAfter", fAfter).apply();
        if (!fDecided.isEmpty())
            Cfg.p(this).edit().putString("guStatus", fDecided).apply();
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try { wait.dismiss(); } catch (Exception ignored) { }
                if (stop) return;
                renderState();
                refreshButtons();
                if ("GU_OK".equals(fDecided)) {
                    new AlertDialog.Builder(GuestRequestActivity.this)
                            .setTitle("✅ تأیید شد")
                            .setMessage("درخواست مهمان «" + guName() + "» تأیید شد و نگهبانی در جریان است.")
                            .setPositiveButton("باشه", null).show();
                } else if ("GU_NO".equals(fDecided)) {
                    new AlertDialog.Builder(GuestRequestActivity.this)
                            .setTitle("تأیید نشد")
                            .setMessage("درخواست مهمان «" + guName() + "» تأیید نشد.\nبرای هماهنگی با نگهبانی تماس بگیر.")
                            .setPositiveButton("باشه", null).show();
                } else {
                    Toast.makeText(GuestRequestActivity.this, "هنوز پاسخی نرسیده — بعداً «بررسی وضعیت» را بزن", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    AlertDialog progress(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(20), dp(20), dp(20), dp(10));
        AlertDialog d = new AlertDialog.Builder(this).setView(tv).setCancelable(false).create();
        d.show();
        return d;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}

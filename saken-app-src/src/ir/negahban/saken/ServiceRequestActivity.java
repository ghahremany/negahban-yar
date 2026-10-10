package ir.negahban.saken;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * درخواست‌های خدماتی ساکن (مشابه کلید پشت‌بام):
 * اسباب‌کشی و آسانسور — درخواست → مدیر تأیید می‌کند → نگهبانی مطلع می‌شود
 * extras: kind=MOVE|ELEV ، title ، emoji ، okHint
 */
public class ServiceRequestActivity extends Activity {

    String kind, emoji, okHint;
    TextView tvState;
    volatile boolean stop = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        kind = getIntent().getStringExtra("kind");
        emoji = getIntent().getStringExtra("emoji");
        okHint = getIntent().getStringExtra("okHint");
        String title = getIntent().getStringExtra("title");
        if (kind == null || kind.trim().isEmpty()) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.WHITE);
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(root);
        setContentView(sc);

        TextView head = new TextView(this);
        head.setText(emoji + " " + title);
        head.setTextSize(20);
        head.setTypeface(null, Typeface.BOLD);
        head.setTextColor(Color.WHITE);
        head.setBackgroundColor(0xFF0F3D56);
        head.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(head);

        TextView info = new TextView(this);
        info.setText("مسیر درخواست:\n۱) درخواستت برای مدیر ساختمان ارسال میشود\n۲) به محض بررسی، نتیجه با اعلان به تو اطلاع داده میشود\n۳) اگر مدیر تأیید کند، به نگهبانی هم اطلاع داده میشود\n۴) " + okHint);
        info.setTextSize(14);
        info.setPadding(dp(4), dp(14), dp(4), dp(14));
        root.addView(info);

        TextView me = new TextView(this);
        me.setText("درخواست به نام: " + Me.name(this) + " — واحد " + Scheduler.fa(Me.unit(this)));
        me.setTextSize(13);
        me.setTextColor(0xFF455A64);
        me.setPadding(dp(4), 0, dp(4), dp(10));
        root.addView(me);

        Button req = new Button(this);
        req.setText(emoji + " ثبت درخواست");
        req.setTextColor(Color.WHITE);
        req.setBackgroundResource(R.drawable.btn_primary);
        req.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(8);
        req.setLayoutParams(lp);
        root.addView(req);
        req.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ask(); }
        });

        tvState = new TextView(this);
        tvState.setTextSize(14);
        tvState.setPadding(dp(4), dp(16), dp(4), 0);
        root.addView(tvState);
    }

    static String reqKind(String k) { return k + "_REQ"; }

    void ask() {
        final String code = Hub.code(this);
        if (code.isEmpty()) { toast("اول کد ساختمان را در ⚙️ وارد کن"); return; }
        new AlertDialog.Builder(this)
                .setTitle(emoji + " درخواست")
                .setMessage("درخواستت برای مدیر ارسال شود؟")
                .setPositiveButton("بله", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { send(code); }
                })
                .setNegativeButton("انصراف", null).show();
    }

    void send(String code) {
        final String fCode = code;
        toast("در حال ارسال…");
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    JSONObject j = new JSONObject();
                    j.put("uid", Me.uid(ServiceRequestActivity.this));
                    j.put("name", Me.name(ServiceRequestActivity.this));
                    j.put("unit", Me.unit(ServiceRequestActivity.this));
                    j.put("mobile", Me.mobile(ServiceRequestActivity.this));
                    j.put("ts", PatrolStore.now());
                    ok = Hub.msg(fCode, "modir", Me.inbox(ServiceRequestActivity.this), reqKind(kind), j.toString()).ok;
                } catch (Exception ignored) { }
                final boolean fOk = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (fOk) {
                            tvState.setText("✅ درخواست شما به اطلاع مدیر رسید.\nبه محض بررسی، خدمتتان اطلاع‌رسانی می‌گردد. 🙏");
                            new AlertDialog.Builder(ServiceRequestActivity.this)
                                    .setTitle("✅ درخواست ثبت شد")
                                    .setMessage("درخواست شما به اطلاع مدیر رسید.\nبه محض بررسی، خدمتتان اطلاع‌رسانی می‌گردد.")
                                    .setPositiveButton("باشه", null).show();
                        } else {
                            tvState.setText("⛔ ارسال نشد — اینترنت را چک کن و دوباره تلاش کن");
                            Toast.makeText(ServiceRequestActivity.this, "ارسال ناموفق — دوباره تلاش کن", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

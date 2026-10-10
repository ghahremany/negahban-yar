package ir.negahban.patrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 🧾 ثبت درخواستِ مراجعه‌کننده توسط نگهبان:
 * کلید پشت‌بام / آسانسور / اسباب‌کشی → دریافت اطلاعات شخص → گزارش به مدیر یار
 * extras: kind=ROOF|MOVE|ELEV ، title ، emoji
 */
public class ServiceDeskActivity extends Activity {

    EditText etName, etMobile, etUnit;
    TextView tvState;
    String kind, emoji, title;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        kind = getIntent().getStringExtra("kind");
        emoji = getIntent().getStringExtra("emoji");
        title = getIntent().getStringExtra("title");
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
        head.setText(emoji + " " + title + " — ثبت برای مراجعه‌کننده");
        head.setTextSize(19);
        head.setTypeface(null, Typeface.BOLD);
        head.setTextColor(Color.WHITE);
        head.setBackgroundColor(0xFF0F3D56);
        head.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(head);

        TextView info = new TextView(this);
        info.setText("مسیر درخواست:\n۱) اطلاعات شخص مراجعه‌کننده را وارد کن\n۲) درخواست برای مدیر ساختمان ارسال می‌شود\n۳) به محض بررسی مدیر، نتیجه با اعلان به همین برنامه اطلاع داده می‌شود");
        info.setTextSize(13.5f);
        info.setTextColor(0xFF455A64);
        info.setPadding(dp(4), dp(12), dp(4), dp(12));
        root.addView(info);

        root.addView(lbl("نام و نام خانوادگی *"));
        etName = field("مثلاً علی محمدی", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        root.addView(lbl("شمارهٔ همراه *"));
        etMobile = field("09xxxxxxxxx", InputType.TYPE_CLASS_PHONE);
        root.addView(lbl("شمارهٔ واحد (اختیاری)"));
        etUnit = field("مثلاً ۵", InputType.TYPE_CLASS_NUMBER);

        Button req = new Button(this);
        req.setText(emoji + " ارسال درخواست به مدیر");
        req.setTextColor(Color.WHITE);
        req.setBackgroundResource(R.drawable.btn_primary);
        req.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(16);
        req.setLayoutParams(lp);
        root.addView(req);
        req.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { send(); }
        });

        tvState = new TextView(this);
        tvState.setTextSize(14);
        tvState.setPadding(dp(4), dp(14), dp(4), 0);
        root.addView(tvState);
    }

    TextView lbl(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(13);
        tv.setTextColor(0xFF546E7A);
        tv.setPadding(dp(4), dp(10), dp(4), dp(2));
        return tv;
    }

    EditText field(String hint, int type) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(type);
        et.setTextSize(15);
        et.setBackgroundResource(R.drawable.bg_card);
        et.setPadding(dp(10), dp(10), dp(10), dp(10));
        return et;
    }

    String hubKind() {
        if ("ROOF".equals(kind)) return "ROOF_KEY_REQ";
        if ("MOVE".equals(kind)) return "MOVE_REQ";
        return "ELEV_REQ";
    }

    void send() {
        final String name = etName.getText().toString().trim();
        final String mobile = etMobile.getText().toString().trim().replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9');
        final String unit = etUnit.getText().toString().trim();
        if (name.length() < 3) { toast("نام را کامل وارد کن"); return; }
        if (!mobile.matches("(\\+98|98|0)?9\\d{9}")) { toast("شمارهٔ همراه ۱۱ رقمی درست وارد کن"); return; }
        final String code = Hub.code(this);
        if (code.isEmpty()) { toast("اول کد ساختمان را در ⚙️ تنظیمات وارد کن"); return; }
        new AlertDialog.Builder(this)
                .setTitle(emoji + " ثبت درخواست " + title)
                .setMessage("درخواست «" + name + "» برای مدیر ارسال شود؟")
                .setPositiveButton("بله", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { doSend(code, name, mobile, unit); }
                })
                .setNegativeButton("انصراف", null).show();
    }

    void doSend(final String code, final String name, final String mobile, final String unit) {
        toast("در حال ارسال…");
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    JSONObject j = new JSONObject();
                    j.put("name", name);
                    j.put("mobile", mobile);
                    j.put("unit", unit);
                    j.put("via", "نگهبان");
                    j.put("ts", PatrolStore.now());
                    ok = Hub.msg(code, "modir", "negahban", hubKind(), j.toString()).ok;
                } catch (Exception ignored) { }
                final boolean fOk = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (fOk) {
                            tvState.setText("✅ درخواست «" + name + "» به اطلاع مدیر رسید.\nبه محض بررسی، نتیجه با اعلان اطلاع داده می‌شود. 🙏");
                            new AlertDialog.Builder(ServiceDeskActivity.this)
                                    .setTitle("✅ درخواست ثبت شد")
                                    .setMessage("درخواست «" + name + "» به اطلاع مدیر رسید.\nبه محض بررسی، نتیجه با اعلان اطلاع داده می‌شود.")
                                    .setPositiveButton("باشه", new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int w) {
                                            // آمادهٔ ثبتِ نفر بعد
                                            etName.setText(""); etMobile.setText(""); etUnit.setText("");
                                        }
                                    }).show();
                        } else {
                            tvState.setText("⛔ ارسال نشد — اینترنت را چک کن و دوباره تلاش کن");
                            Toast.makeText(ServiceDeskActivity.this, "ارسال ناموفق — دوباره تلاش کن", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, "svcdesk").start();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

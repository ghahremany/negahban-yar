package ir.negahban.patrol;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * دریافت بسته: نوع (پست/پیک/…)، بارکد، گیرنده (نام/بلوک/همراه)
 * بعد از ثبت: پیامک به گیرنده + ثبت خبر در بله (صف ارسال)
 */
public class PackageActivity extends Activity {

    EditText eKind, eBarcode, eName, eBlock, eMobile;
    LinearLayout listBox;

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
        title.setText("📦 دریافت بسته");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        eKind = new EditText(this);
        eKind.setHint("نوع بسته (پست / پیک / دست‌به‌دست…)");
        eBarcode = new EditText(this);
        eBarcode.setHint("بارکد پستی / شمارهٔ رهگیری — اختیاری");
        eName = new EditText(this);
        eName.setHint("نام گیرنده");
        eBlock = new EditText(this);
        eBlock.setHint("بلوک گیرنده (مثل ب۳ یا طبقهٔ ۲)");
        eMobile = new EditText(this);
        eMobile.setHint("شمارهٔ همراه گیرنده (مثل 0912…)");
        eMobile.setInputType(InputType.TYPE_CLASS_PHONE);

        int pad = dp(10);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setBackground(round(0xFFF7F9FA, dp(12)));
        form.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-1, -2);
        fLp.topMargin = dp(12);
        form.setLayoutParams(fLp);
        form.addView(eKind);
        form.addView(eBarcode);
        form.addView(eName);
        form.addView(eBlock);
        form.addView(eMobile);
        root.addView(form);

        Button save = new Button(this);
        save.setText("✅ ثبت بسته و ارسال پیامک");
        save.setTextColor(Color.WHITE);
        save.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2);
        sLp.topMargin = dp(10);
        save.setLayoutParams(sLp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { savePackage(); }
        });
        root.addView(save);

        TextView hist = new TextView(this);
        hist.setText("📜 بسته‌های اخیر");
        hist.setTypeface(null, Typeface.BOLD);
        hist.setPadding(0, dp(18), 0, dp(4));
        root.addView(hist);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);
    }

    private void savePackage() {
        String name = eName.getText().toString().trim();
        String mobile = eMobile.getText().toString().trim();
        if (name.isEmpty()) { toast("نام گیرنده لازم است"); return; }
        if (mobile.length() < 10) { toast("شمارهٔ همراه گیرنده معتبر نیست"); return; }
        String kind = eKind.getText().toString().trim();
        if (kind.isEmpty()) kind = "بسته";
        String barcode = eBarcode.getText().toString().trim();
        String block = eBlock.getText().toString().trim();

        // ۱) ثبت در پایگاه‌داده
        PatrolStore st = new PatrolStore(this);
        final long id = st.addPackageTs(PatrolStore.now(), kind, barcode, name, mobile, block, "PENDING");
        st.close();

        // ۲) خبر در بله (همیشه — تاریخچه + پشتیبان)
        PatrolStore q = new PatrolStore(this);
        q.enq("📦 بسته ثبت شد\n👤 گیرنده: " + name
                + (block.isEmpty() ? "" : "\n🏢 بلوک: " + block)
                + "\n📦 نوع: " + kind
                + (barcode.isEmpty() ? "" : "\n🔖 بارکد: " + barcode)
                + "\n🕐 " + Scheduler.jalaliDate(PatrolStore.now()) + " — " + Scheduler.fa(Scheduler.hm(PatrolStore.now())));
        q.close();

        // ۳) پیامک به گیرنده
        sendSms(id, mobile, smsText(kind, barcode, name, block));

        eName.setText(""); eBlock.setText(""); eMobile.setText(""); eBarcode.setText("");
        refresh();
    }

    private String smsText(String kind, String barcode, String name, String block) {
        StringBuilder sb = new StringBuilder("سلام ");
        sb.append(name).append("، یک ").append(kind);
        if (!barcode.isEmpty()) sb.append(" با بارکد ").append(barcode);
        sb.append(" به نام شما در نگهبانی مجتمع تحویل شد");
        if (!block.isEmpty()) sb.append(" (بلوک ").append(block).append(")");
        sb.append(". مراجعه فرمایید. — نگهبانی");
        return sb.toString();
    }

    private void sendSms(final long id, final String mobile, final String text) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 21);
            PatrolStore st = new PatrolStore(this);
            st.setPkgSms(id, "NOPERM");
            st.close();
            toast("بسته ثبت شد — برای ارسال پیامک، اجازهٔ پیامک را بده و دوباره امتحان کن");
            return;
        }
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 23
                    ? getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            if (sm == null) {
                PatrolStore st = new PatrolStore(this);
                st.setPkgSms(id, "NOSIM");
                st.close();
                toast("بسته ثبت شد — پیامک در این گوشی ممکن نیست (سیم‌کارت؟) — خبر در بله ثبت شد");
                return;
            }
            final String action = "ir.negahban.SMS_SENT_" + id;
            PendingIntent pi = PendingIntent.getBroadcast(this, (int) (id % 100000),
                    new Intent(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    PatrolStore st = new PatrolStore(PackageActivity.this);
                    st.setPkgSms(id, getResultCode() == android.app.Activity.RESULT_OK ? "SENT" : "FAILED");
                    st.close();
                    try { unregisterReceiver(this); } catch (Exception ignored) {}
                    refresh();
                }
            }, new IntentFilter(action));

            ArrayList<String> parts = sm.divideMessage(text);
            if (parts.size() <= 1) {
                sm.sendTextMessage(mobile, null, text, pi, null);
            } else {
                ArrayList<PendingIntent> pis = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) pis.add(pi);
                sm.sendMultipartTextMessage(mobile, null, parts, pis, null);
            }
            toast("بسته ثبت شد — پیامک در حال ارسال…");
        } catch (Exception e) {
            PatrolStore st = new PatrolStore(this);
            st.setPkgSms(id, "FAILED");
            st.close();
            toast("بسته ثبت شد — ارسال پیامک ناموفق: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == 21) {
            toast(res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED
                    ? "اجازهٔ پیامک داده شد — بستهٔ بعدی پیامک می‌رود"
                    : "بدون اجازهٔ پیامک، فقط خبر در بله ثبت می‌شود");
            refresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        PatrolStore st = new PatrolStore(this);
        ArrayList<String[]> rows = st.allPackages(50);
        st.close();
        listBox.removeAllViews();
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("هنوز بسته‌ای ثبت نشده");
            empty.setPadding(0, dp(20), 0, 0);
            empty.setGravity(android.view.Gravity.CENTER);
            listBox.addView(empty);
            return;
        }
        for (String[] k : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(round(0xFFF7F9FA, dp(10)));
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            row.setLayoutParams(lp);

            boolean given = "1".equals(k[8]);
            TextView head = new TextView(this);
            head.setText("📦 " + k[2] + (k[3] == null || k[3].isEmpty() ? "" : "  🔖 " + k[3]) + (given ? "   ✅ تحویل شد" : ""));
            head.setTypeface(null, Typeface.BOLD);
            head.setTextSize(15);
            row.addView(head);

            TextView info = new TextView(this);
            info.setText("👤 " + k[4] + (k[6] == null || k[6].isEmpty() ? "" : "  |  🏢 بلوک " + k[6])
                    + "  |  📞 " + Scheduler.fa(k[5] == null ? "" : k[5])
                    + "\n🕐 " + Scheduler.fa(Scheduler.hm(Long.parseLong(k[1]))) + " — " + smsLabel(k[7])
                    + (given ? "\n🤝 تحویل: " + Scheduler.fa(Scheduler.hm(Long.parseLong(k[9]))) : ""));
            info.setTextSize(13);
            info.setTextColor(0xFF455A64);
            row.addView(info);

            if (!given) {
                Button givenBtn = new Button(this);
                givenBtn.setText("🤝 تحویل شد");
                givenBtn.setTextColor(Color.WHITE);
                givenBtn.setTextSize(13);
                givenBtn.setBackgroundResource(R.drawable.btn_dark);
                LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(-2, -2);
                gLp.topMargin = dp(6);
                givenBtn.setLayoutParams(gLp);
                final long pkgId = Long.parseLong(k[0]);
                givenBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        PatrolStore st2 = new PatrolStore(PackageActivity.this);
                        st2.markGiven(pkgId);
                        st2.close();
                        toast("تحویل ثبت شد ✅");
                        refresh();
                    }
                });
                row.addView(givenBtn);
            }
            listBox.addView(row);
        }
    }

    private String smsLabel(String s) {
        if ("SENT".equals(s)) return "✅ پیامک ارسال شد";
        if ("FAILED".equals(s)) return "⛔ پیامک ناموفق";
        if ("NOPERM".equals(s)) return "⚠️ اجازهٔ پیامک داده نشده";
        if ("NOSIM".equals(s)) return "⚠️ سیم‌کارت/پیامک در دسترس نیست";
        return "… در حال ارسال";
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private android.graphics.drawable.Drawable round(int color, float radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

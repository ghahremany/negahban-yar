package ir.negahban.modir;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** ثبت نگهبان‌ها: نام، همراه، شیفت (ساعت + روزها) — و انتخاب «نگهبان امشب» */
public class GuardActivity extends Activity {

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
        title.setText("👤 نگهبان‌ها و شیفت‌ها");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("نگهبانِ انتخاب‌شده به‌عنوان «امشب» در گزارش صبح بله و صفحهٔ اصلی نمایش داده می‌شود.");
        hint.setTextSize(13);
        hint.setTextColor(0xFF607D8B);
        hint.setPadding(0, dp(10), 0, 0);
        root.addView(hint);

        Button add = new Button(this);
        add.setText("➕ افزودن نگهبان");
        add.setTextColor(Color.WHITE);
        add.setBackgroundResource(R.drawable.btn_primary);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        add.setLayoutParams(lp);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editDialog(-1); }
        });
        root.addView(add);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        listBox.removeAllViews();
        JSONArray gs = Cfg.guards(this);
        String active = Cfg.p(this).getString("activeGuardPhone", "");
        if (gs.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("هنوز نگهبانی ثبت نشده — «افزودن نگهبان» را بزن");
            empty.setPadding(0, dp(24), 0, 0);
            empty.setGravity(android.view.Gravity.CENTER);
            listBox.addView(empty);
            return;
        }
        for (int i = 0; i < gs.length(); i++) {
            final JSONObject g = gs.optJSONObject(i);
            if (g == null) continue;
            listBox.addView(card(g, g.optString("phone").equals(active), i));
        }
    }

    private View card(final JSONObject g, final boolean isActive, final int idx) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isActive ? 0xFFE8F8F5 : 0xFFF7F9FA);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), isActive ? 0xFF12B5A5 : 0xFFE0E0E0);
        card.setBackground(bg);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);

        android.widget.ImageView av = new android.widget.ImageView(this);
        android.graphics.Bitmap ph = loadPhoto(this, g.optString("phone", ""), 96);
        if (ph != null) {
            av.setImageBitmap(ph);
            av.setBackgroundResource(R.drawable.btn_dark);
            av.setClipToOutline(true);
        } else {
            av.setImageResource(android.R.drawable.ic_menu_myplaces);
            av.setBackgroundResource(R.drawable.btn_dark);
            av.setPadding(dp(10), dp(10), dp(10), dp(10));
        }
        av.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        avLp.setMargins(0, 0, dp(10), 0);
        av.setLayoutParams(avLp);
        head.addView(av);

        TextView name = new TextView(this);
        name.setText(g.optString("name", "—") + (isActive ? "   ✅ نگهبان امشب" : ""));
        name.setTextSize(16);
        name.setTypeface(null, Typeface.BOLD);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(head);

        TextView info = new TextView(this);
        info.setText("📞 " + Scheduler.fa(g.optString("phone", "")) + "\n🕒 " + Cfg.guardShiftText(g));
        info.setTextSize(14);
        info.setPadding(0, dp(4), 0, 0);
        card.addView(info);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);

        Button activeBtn = new Button(this);
        activeBtn.setText(isActive ? "لغو «امشب»" : "نگهبان امشب باش");
        activeBtn.setAllCaps(false);
        activeBtn.setTextColor(Color.WHITE);
        activeBtn.setTextSize(13);
        activeBtn.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, -2, 1f);
        aLp.setMargins(0, 0, dp(4), 0);
        activeBtn.setLayoutParams(aLp);
        activeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Cfg.setActiveGuard(GuardActivity.this, isActive ? "" : g.optString("phone"));
                refresh();
            }
        });
        row.addView(activeBtn);

        Button editBtn = new Button(this);
        editBtn.setText("✏️ ویرایش");
        editBtn.setAllCaps(false);
        editBtn.setTextColor(Color.WHITE);
        editBtn.setTextSize(13);
        editBtn.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(0, -2, 1f);
        eLp.setMargins(dp(4), 0, dp(4), 0);
        editBtn.setLayoutParams(eLp);
        editBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editDialog(idx); }
        });
        row.addView(editBtn);

        Button delBtn = new Button(this);
        delBtn.setText("🗑");
        delBtn.setAllCaps(false);
        delBtn.setTextColor(Color.WHITE);
        delBtn.setTextSize(13);
        delBtn.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(56), -2);
        dLp.setMargins(dp(4), 0, 0, 0);
        delBtn.setLayoutParams(dLp);
        delBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(GuardActivity.this)
                        .setTitle("حذف نگهبان")
                        .setMessage("«" + g.optString("name") + "» حذف شود؟")
                        .setPositiveButton("حذف", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                JSONArray gs = Cfg.guards(GuardActivity.this);
                                JSONArray out = new JSONArray();
                                for (int k = 0; k < gs.length(); k++) {
                                    if (k != idx) out.put(gs.optJSONObject(k));
                                }
                                Cfg.saveGuards(GuardActivity.this, out);
                                if (g.optString("phone").equals(Cfg.p(GuardActivity.this).getString("activeGuardPhone", ""))) {
                                    Cfg.setActiveGuard(GuardActivity.this, "");
                                }
                                refresh();
                            }
                        })
                        .setNegativeButton("انصراف", null).show();
            }
        });
        row.addView(delBtn);

        card.addView(row);
        return card;
    }

    // ---------- عکس نگهبان ----------

    static java.io.File photoFile(android.content.Context c, String phone) {
        return new java.io.File(c.getFilesDir(), "guard-" + phone.replaceAll("[^0-9]", "") + ".jpg");
    }

    static android.graphics.Bitmap loadPhoto(android.content.Context c, String phone, int target) {
        try {
            java.io.File f = photoFile(c, phone);
            if (!f.exists()) return null;
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= target) sample *= 2;
            o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = sample;
            return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    private String pendingPhotoPhone = null;

    private void pickPhoto(String phone) {
        pendingPhotoPhone = phone.replaceAll("[^0-9]", "");
        try {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*"), 93);
        } catch (Exception e) {
            toast("انتخاب‌گر تصویر باز نشد: " + e);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 93 && res == RESULT_OK && data != null && data.getData() != null && pendingPhotoPhone != null) {
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                java.io.InputStream in = getContentResolver().openInputStream(data.getData());
                android.graphics.BitmapFactory.decodeStream(in, null, o);
                if (in != null) in.close();
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 512) sample *= 2;
                o = new android.graphics.BitmapFactory.Options();
                o.inSampleSize = sample;
                java.io.InputStream in2 = getContentResolver().openInputStream(data.getData());
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in2, null, o);
                if (in2 != null) in2.close();
                if (bmp == null) { toast("تصویر خوانده نشد"); return; }
                java.io.File f = photoFile(this, pendingPhotoPhone);
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fo);
                fo.close();
                toast("✅ عکس ذخیره شد");
                refresh();
            } catch (Exception e) {
                toast("ذخیرهٔ عکس ناموفق: " + e);
            }
        }
    }

    /** idx = -1 برای افزودن */
    private void editDialog(final int idx) {
        final JSONObject cur = idx >= 0 ? Cfg.guards(this).optJSONObject(idx) : null;
        final EditText eName = new EditText(this);
        final EditText ePhone = new EditText(this);
        final EditText eStart = new EditText(this);
        final EditText eEnd = new EditText(this);
        final EditText eDays = new EditText(this);
        eName.setHint("نام و نام خانوادگی");
        ePhone.setHint("شمارهٔ همراه (مثل 0912…)");
        ePhone.setInputType(InputType.TYPE_CLASS_PHONE);
        eStart.setHint("شروع شیفت (مثل 22:00)");
        eEnd.setHint("پایان شیفت (مثل 06:00)");
        eDays.setHint("روزهای شیفت (مثلاً: شنبه تا چهارشنبه)");

        if (cur != null) {
            eName.setText(cur.optString("name"));
            ePhone.setText(cur.optString("phone"));
            eStart.setText(cur.optString("shStart"));
            eEnd.setText(cur.optString("shEnd"));
            eDays.setText(cur.optString("days"));
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(eName);
        box.addView(ePhone);
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        eStart.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        eEnd.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        times.addView(eStart);
        times.addView(eEnd);
        box.addView(times);
        box.addView(eDays);

        final String curPhone0 = cur != null ? cur.optString("phone", "").replaceAll("[^0-9]", "") : "";
        android.widget.Button btnPhoto = new android.widget.Button(this);
        btnPhoto.setText(curPhone0.isEmpty() ? "🖼 انتخاب عکس (بعد از ذخیره با شماره)" : "🖼 انتخاب/تغییر عکس نگهبان");
        btnPhoto.setAllCaps(false);
        btnPhoto.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(View v) {
                String ph = ePhone.getText().toString().trim().replaceAll("[^0-9]", "");
                if (ph.length() < 8) { toast("اول شمارهٔ همراه را وارد کن، بعد عکس"); return; }
                pickPhoto(ph);
            }
        });
        box.addView(btnPhoto);

        new AlertDialog.Builder(this)
                .setTitle(idx >= 0 ? "ویرایش نگهبان" : "افزودن نگهبان")
                .setView(box)
                .setPositiveButton("ذخیره", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String name = eName.getText().toString().trim();
                        String phone = ePhone.getText().toString().trim();
                        if (name.isEmpty() || phone.length() < 8) {
                            toast("نام و شمارهٔ همراه معتبر لازم است");
                            return;
                        }
                        JSONArray gs = Cfg.guards(GuardActivity.this);
                        JSONArray out = new JSONArray();
                        boolean saved = false;
                        for (int k = 0; k < gs.length(); k++) {
                            if (k == idx) {
                                out.put(makeGuard(name, phone, eStart, eEnd, eDays));
                                saved = true;
                            } else if (gs.optJSONObject(k) != null) {
                                out.put(gs.optJSONObject(k));
                            }
                        }
                        if (!saved) out.put(makeGuard(name, phone, eStart, eEnd, eDays));
                        Cfg.saveGuards(GuardActivity.this, out);
                        toast("ذخیره شد ✅");
                        refresh();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private JSONObject makeGuard(String name, String phone, EditText eStart, EditText eEnd, EditText eDays) {
        JSONObject g = new JSONObject();
        try {
            g.put("name", name);
            g.put("phone", phone);
            g.put("shStart", eStart.getText().toString().trim());
            g.put("shEnd", eEnd.getText().toString().trim());
            g.put("days", eDays.getText().toString().trim());
        } catch (Exception ignored) {}
        return g;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

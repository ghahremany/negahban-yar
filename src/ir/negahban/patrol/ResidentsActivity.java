package ir.negahban.patrol;

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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * ساکنین: ثبت (نام، همراه، پلاک، خودرو، پارکینگ) + جستجو.
 * حالت مدیر (admin=true): افزودن/ویرایش/حذف — حالت نگهبان: فقط جستجو و تماس.
 */
public class ResidentsActivity extends Activity {

    boolean admin;
    LinearLayout listBox;
    EditText etQuery;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        admin = getIntent().getBooleanExtra("admin", false);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.WHITE);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText(admin ? "👥 مدیریت ساکنین" : "🔎 جستجوی ساکنین");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xFF0F3D56);
        title.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(title);

        // نوار جستجو
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2);
        sLp.topMargin = dp(12);
        searchRow.setLayoutParams(sLp);

        etQuery = new EditText(this);
        etQuery.setHint("نام، شمارهٔ پلاک، پارکینگ یا همراه…");
        etQuery.setInputType(InputType.TYPE_CLASS_TEXT);
        etQuery.setTextSize(15);
        etQuery.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        searchRow.addView(etQuery);

        Button btnSearch = new Button(this);
        btnSearch.setText("🔎");
        btnSearch.setBackgroundResource(R.drawable.btn_dark);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(56), -2);
        bLp.setMargins(dp(6), 0, 0, 0);
        btnSearch.setLayoutParams(bLp);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        searchRow.addView(btnSearch);
        root.addView(searchRow);

        if (admin) {
            Button add = new Button(this);
            add.setText("➕ افزودن ساکن");
            add.setTextColor(Color.WHITE);
            add.setBackgroundResource(R.drawable.btn_primary);
            LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(-1, -2);
            aLp.topMargin = dp(10);
            add.setLayoutParams(aLp);
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { editDialog(-1, null); }
            });
            root.addView(add);
        }

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
        PatrolStore st = new PatrolStore(this);
        final ArrayList<String[]> rows = st.searchRes(etQuery == null ? "" : etQuery.getText().toString());
        int total = st.resCount();
        st.close();

        listBox.removeAllViews();
        TextView count = new TextView(this);
        count.setText(rows.size() + " نتیجه" + (rows.size() >= 200 ? " (اولین ۲۰۰)" : "") + " — مجموع: " + Scheduler.fa(String.valueOf(total)) + " ساکن");
        count.setTextSize(13);
        count.setTextColor(0xFF607D8B);
        count.setPadding(0, dp(8), 0, dp(2));
        listBox.addView(count);

        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(etQuery != null && !etQuery.getText().toString().trim().isEmpty()
                    ? "چیزی پیدا نشد" : "هنوز ساکنی ثبت نشده" + (admin ? " — «افزودن ساکن» را بزن" : ""));
            empty.setPadding(0, dp(24), 0, 0);
            empty.setGravity(android.view.Gravity.CENTER);
            listBox.addView(empty);
            return;
        }
        for (final String[] r : rows) listBox.addView(resRow(r));
    }

    private View resRow(final String[] r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF7F9FA);
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = new TextView(this);
        name.setText(r[1]);
        name.setTextSize(15);
        name.setTypeface(null, Typeface.BOLD);
        texts.addView(name);

        TextView info = new TextView(this);
        info.setText("🚗 " + r[3] + (r[4].isEmpty() ? "" : " (" + r[4] + ")") + "  |  🅿 " + r[5] + "  |  📞 " + Scheduler.fa(r[2]));
        info.setTextSize(13);
        info.setTextColor(0xFF455A64);
        texts.addView(info);

        row.addView(texts);

        if (admin) {
            Button del = new Button(this);
            del.setText("🗑");
            del.setBackgroundResource(R.drawable.btn_dark);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(52), -2);
            dLp.setMargins(dp(6), 0, 0, 0);
            del.setLayoutParams(dLp);
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(ResidentsActivity.this)
                            .setTitle("حذف ساکن")
                            .setMessage("«" + r[1] + "» حذف شود؟")
                            .setPositiveButton("حذف", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    PatrolStore st = new PatrolStore(ResidentsActivity.this);
                                    st.deleteRes(Long.parseLong(r[0]));
                                    st.close();
                                    refresh();
                                }
                            })
                            .setNegativeButton("انصراف", null).show();
                }
            });
            row.addView(del);
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { detailDialog(r); }
        });
        return row;
    }

    private void detailDialog(final String[] r) {
        String msg = "نام: " + r[1]
                + "\nهمراه: " + Scheduler.fa(r[2])
                + "\nپلاک: " + r[3]
                + "\nخودرو: " + (r[4].isEmpty() ? "—" : r[4])
                + "\nپارکینگ: " + r[5];
        AlertDialog.Builder ab = new AlertDialog.Builder(this)
                .setTitle("اطلاعات ساکن")
                .setMessage(msg)
                .setPositiveButton("📞 تماس", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + r[2]))); } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("بستن", null);
        if (admin) {
            ab.setNeutralButton("✏️ ویرایش", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { editDialog(Long.parseLong(r[0]), r); }
            });
        }
        ab.show();
    }

    /** id = -1 برای افزودن */
    private void editDialog(final long id, final String[] cur) {
        final EditText eName = new EditText(this);
        final EditText eMobile = new EditText(this);
        final EditText ePlate = new EditText(this);
        final EditText eCar = new EditText(this);
        final EditText eParking = new EditText(this);
        eName.setHint("نام و نام خانوادگی");
        eMobile.setHint("شمارهٔ همراه (مثل 0912…)");
        eMobile.setInputType(InputType.TYPE_CLASS_PHONE);
        ePlate.setHint("پلاک خودرو (مثل 12ب345ایران66)");
        eCar.setHint("نوع خودرو (مثل پژو ۲۰۷)");
        eParking.setHint("شمارهٔ پارکینگ");
        if (cur != null) {
            eName.setText(cur[1]); eMobile.setText(cur[2]); ePlate.setText(cur[3]);
            eCar.setText(cur[4]); eParking.setText(cur[5]);
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(eName); box.addView(eMobile); box.addView(ePlate);
        box.addView(eCar); box.addView(eParking);

        new AlertDialog.Builder(this)
                .setTitle(id >= 0 ? "ویرایش ساکن" : "افزودن ساکن")
                .setView(box)
                .setPositiveButton("ذخیره", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String name = eName.getText().toString().trim();
                        if (name.isEmpty()) { toast("نام لازم است"); return; }
                        PatrolStore st = new PatrolStore(ResidentsActivity.this);
                        if (id >= 0) st.updateRes(id, name,
                                eMobile.getText().toString().trim(), ePlate.getText().toString().trim(),
                                eCar.getText().toString().trim(), eParking.getText().toString().trim());
                        else st.addRes(name,
                                eMobile.getText().toString().trim(), ePlate.getText().toString().trim(),
                                eCar.getText().toString().trim(), eParking.getText().toString().trim());
                        st.close();
                        toast("ذخیره شد ✅");
                        refresh();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

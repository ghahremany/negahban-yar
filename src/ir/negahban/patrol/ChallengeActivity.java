package ir.negahban.patrol;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONObject;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** چالش بیداری: عدد تصادفی ۴ رقمی، ۹۰ ثانیه مهلت */
public class ChallengeActivity extends Activity {

    private boolean finished = false;
    private long startTs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        setContentView(R.layout.activity_challenge);
        startTs = PatrolStore.now();

        final JSONObject plan = Scheduler.getPlan(this);
        final String code = String.valueOf(plan.optInt("code", 0));

        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(700);
            }
        } catch (Exception ignored) {}

        final TextView tvTimer = findViewById(R.id.tvTimer);
        final EditText et = findViewById(R.id.etAnswer);
        ((TextView) findViewById(R.id.tvCode)).setText(Scheduler.fa(code));

        findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (et.getText().toString().trim().equals(code)) {
                    finishWith(true);
                } else {
                    Toast.makeText(ChallengeActivity.this, "عدد اشتباه است", Toast.LENGTH_SHORT).show();
                }
            }
        });

        new CountDownTimer(90000, 1000) {
            @Override public void onTick(long ms) {
                tvTimer.setText(Scheduler.fa(String.valueOf(ms / 1000)) + " ثانیه");
            }
            @Override public void onFinish() {
                if (!finished) finishWith(false);
            }
        }.start();
    }

    private void finishWith(boolean ok) {
        if (finished) return;
        finished = true;
        Scheduler.markChallengeDone(this, ok);
        PatrolStore st = new PatrolStore(this);
        st.addEv(ok ? "CHALLENGE_OK" : "CHALLENGE_FAIL", null, null, "secs=" + (PatrolStore.now() - startTs) / 1000);
        st.close();
        finish();
    }

    @Override
    public void onBackPressed() {
        // بستن با دکمهٔ برگشت = بی‌پاسخ
        finishWith(false);
    }
}

package ir.negahban.patrol;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.Result;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** اسکنر QR با دوربین — کاملاً آفلاین */
public class ScannerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private Camera camera;
    private SurfaceView surface;
    private TextView tvResult;
    private boolean torch = false;
    private boolean done = false;

    private final MultiFormatReader reader = new MultiFormatReader();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_scanner);
        surface = findViewById(R.id.surface);
        tvResult = findViewById(R.id.tvResult);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);

        surface.getHolder().addCallback(this);
        surface.getHolder().setFixedSize(640, 480);

        findViewById(R.id.btnTorch).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTorch(); }
        });

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 8);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) { openCam(holder); }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) { releaseCam(); }

    private void openCam(SurfaceHolder holder) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int camId = 0;
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { camId = i; break; }
            }
            camera = Camera.open(camId);
            Camera.CameraInfo info2 = new Camera.CameraInfo();
            Camera.getCameraInfo(camId, info2);
            Camera.Parameters p = camera.getParameters();

            // کوچک‌ترین سایز مناسب برای سرعت
            Camera.Size best = null;
            for (Camera.Size s : p.getSupportedPreviewSizes()) {
                if (s.width * s.height > 1280 * 720) continue;
                if (best == null || s.width * s.height > best.width * best.height) best = s;
            }
            if (best != null) p.setPreviewSize(best.width, best.height);
            if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            camera.setParameters(p);
            camera.setDisplayOrientation(displayOrientation(info2));
            camera.setPreviewDisplay(holder);
            int bufSize = 1280 * 720 * 3 / 2;
            Camera.Size ps = p.getPreviewSize();
            if (ps != null) bufSize = ps.width * ps.height * 3 / 2;
            for (int i = 0; i < 3; i++) camera.addCallbackBuffer(new byte[bufSize]);
            camera.setPreviewCallbackWithBuffer(this);
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "بازکردن دوربین ممکن نشد: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private int displayOrientation(Camera.CameraInfo info) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == 1 ? 90 : (rotation == 2 ? 180 : (rotation == 3 ? 270 : 0));
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    private void releaseCam() {
        try { if (camera != null) { camera.stopPreview(); camera.release(); } } catch (Exception ignored) {}
        camera = null;
    }

    private void toggleTorch() {
        try {
            if (camera == null) return;
            Camera.Parameters p = camera.getParameters();
            torch = !torch;
            p.setFlashMode(torch ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(p);
        } catch (Exception ignored) {}
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera cam) {
        if (done || cam == null) return;
        final Camera.Size ps = cam.getParameters().getPreviewSize();
        decoder.execute(new Runnable() {
            @Override public void run() {
                try {
                    PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(data, ps.width, ps.height, 0, 0, ps.width, ps.height, false);
                    Result r = null;
                    try { r = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(src))); }
                    catch (Exception ignored) {}
                    if (r == null) {
                        try { r = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(src.rotateCounterClockwise()))); }
                        catch (Exception ignored) {}
                    }
                    reader.reset();
                    final String text = r == null ? null : r.getText();
                    ui.post(new Runnable() {
                        @Override public void run() { onDecoded(text, data, cam); }
                    });
                } catch (Exception e) {
                    try { cam.addCallbackBuffer(data); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void onDecoded(String text, byte[] data, Camera cam) {
        try {
            if (!done && text != null) handleQr(text);
        } finally {
            try { if (camera != null && !done) cam.addCallbackBuffer(data); } catch (Exception ignored) {}
        }
    }

    private void handleQr(String text) {
        Context c = this;
        final String stationId = Crypto.validatePlaque(Cfg.keyHex(c), text == null ? "" : text.trim());
        if (stationId != null) {
            int id;
            try { id = Integer.parseInt(stationId); } catch (Exception e) { id = 0; }
            PatrolStore st = new PatrolStore(c);
            final String flag = st.recordScan(c, id);
            st.close();

            done = true;
            beep(true);
            tvResult.setBackgroundColor(0xCC1E8449);
            tvResult.setText("✅ ثبت شد: " + Cfg.stationName(c, id) + (flag.equals("GAP") ? "\n(فاصلهٔ زمانی مشکوک)" : ""));
            ui.postDelayed(new Runnable() {
                @Override public void run() { finish(); }
            }, 900);
        } else if (text != null && text.startsWith("NGHBN")) {
            PatrolStore st = new PatrolStore(c);
            st.addEv("BADPLAQUE", null, "SIG", "");
            st.close();
            beep(false);
            tvResult.setBackgroundColor(0xCC922B21);
            tvResult.setText("⛔ پلاک نامعتبر است");
        } else {
            Toast.makeText(this, "این QR پلاک سامانهٔ گشت نیست", Toast.LENGTH_SHORT).show();
        }
    }

    private void beep(boolean ok) {
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            tg.startTone(ok ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK, 300);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        releaseCam();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == 8 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && surface.getHolder().getSurface() != null) {
            if (surface.getHolder().getSurface().isValid()) openCam(surface.getHolder());
        }
    }
}

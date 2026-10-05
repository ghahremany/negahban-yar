package ir.negahban.patrol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.FileOutputStream;

/**
 * چاپ مستقیم پلاک‌ها از خود اپ:
 * دیالوگ چاپ اندروید باز می‌شود → پرینتر وایفای/بلوتوث، یا «Save as PDF».
 * هر برگهٔ A4 دو پلاک با QR بزرگ دارد.
 */
public class PlaquePrinter {

    private static final int PAGE_W = 595;  // A4 به پوینت (72dpi)
    private static final int PAGE_H = 842;
    private static final int SLOT_H = PAGE_H / 2;

    public static void print(Context c) {
        PrintManager pm = (PrintManager) c.getSystemService(Context.PRINT_SERVICE);
        if (pm == null) return;
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
        pm.print("negahban-plaques", new Adapter(c), attrs);
    }

    private static Bitmap qrBitmap(String payload, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            int[] row = new int[size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) row[x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
                bmp.setPixels(row, 0, size, 0, y, size, 1);
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private static class Adapter extends PrintDocumentAdapter {
        final Context ctx;

        Adapter(Context c) { ctx = c; }

        private int pageCount() {
            int n = Cfg.stations(ctx).length;
            return Math.max(1, (n + 1) / 2);
        }

        @Override
        public void onLayout(PrintAttributes old, PrintAttributes nw, CancellationSignal sig, LayoutResultCallback cb, Bundle ex) {
            if (sig.isCanceled()) { cb.onLayoutCancelled(); return; }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder("negahban-plaques.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount())
                    .build();
            cb.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(PageRange[] ranges, ParcelFileDescriptor dest, CancellationSignal sig, WriteResultCallback cb) {
            try {
                String[][] sts = Cfg.stations(ctx);
                String key = Cfg.keyHex(ctx);
                String building = Cfg.building(ctx);
                PdfDocument doc = new PdfDocument();

                for (int p = 0; p * 2 < Math.max(1, sts.length); p++) {
                    PdfDocument.Page pg = doc.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, p + 1).create());
                    Canvas cv = pg.getCanvas();
                    cv.drawColor(Color.WHITE);
                    for (int s = 0; s < 2; s++) {
                        int idx = p * 2 + s;
                        if (idx >= sts.length) break;
                        drawPlaque(cv, key, building, sts[idx][0], sts[idx][1], s * SLOT_H);
                    }
                    doc.finishPage(pg);
                }

                FileOutputStream out = new FileOutputStream(dest.getFileDescriptor());
                doc.writeTo(out);
                out.close();
                doc.close();
                cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (Exception e) {
                cb.onWriteFailed("خطای ساخت PDF: " + e);
            }
        }

        private void drawPlaque(Canvas cv, String key, String building, String sid, String sname, int top) {
            String payload = Crypto.plaquePayload(key, sid);

            // قاب پلاک
            Paint frame = new Paint();
            frame.setColor(0xFF1A5276);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(3);
            float fx = 52, fy = top + 14, fw = PAGE_W - 104, fh = SLOT_H - 34;
            cv.drawRoundRect(fx, fy, fx + fw, fy + fh, 16, 16, frame);

            Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
            title.setColor(0xFF1A5276);
            title.setTextAlign(Paint.Align.CENTER);
            title.setFakeBoldText(true);
            title.setTextSize(19);
            cv.drawText(building, PAGE_W / 2f, top + 52, title);

            Bitmap qr = qrBitmap(payload, 280);
            if (qr != null) cv.drawBitmap(qr, (PAGE_W - 280) / 2f, top + 64, null);

            Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
            name.setColor(0xFF111111);
            name.setTextAlign(Paint.Align.CENTER);
            name.setFakeBoldText(true);
            name.setTextSize(27);
            cv.drawText("ایستگاه " + sname, PAGE_W / 2f, top + 374, name);

            Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
            note.setColor(0xFF999999);
            note.setTextAlign(Paint.Align.CENTER);
            note.setTextSize(10);
            cv.drawText("این پلاک فقط با سامانهٔ گشت شبانه کار می‌کند — لطفاً دست‌کاری نشود", PAGE_W / 2f, top + 392, note);

            Paint code = new Paint(Paint.ANTI_ALIAS_FLAG);
            code.setColor(0xFFBBBBBB);
            code.setTextAlign(Paint.Align.CENTER);
            code.setTypeface(Typeface.MONOSPACE);
            code.setTextSize(8);
            cv.drawText(payload, PAGE_W / 2f, top + 404, code);
        }
    }
}

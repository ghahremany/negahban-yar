package ir.negahban.saken;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * سرودهندهٔ فایل APK دانلودشده به نصب‌گر سیستم — جایگزین سبک FileProvider (بدون AndroidX)
 */
public class ApkProvider extends ContentProvider {

    @Override
    public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        android.content.Context c = getContext();
        if (c == null) throw new java.io.FileNotFoundException("no context");
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        File f = new File(dir, "update.apk");
        if (!f.exists()) throw new java.io.FileNotFoundException(f.getAbsolutePath());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }

    @Override
    public Uri insert(Uri uri, ContentValues v) { return null; }

    @Override
    public int delete(Uri uri, String s, String[] a) { return 0; }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}

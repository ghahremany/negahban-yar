package ir.negahban.saken;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

/** پایگاه‌دادهٔ محلی: رویدادها (با زنجیرهٔ HMAC) + صف پیام‌های بله */
public class PatrolStore extends SQLiteOpenHelper {

    private final Context ctx;

    public PatrolStore(Context c) { super(c, "patrol.db", null, 5); ctx = c.getApplicationContext(); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ev(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, type TEXT, station INTEGER, flag TEXT, extra TEXT)");
        db.execSQL("CREATE TABLE q(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, txt TEXT, sent INTEGER DEFAULT 0, sentTs INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE resident(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, mobile TEXT, plate TEXT, car TEXT, parking TEXT, chatId INTEGER DEFAULT 0, approved INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE guest(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, name TEXT, mobile TEXT, plate TEXT, nid TEXT, outTs INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE pkg(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, kind TEXT, barcode TEXT, rname TEXT, rmobile TEXT, rblock TEXT, sms TEXT DEFAULT 'PENDING', claimed INTEGER DEFAULT 0, givenTs INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE member_req(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, chatId INTEGER, name TEXT, mobile TEXT, unit TEXT, status TEXT DEFAULT 'PENDING'), notif(id INTEGER PRIMARY KEY AUTOINCREMENT, sid INTEGER UNIQUE, ts INTEGER, kind TEXT, text TEXT, seen INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE charge(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, amount TEXT, ref TEXT, month TEXT, status TEXT DEFAULT 'PENDING')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notif(id INTEGER PRIMARY KEY AUTOINCREMENT, sid INTEGER UNIQUE, ts INTEGER, kind TEXT, text TEXT, seen INTEGER DEFAULT 0)");
        if (o < 2) db.execSQL("CREATE TABLE IF NOT EXISTS resident(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, mobile TEXT, plate TEXT, car TEXT, parking TEXT)");
        if (o < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS guest(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, name TEXT, mobile TEXT, plate TEXT, nid TEXT, outTs INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE IF NOT EXISTS pkg(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, kind TEXT, barcode TEXT, rname TEXT, rmobile TEXT, rblock TEXT, sms TEXT DEFAULT 'PENDING')");
        }
        if (o < 4) {
            try { db.execSQL("ALTER TABLE resident ADD COLUMN chatId INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE resident ADD COLUMN approved INTEGER DEFAULT 1"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE pkg ADD COLUMN claimed INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE pkg ADD COLUMN givenTs INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            db.execSQL("CREATE TABLE IF NOT EXISTS member_req(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, chatId INTEGER, name TEXT, mobile TEXT, unit TEXT, status TEXT DEFAULT 'PENDING')");
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS charge(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, amount TEXT, ref TEXT, month TEXT, status TEXT DEFAULT 'PENDING')");
    }

    public static long now() { return System.currentTimeMillis(); }

    public long addEv(String type, Integer station, String flag, String extra) {
        return addEvTs(now(), type, station, flag, extra, true);
    }

    /** ثبت رویداد؛ chain=true یعنی داخل زنجیرهٔ امضا قرار بگیرد */
    public long addEvTs(long ts, String type, Integer station, String flag, String extra, boolean chain) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("ts", ts);
            v.put("type", type);
            if (station != null) v.put("station", station);
            v.put("flag", flag);
            String ext = extra == null ? "" : extra;
            if (chain) {
                String prev = lastHash(db);
                String key = Cfg.keyHex(ctx);
                ext = Crypto.hmacHex(key, prev + "|" + ts + "|" + type + "|" + (station == null ? "-" : station)).substring(0, 16);
            }
            v.put("extra", ext);
            return db.insert("ev", null, v);
        } finally {
            db.close();
        }
    }

    private String lastHash(SQLiteDatabase db) {
        Cursor cur = db.rawQuery("SELECT extra FROM ev WHERE type='SCAN' ORDER BY id DESC LIMIT 1", null);
        try {
            if (cur.moveToFirst()) return cur.getString(0);
        } finally { cur.close(); }
        return "GENESIS";
    }

    /** ثبت اسکن ایستگاه؛ اگر فاصلهٔ زمانی از اسکن قبلی مشکوک باشد flag=GAP */
    public String recordScan(Context ctx, int stationId) {
        long ts = now();
        SQLiteDatabase db = getReadableDatabase();
        long last = 0;
        Cursor cur = db.rawQuery("SELECT ts FROM ev WHERE type='SCAN' ORDER BY id DESC LIMIT 1", null);
        try { if (cur.moveToFirst()) last = cur.getLong(0); } finally { cur.close(); }
        db.close();

        String flag = "OK";
        if (last > 0 && (ts - last) < Cfg.minGapSec(ctx) * 1000L) flag = "GAP";

        addEvTs(ts, "SCAN", stationId, flag, null, true);
        return flag;
    }

    /** شمارش ایستگاه‌های متمایز اسکن‌شده در بازهٔ [a,b] */
    public int distinctStations(long a, long b) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            Cursor cur = db.rawQuery("SELECT COUNT(DISTINCT station) FROM ev WHERE type='SCAN' AND ts>=? AND ts<?",
                    new String[]{String.valueOf(a), String.valueOf(b)});
            try {
                cur.moveToFirst();
                return cur.getInt(0);
            } finally { cur.close(); }
        } finally { db.close(); }
    }

    /** اسکن‌های بازه: آرایه‌ای از [stationId, ts] */
    public ArrayList<long[]> scansBetween(long a, long b) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<long[]> out = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT station, ts FROM ev WHERE type='SCAN' AND ts>=? AND ts<? ORDER BY ts",
                    new String[]{String.valueOf(a), String.valueOf(b)});
            try {
                while (cur.moveToNext()) out.add(new long[]{cur.getLong(0), cur.getLong(1)});
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    /** همهٔ رویدادهای بازه: [type, stationStr, flag, ts, extra] */
    public ArrayList<String[]> evBetween(long a, long b) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT type, station, flag, ts, extra FROM ev WHERE ts>=? AND ts<? ORDER BY id",
                    new String[]{String.valueOf(a), String.valueOf(b)});
            try {
                while (cur.moveToNext()) {
                    Integer st = cur.isNull(1) ? null : cur.getInt(1);
                    out.add(new String[]{cur.getString(0), st == null ? "" : String.valueOf(st), cur.getString(2), String.valueOf(cur.getLong(3)), cur.getString(4)});
                }
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    public int countEv(String type, long a, long b) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            Cursor cur = db.rawQuery("SELECT COUNT(*) FROM ev WHERE type=? AND ts>=? AND ts<?", new String[]{type, String.valueOf(a), String.valueOf(b)});
            try { cur.moveToFirst(); return cur.getInt(0); } finally { cur.close(); }
        } finally { db.close(); }
    }

    // ---------- صف ارسال ----------

    public long enq(String txt) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("ts", now());
            v.put("txt", txt);
            v.put("sent", 0);
            return db.insert("q", null, v);
        } finally { db.close(); }
    }

    /** صف ارسال‌نشده: [id, txt] */
    public ArrayList<String[]> pending() {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur = db.query("q", new String[]{"id", "txt"}, "sent=0", null, null, null, "id", "50");
            try {
                while (cur.moveToNext()) out.add(new String[]{String.valueOf(cur.getLong(0)), cur.getString(1)});
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    public int pendingCount() {
        SQLiteDatabase db = getReadableDatabase();
        try {
            Cursor cur = db.rawQuery("SELECT COUNT(*) FROM q WHERE sent=0", null);
            try { cur.moveToFirst(); return cur.getInt(0); } finally { cur.close(); }
        } finally { db.close(); }
    }

    public void markSent(long id) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("sent", 1);
            v.put("sentTs", now());
            db.update("q", v, "id=?", new String[]{String.valueOf(id)});
        } finally { db.close(); }
    }

    // ---------- ساکنین ----------

    public long addRes(String name, String mobile, String plate, String car, String parking) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("name", name); v.put("mobile", mobile); v.put("plate", plate);
            v.put("car", car); v.put("parking", parking);
            return db.insert("resident", null, v);
        } finally { db.close(); }
    }

    public void updateRes(long id, String name, String mobile, String plate, String car, String parking) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("name", name); v.put("mobile", mobile); v.put("plate", plate);
            v.put("car", car); v.put("parking", parking);
            db.update("resident", v, "id=?", new String[]{String.valueOf(id)});
        } finally { db.close(); }
    }

    public void deleteRes(long id) {
        SQLiteDatabase db = getWritableDatabase();
        try { db.delete("resident", "id=?", new String[]{String.valueOf(id)}); } finally { db.close(); }
    }

    public void clearRes() {
        SQLiteDatabase db = getWritableDatabase();
        try { db.delete("resident", null, null); } finally { db.close(); }
    }

    /** [id, name, mobile, plate, car, parking] */
    public ArrayList<String[]> searchRes(String q) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur;
            if (q == null || q.trim().isEmpty()) {
                cur = db.rawQuery("SELECT id, name, mobile, plate, car, parking FROM resident ORDER BY name LIMIT 200", null);
            } else {
                String like = "%" + q.trim() + "%";
                cur = db.rawQuery("SELECT id, name, mobile, plate, car, parking FROM resident WHERE name LIKE ? OR mobile LIKE ? OR plate LIKE ? OR parking LIKE ? OR car LIKE ? ORDER BY name LIMIT 200",
                        new String[]{like, like, like, like, like});
            }
            try {
                while (cur.moveToNext()) {
                    out.add(new String[]{String.valueOf(cur.getLong(0)), cur.getString(1), cur.getString(2), cur.getString(3), cur.getString(4), cur.getString(5)});
                }
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    public int resCount() {
        SQLiteDatabase db = getReadableDatabase();
        try {
            Cursor cur = db.rawQuery("SELECT COUNT(*) FROM resident", null);
            try { cur.moveToFirst(); return cur.getInt(0); } finally { cur.close(); }
        } finally { db.close(); }
    }

    // ---------- عضویت ساکنین از طریق ربات ----------

    /** ساکنِ تأییدشدهٔ متصل به این chat بله یا null: [id, name, mobile, parking] */
    public String[] resByChat(long chatId) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            Cursor cur = db.rawQuery("SELECT id, name, mobile, parking FROM resident WHERE chatId=? AND approved=1 LIMIT 1",
                    new String[]{String.valueOf(chatId)});
            try {
                if (cur.moveToFirst()) return new String[]{String.valueOf(cur.getLong(0)), cur.getString(1), cur.getString(2), cur.getString(3)};
            } finally { cur.close(); }
            return null;
        } finally { db.close(); }
    }

    /** اتصال chat بله به ساکن موجود بر اساس شمارهٔ همراه؛ خروجی: شناسهٔ ردیف یا ۰ */
    public long linkChatByMobile(long chatId, String mobile) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            Cursor cur = db.rawQuery("SELECT id FROM resident WHERE REPLACE(REPLACE(REPLACE(mobile,'-',''),' ',''),'‎','') LIKE ? AND approved=1 LIMIT 1",
                    new String[]{"%" + mobile});
            long id = 0;
            try { if (cur.moveToFirst()) id = cur.getLong(0); } finally { cur.close(); }
            if (id > 0) {
                ContentValues v = new ContentValues();
                v.put("chatId", chatId);
                db.update("resident", v, "id=?", new String[]{String.valueOf(id)});
            }
            return id;
        } finally { db.close(); }
    }

    public long addMemberReq(long ts, long chatId, String name, String mobile, String unit) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("ts", ts); v.put("chatId", chatId); v.put("name", name);
            v.put("mobile", mobile); v.put("unit", unit); v.put("status", "PENDING");
            return db.insert("member_req", null, v);
        } finally { db.close(); }
    }

    /** درخواستِ در انتظارِ همین chat یا null */
    public String[] pendingReqByChat(long chatId) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            Cursor cur = db.rawQuery("SELECT id FROM member_req WHERE chatId=? AND status='PENDING' LIMIT 1", new String[]{String.valueOf(chatId)});
            try {
                if (cur.moveToFirst()) return new String[]{String.valueOf(cur.getLong(0))};
            } finally { cur.close(); }
            return null;
        } finally { db.close(); }
    }

    /** [id, ts, chatId, name, mobile, unit] */
    public ArrayList<String[]> reqsByStatus(String status, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT id, ts, chatId, name, mobile, unit FROM member_req WHERE status=? ORDER BY id DESC LIMIT ?",
                    new String[]{status, String.valueOf(limit)});
            try {
                while (cur.moveToNext()) out.add(new String[]{String.valueOf(cur.getLong(0)), String.valueOf(cur.getLong(1)),
                        String.valueOf(cur.getLong(2)), cur.getString(3), cur.getString(4), cur.getString(5)});
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    public void setReqStatus(long id, String status) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("status", status);
            db.update("member_req", v, "id=?", new String[]{String.valueOf(id)});
        } finally { db.close(); }
    }

    // ---------- تحویل بسته ----------

    public void markGiven(long id) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("claimed", 1);
            v.put("givenTs", now());
            db.update("pkg", v, "id=?", new String[]{String.valueOf(id)});
        } finally { db.close(); }
    }

    /** بسته‌های تحویل‌نشدهٔ یک شمارهٔ همراه: [id, ts, kind, barcode, rblock] */
    public ArrayList<String[]> pendingForMobile(String mobile) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT id, ts, kind, barcode, rblock FROM pkg WHERE rmobile LIKE ? AND claimed=0 ORDER BY id DESC LIMIT 10",
                    new String[]{"%" + mobile});
            try {
                while (cur.moveToNext()) out.add(new String[]{String.valueOf(cur.getLong(0)), String.valueOf(cur.getLong(1)),
                        cur.getString(2), cur.getString(3), cur.getString(4)});
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    // ---------- شارژ ----------

    public void addCharge(long ts, String amount, String ref, String month, String status) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("ts", ts); v.put("amount", amount); v.put("ref", ref);
            v.put("month", month); v.put("status", status);
            db.insert("charge", null, v);
        } finally { db.close(); }
    }

    /** [ts, amount, ref, month, status] */
    public ArrayList<String[]> myCharges() {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT ts, amount, ref, month, status FROM charge ORDER BY id DESC LIMIT 50", null);
            try {
                while (cur.moveToNext()) out.add(new String[]{String.valueOf(cur.getLong(0)), cur.getString(1),
                        cur.getString(2), cur.getString(3), cur.getString(4)});
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    // ---------- ابزار پشتیبان‌گیری ----------

    /** درج رویداد با مقادیر اصلی (برای بازیابی پشتیبان) */
    public void addEvRaw(long ts, String type, Integer station, String flag, String extra) {
        addEvTs(ts, type, station, flag, extra, false);
    }

    // ---------- مهمان‌ها ----------

    public long addGuestTs(long ts, String name, String mobile, String plate, String nid, long outTs) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("ts", ts); v.put("name", name); v.put("mobile", mobile);
            v.put("plate", plate); v.put("nid", nid); v.put("outTs", outTs);
            return db.insert("guest", null, v);
        } finally { db.close(); }
    }

    /** [id, ts, name, mobile, plate, nid, outTs] — جدیدترین اول */
    public ArrayList<String[]> allGuests(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT id, ts, name, mobile, plate, nid, outTs FROM guest ORDER BY id DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            try {
                while (cur.moveToNext()) {
                    out.add(new String[]{String.valueOf(cur.getLong(0)), String.valueOf(cur.getLong(1)),
                            cur.getString(2), cur.getString(3), cur.getString(4), cur.getString(5), String.valueOf(cur.getLong(6))});
                }
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    public void markGuestOut(long id, long ts) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("outTs", ts);
            db.update("guest", v, "id=?", new String[]{String.valueOf(id)});
        } finally { db.close(); }
    }

    public void clearGuests() {
        SQLiteDatabase db = getWritableDatabase();
        try { db.delete("guest", null, null); } finally { db.close(); }
    }

    // ---------- بسته‌ها ----------

    public long addPackageTs(long ts, String kind, String barcode, String rname, String rmobile, String rblock, String sms) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("ts", ts); v.put("kind", kind); v.put("barcode", barcode);
            v.put("rname", rname); v.put("rmobile", rmobile); v.put("rblock", rblock);
            v.put("sms", sms);
            return db.insert("pkg", null, v);
        } finally { db.close(); }
    }

    public void setPkgSms(long id, String status) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("sms", status);
            db.update("pkg", v, "id=?", new String[]{String.valueOf(id)});
        } finally { db.close(); }
    }

    /** [id, ts, kind, barcode, rname, rmobile, rblock, sms, claimed, givenTs] — جدیدترین اول */
    public ArrayList<String[]> allPackages(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT id, ts, kind, barcode, rname, rmobile, rblock, sms, claimed, givenTs FROM pkg ORDER BY id DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            try {
                while (cur.moveToNext()) {
                    out.add(new String[]{String.valueOf(cur.getLong(0)), String.valueOf(cur.getLong(1)),
                            cur.getString(2), cur.getString(3), cur.getString(4), cur.getString(5), cur.getString(6), cur.getString(7),
                            String.valueOf(cur.getInt(8)), String.valueOf(cur.getLong(9))});
                }
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    public void clearPackages() {
        SQLiteDatabase db = getWritableDatabase();
        try { db.delete("pkg", null, null); } finally { db.close(); }
    }

    /** رویدادهای ۹۰ روز اخیر برای فایل پشتیبان: [ts, type, station, flag, extra] */
    public ArrayList<String[]> rawEventsSince(long since) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            ArrayList<String[]> out = new ArrayList<>();
            Cursor cur = db.rawQuery("SELECT ts, type, station, flag, extra FROM ev WHERE ts>=? ORDER BY ts", new String[]{String.valueOf(since)});
            try {
                while (cur.moveToNext()) {
                    Integer stn = cur.isNull(2) ? null : cur.getInt(2);
                    out.add(new String[]{String.valueOf(cur.getLong(0)), cur.getString(1), stn == null ? "" : String.valueOf(stn), cur.getString(3), cur.getString(4)});
                }
            } finally { cur.close(); }
            return out;
        } finally { db.close(); }
    }

    // ---------- اعلان‌ها (زنگوله) ----------
    public boolean addNotif(long sid, long ts, String kind, String text) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.put("sid", sid); v.put("ts", ts); v.put("kind", kind);
            v.put("text", text); v.put("seen", 0);
            return db.insertWithOnConflict("notif", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
        } finally { db.close(); }
    }

    /** [0]=sid [1]=ts [2]=kind [3]=text [4]=seen */
    public ArrayList<String[]> notifs(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<String[]> out = new ArrayList<String[]>();
        try {
            android.database.Cursor c = db.rawQuery("SELECT sid, ts, kind, text, seen FROM notif ORDER BY sid DESC LIMIT " + limit, null);
            while (c.moveToNext()) out.add(new String[]{String.valueOf(c.getLong(0)), String.valueOf(c.getLong(1)), c.getString(2), c.getString(3), String.valueOf(c.getInt(4))});
            c.close();
        } finally { db.close(); }
        return out;
    }

    public int unseenNotifs() {
        SQLiteDatabase db = getReadableDatabase();
        try {
            android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM notif WHERE seen=0", null);
            int n = c.moveToFirst() ? c.getInt(0) : 0;
            c.close();
            return n;
        } finally { db.close(); }
    }

    public void markNotifsSeen() {
        SQLiteDatabase db = getWritableDatabase();
        try { db.execSQL("UPDATE notif SET seen=1 WHERE seen=0"); } finally { db.close(); }
    }
}

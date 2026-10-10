package ir.negahban.saken;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/** امضای HMAC پلاک‌ها + زنجیرهٔ رویدادها */
public class Crypto {

    static byte[] hex(String s) {
        int n = s.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    public static String randHex(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static String hmacHex(String keyHex, String msg) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(hex(keyHex), "HmacSHA256"));
            byte[] d = m.doFinal(msg.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** امضای یک پلاک برای ایستگاه id (۱۰ کاراکتر اول HMAC) — همین در ابزار چاپ پلاک هم استفاده می‌شود */
    public static String plaqueSig(String keyHex, String stationId) {
        return hmacHex(keyHex, stationId).substring(0, 10);
    }

    /** محتوای کامل QR پلاک */
    public static String plaquePayload(String keyHex, String stationId) {
        return "NGHBN|" + stationId + "|" + plaqueSig(keyHex, stationId);
    }

    /** اگر payload یک پلاک معتبر باشد، شناسهٔ ایستگاه برمی‌گردد؛ وگرنه null */
    public static String validatePlaque(String keyHex, String payload) {
        String[] parts = payload.split("\\|");
        if (parts.length != 3 || !"NGHBN".equals(parts[0])) return null;
        String expected = plaqueSig(keyHex, parts[1]);
        if (expected.isEmpty()) return null;
        // مقایسهٔ زمان‌ثابت ساده
        if (expected.length() != parts[2].length()) return null;
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) diff |= expected.charAt(i) ^ parts[2].charAt(i);
        return diff == 0 ? parts[1] : null;
    }
}

<?php
// بات پشتیبانی «نگهبان‌یار» در پیام‌رسان بله — polling + وضعیت گفتگو در MySQL
// اجرا:  CLI:  php bot.php run        (برای cron)
//        وب:   bot.php?action=run&key=NGY-CLOUD-…   (بدون UA مرورگر)
// اولین تماس: نام و نام خانوادگی → شمارهٔ همراه → شهر؛ بعد پاسخ‌گوی محصول
error_reporting(E_ALL & ~E_DEPRECATED);
ini_set('display_errors', '0');
require __DIR__ . '/../panel/config.php';   // DB_* + BALE_TOKEN + BALE_BASE

$KEY = 'NGY-CLOUD-7c4e2f91a8d63b50e7f1c2a493d85b6e';
$isCli = PHP_SAPI === 'cli';
if (!$isCli) {
    $k = isset($_GET['key']) ? $_GET['key'] : (isset($_SERVER['HTTP_X_NGY_KEY']) ? $_SERVER['HTTP_X_NGY_KEY'] : '');
    if (!hash_equals($KEY, (string)$k)) { http_response_code(403); header('Content-Type: text/plain'); die('forbidden'); }
}
$action = $isCli ? (isset($argv[1]) ? $argv[1] : 'run') : (isset($_GET['action']) ? $_GET['action'] : 'run');
header('Content-Type: application/json; charset=utf-8');

// ---------- DB ----------
function db() {
    static $DB = null;
    if ($DB === null) {
        $DB = new PDO('mysql:host=' . $GLOBALS['DB_HOST'] . ';dbname=' . $GLOBALS['DB_NAME'] . ';charset=utf8mb4',
            $GLOBALS['DB_USER'], $GLOBALS['DB_PASS'],
            [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_TIMEOUT => 15]);
        $DB->exec("CREATE TABLE IF NOT EXISTS ngy_bale (
            chat_id BIGINT PRIMARY KEY, name VARCHAR(80) DEFAULT '', family VARCHAR(80) DEFAULT '',
            mobile VARCHAR(15) DEFAULT '', city VARCHAR(60) DEFAULT '', step VARCHAR(12) DEFAULT 'new', ts INT DEFAULT 0
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $DB->exec("CREATE TABLE IF NOT EXISTS ngy_bale_log (
            id INT AUTO_INCREMENT PRIMARY KEY, chat_id BIGINT, msg VARCHAR(500), replied TINYINT DEFAULT 0, ts INT,
            KEY chat (chat_id), KEY rep (replied)
        ) ENGINE=INNODB DEFAULT CHARSET=utf8mb4");
        $DB->exec("CREATE TABLE IF NOT EXISTS ngy_bale_out (
            id INT AUTO_INCREMENT PRIMARY KEY, chat_id BIGINT, text VARCHAR(1200), sent TINYINT DEFAULT 0, tries TINYINT DEFAULT 0, ts INT,
            KEY st (sent)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $DB->exec("CREATE TABLE IF NOT EXISTS ngy_bale_meta (k VARCHAR(32) PRIMARY KEY, v VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }
    return $DB;
}

// ---------- Bale API ----------
function api($method, $params = [], $timeout = 20) {
    // دروازهٔ بله فقط GET رو روت می‌کند — همهٔ متدها با query string
    $url = $GLOBALS['BALE_BASE'] . '/' . $GLOBALS['BALE_TOKEN'] . '/' . $method;
    if ($params) $url .= '?' . http_build_query($params);
    $r = ''; $e = ''; $code = 0;
    for ($i = 0; $i < 3; $i++) {           // دسترسی از دیتاسنتر نوسان دارد → ۳ تلاش
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => $timeout,
            CURLOPT_SSL_VERIFYPEER => true,
        ]);
        $r = curl_exec($ch);
        $e = curl_error($ch);
        $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        $j = $r ? json_decode($r, true) : null;
        if (is_array($j)) return $j;
        if ($i < 2) sleep(2 + $i * 2);
    }
    return ['ok' => false, 'err' => $e ?: ('http' . $code), 'raw' => mb_substr((string)$r, 0, 120)];
}
function send($chat, $text) {
    if (!empty($GLOBALS['DEFER'])) { $GLOBALS['replies'][] = (string)$text; return ['ok' => true]; }
    $r = api('sendMessage', ['chat_id' => $chat, 'text' => $text]); $GLOBALS['last_send'] = $r; return $r;
}

// ---------- کمکی ----------
function normEn($s) {
    $fa = ['۰','۱','۲','۳','۴','۵','۶','۷','۸','۹','٠','١','٢','٣','٤','٥','٦','٧','٨','٩'];
    $en = ['0','1','2','3','4','5','6','7','8','9','0','1','2','3','4','5','6','7','8','9'];
    return trim(str_replace($fa, $en, (string)$s));
}
function normMobile($s) {
    $s = normEn($s);
    $s = preg_replace('/[^0-9]/', '', str_replace(['+98', '0098'], '0', ltrim($s, '+')));
    if (strpos($s, '98') === 0 && strlen($s) === 12) $s = '0' . substr($s, 2);
    if (strpos($s, '9') === 0 && strlen($s) === 10) $s = '0' . $s;
    return $s;
}
function state($chat) {
    $st = db()->prepare('SELECT * FROM ngy_bale WHERE chat_id=?'); $st->execute([$chat]);
    $r = $st->fetch(PDO::FETCH_ASSOC);
    return $r ?: ['chat_id' => $chat, 'name' => '', 'family' => '', 'mobile' => '', 'city' => '', 'step' => 'new', 'ts' => 0];
}
function saveStep($chat, $step, $name = null, $family = null, $mobile = null, $city = null) {
    db()->prepare('INSERT INTO ngy_bale (chat_id, step, ts) VALUES (?,?,?) ON DUPLICATE KEY UPDATE step=VALUES(step), ts=VALUES(ts)')
       ->execute([$chat, $step, time()]);
    if ($name !== null)   db()->prepare('UPDATE ngy_bale SET name=? WHERE chat_id=?')->execute([$name, $chat]);
    if ($family !== null) db()->prepare('UPDATE ngy_bale SET family=? WHERE chat_id=?')->execute([$family, $chat]);
    if ($mobile !== null) db()->prepare('UPDATE ngy_bale SET mobile=? WHERE chat_id=?')->execute([$mobile, $chat]);
    if ($city !== null)   db()->prepare('UPDATE ngy_bale SET city=? WHERE chat_id=?')->execute([$city, $chat]);
}
function logQ($chat, $txt) {
    db()->prepare('INSERT INTO ngy_bale_log (chat_id, msg, ts) VALUES (?,?,?)')->execute([$chat, mb_substr($txt, 0, 500), time()]);
}
function meta($k, $v = null) {
    if ($v === null) {
        $st = db()->prepare('SELECT v FROM ngy_bale_meta WHERE k=?'); $st->execute([$k]);
        $r = $st->fetch(PDO::FETCH_ASSOC);
        return $r ? $r['v'] : '';
    }
    db()->prepare('INSERT INTO ngy_bale_meta (k, v) VALUES (?,?) ON DUPLICATE KEY UPDATE v=VALUES(v)')->execute([$k, $v]);
}

const DEV_PHONE = '۰۹۱۲۷۲۸۵۰۶۵';
const SITE = 'https://negahbanyar.xo.je';

function menuText($name = '') {
    return "سوالت رو بنویس، یا عدد گزینه رو بفرست:\n"
        . "1️⃣ امکانات مجموعه «یار»\n2️⃣ قیمت و فعال‌سازی\n3️⃣ دانلود اپ‌ها\n4️⃣ ثبت‌نام ساکنان\n5️⃣ گشت و نگهبانی\n6️⃣ تماس با پشتیبانی";
}

// ---------- موتور پاسخ ----------
function faq($raw) {
    $t = normEn(mb_strtolower($raw));
    $has = function ($words) use ($t) {
        foreach ((array)$words as $w) if (mb_strpos($t, $w) !== false) return true;
        return false;
    };
    if ($has(['قیمت', 'هزینه', 'پول', 'نقد', 'چند می', 'تعرفه', 'پرداخت']))
        return "💰 قیمت:\n• نگهبان‌یار (اپ نگهبان) — رایگان\n• ساکن‌یار (اپ اهالی) — رایگان\n• مدیر یار — ۱۴ روز آزمایش رایگان، سپس فعال‌سازی دائمی\n\nبرای فعال‌سازی با پشتیبانی تماس بگیر: " . DEV_PHONE;
    if ($has(['فعال', 'سریال', 'کد فعال', 'ریجستری', 'اسپانسر']))
        return "🔑 فعال‌سازی مدیر یار: پس از نصب، ۱۴ روز کامل رایگان است. برای فعال‌سازی دائمی تماس بگیر: " . DEV_PHONE . "\nفعال‌سازی یک‌باره برای همیشه است و با تعویض گوشی هم قابل انتقال.";
    if ($has(['دانلود', 'نصب', 'لینک مستقیم', 'apk', 'اپ رو از کجا', 'از کجا بیارم', 'دریافت']))
        return "📥 دانلود هر سه اپ از سایت رسمی:\n" . SITE . "\n(نگهبان‌یار، مدیر یار، ساکن‌یار)";
    if ($has(['ثبت نام', 'ثبت‌نام', 'ساکن', 'اهالی', 'عضویت', 'واحد']))
        return "🏠 ثبت‌نام اهالی:\nساکن اپ «ساکن‌یار» را نصب می‌کند → شمارهٔ همراه مدیر ساختمان را وارد می‌کند → اطلاعات خودش (نام، همراه، واحد)، نقش (مالک/مستأجر) و مشخصات ملک (نفرات، خودرو، پلاک، پارکینگ، کد ملی) را ثبت می‌کند → مدیر در اپ «مدیر یار» تأیید می‌کند و عضویت نهایی می‌شود.";
    if ($has(['گشت', 'کیو', ' qr', 'اسکن', 'چک', 'دوربین', 'خواب']))
        return "🛡 گشت شبانه:\nنگهبان‌یار تگ‌های QR را در نقاط ساختمان نصب می‌کند؛ نگهبان هر دور اسکن می‌کند. ساعت، مسیر و ترتیب گشت ثبت می‌شود و دورِ جاافتاده یا خوابیدنِ سرِ پست، در تقویم رنگی مدیر مشخص می‌شود (ضد تقلب).";
    if ($has(['بسته', 'مرسول', 'پست', 'مرسله', 'سوپرایز']))
        return "📦 بسته‌ها:\nنگهبان بسته را با نوع و بارکد و گیرنده ثبت می‌کند؛ ساکن در اپش می‌بیند «بستهٔ تو نزد نگهبانی است». خروج بسته هم ثبت می‌شود.";
    if ($has(['مهمان', 'میهمان', 'خودرو', 'پلاک']))
        return "🚗 مهمان‌ها:\nورود و خروج مهمان با نام، همراه، شمارهٔ خودرو و کد ملی ثبت می‌شود؛ سابقهٔ کامل برای مدیر باقی می‌ماند.";
    if ($has(['شارژ', 'صندوق', 'بدهکار', 'قرض', 'قسط']))
        return "💳 صندوق و شارژ:\nمدیر شارژ ماهانه را ثبت می‌کند؛ وضعیت پرداخت هر واحد در داشبورد ساکن و فهرست بدهکارهای مدیر خودکار به‌روز می‌شود.";
    if ($has(['کلید', 'پشت بام', 'پشت‌بام', 'بام', 'پشت‌بام']))
        return "🗝 کلید پشت‌بام:\nساکن درخواست می‌دهد → مدیر یک لمس تأیید می‌کند → نگهبان اطلاع «کلید تحویل شود» می‌گیرد. درخواست رد هم ثبت می‌شود.";
    if ($has(['تعمیر', 'خرابی', 'گلویی', 'پرت']))
        return "🔧 تعمیرات: در نسخه‌های بعدی، ساکن خرابی را ثبت می‌کند و برای مدیر/تعمیرکار می‌آید (در نقشهٔ راه است).";
    if ($has(['پنل', 'مدیریت', 'سایت', 'وب']))
        return "🖥 پنل: مدیران از اپ «مدیر یار» و ساکنان از «ساکن‌یار» استفاده می‌کنند؛ سایت " . SITE . " هم صفحهٔ معرفی و دانلود است. پنل مخصوص مدیر ساختمان داخل خود اپ مدیر یار هست.";
    if ($has(['تماس', 'پشتیبان', 'شماره', 'تلفن', 'ادمین', 'انسانی', 'اقا', 'آقا', 'مهندس', 'قهرمانی']))
        return "📞 پشتیبانی انسانی:\nمحمد جواد قهرمانی — " . DEV_PHONE . "\nهمین‌جا هم سوالت را بنویس، جواب می‌دهم.";
    if ($has(['سلام', 'درود', 'aliak', 'وقت بخیر']))
        return "سلام! 👋 خوش اومدی.\n" . menuText();
    if ($has(['ممنون', 'مرسی', 'دستت درد', 'خدا', 'مچکر', 'سپاس']))
        return "خواهش می‌کنم! 🌹 کار دیگه‌ای هست؟";
    if ($has(['خداحافظ', 'بای', 'فعلا']))
        return "به امید دیدار! 🌙 هر وقت سوالی بود همین‌جاییم.";
    if ($has(['نگهبان یار', 'نگهبان‌یار', 'چیه', 'چیست', 'چیکار', 'کاربرد', 'معرفی']))
        return "🧭 مجموعهٔ «یار» سه اپ هماهنگ برای ساختمان است:\n• 🛡 نگهبان‌یار — گشت شبانهٔ QR ضد تقلب، مهمان، بسته\n• 🧭 مدیر یار — داشبورد مدیر، تأیید اهالی، صندوق، تقویم گشت\n• 🏠 ساکن‌یار — داشبورد ساکن: بسته، شارژ، اخبار، درخواست کلید\n\n" . menuText();
    return null; // نامشخص → ثبت برای پشتیبانی
}

function answer($chat, $txt) {
    $a = faq($txt);
    if ($a !== null) return $a;
    logQ($chat, $txt);
    return "سوالت ثبت شد ✅ دقیق‌تر بررسی می‌کنم و جواب می‌دهم.\nاگر فوری بود: " . DEV_PHONE . "\n\n" . menuText();
}

// ---------- پردازش یک پیام ----------
function handle($chat, $txt) {
    $st = state($chat);
    $txt = trim($txt);
    if ($txt === '') return;
    if ($txt === '/start' || $txt === '/menu') {
        if ($st['step'] !== 'new' && $st['step'] !== 'ready' && $st['name'] !== '' && $st['mobile'] !== '') {
            saveStep($chat, 'ready');
            send($chat, 'سلام ' . $st['name'] . ' عزیز 👋' . "\n" . menuText());
            return;
        }
        if ($st['step'] === 'ready') { send($chat, menuText()); return; }
        saveStep($chat, 'name');
        send($chat, "سلام! 👋 به پشتیبانی «نگهبان‌یار» خوش اومدی.\nقبل از هر چیز چند مشخصه ازت می‌گیرم تا بهتر کمکت کنم.\n\n۱) نام و نام خانوادگی‌ات رو بنویس:");
        return;
    }
    if ($txt === '/contact') { send($chat, '📞 محمد جواد قهرمانی — ' . DEV_PHONE); return; }

    switch ($st['step']) {
        case 'new':
            saveStep($chat, 'name');
            send($chat, "سلام! 👋 به پشتیبانی «نگهبان‌یار» خوش اومدی.\nقبل از هر چیز چند مشخصه ازت می‌گیرم تا بهتر کمکت کنم.\n\n۱) نام و نام خانوادگی‌ات رو بنویس:");
            return;
        case 'name':
            if (mb_strlen($txt) < 3 || mb_strlen($txt) > 60) { send($chat, 'نام رو کامل بنویس (نام و نام خانوادگی):'); return; }
            $p = explode(' ', preg_replace('/\s+/', ' ', $txt), 2);
            saveStep($chat, 'mobile', $p[0], isset($p[1]) ? $p[1] : '');
            send($chat, "خوش اومدم «{$txt}» 🌹\n۲) شمارهٔ همراهت (مثل 09123456789):");
            return;
        case 'mobile':
            $m = normMobile($txt);
            if (!preg_match('/^09\d{9}$/', $m)) { send($chat, 'شماره درست نیست؛ ۱۱ رقمی و با ۰۹ شروع شود (مثل 09123456789):'); return; }
            saveStep($chat, 'city', null, null, $m);
            send($chat, "✅ ثبت شد.\n۳) اسم شهرت:");
            return;
        case 'city':
            if (mb_strlen($txt) < 2) { send($chat, 'اسم شهر رو بنویس:'); return; }
            saveStep($chat, 'ready', null, null, null, $txt);
            $st = state($chat);
            send($chat, "🎉 تمام شد، {$st['name']} عزیز!\nثبت شد: {$st['name']} {$st['family']} — {$st['mobile']} — {$st['city']}\n\nحالا هر سوالی دربارهٔ مجموعهٔ «یار» داری بپرس.\n\n" . menuText());
            return;
        default: // ready
            send($chat, answer($chat, $txt));
    }
}

// ---------- اجرا ----------
try {
    db();
    if ($action === 'offset') {
        if (isset($_GET['value'])) { meta('offset', (string)(int)$_GET['value']); out_json(['ok' => true]); }
        out_json(['ok' => true, 'offset' => (int)meta('offset')]);
    }
    if ($action === 'feed') {           // Actions پیام را می‌دهد، پاسخ برمی‌گردد تا خودش به بله بفرستد
        $chat = (int)$_GET['chat']; $text = trim((string)$_GET['text']);
        if ($text === '') out_json(['ok' => true, 'reply' => '']);
        $GLOBALS['DEFER'] = true; $GLOBALS['replies'] = [];
        handle($chat, $text);
        out_json(['ok' => true, 'reply' => implode("\n", $GLOBALS['replies'])]);
    }
    if ($action === 'outbox') {         // پاسخ‌های queued از پنل
        $items = [];
        foreach (db()->query('SELECT id, chat_id, text FROM ngy_bale_out WHERE sent=0 AND tries<5 ORDER BY id LIMIT 20') as $o)
            $items[] = ['id' => (int)$o['id'], 'chat' => (int)$o['chat_id'], 'text' => $o['text']];
        out_json(['ok' => true, 'items' => $items]);
    }
    if ($action === 'sent') {
        db()->prepare('UPDATE ngy_bale_out SET sent=1 WHERE id=?')->execute([(int)$_GET['id']]);
        out_json(['ok' => true]);
    }
    if ($action === 'probe') {
        $me = api('getMe', [], 15);
        out_json(['ok' => true, 'me' => $me, 'dir' => __DIR__, 'php' => PHP_VERSION, 'tables' => 'ok']);
    } elseif ($action === 'simulate') {
        $chat = (int)$_GET['chat']; $text = (string)$_GET['text'];
        $GLOBALS['last_send'] = null;
        handle($chat, $text);
        $st = state($chat);
        out_json(['ok' => true, 'state' => $st, 'send' => $GLOBALS['last_send']]);
    } elseif ($action === 'forget') {
        $chat = (int)$_GET['chat'];
        foreach (['DELETE FROM ngy_bale WHERE chat_id=?', 'DELETE FROM ngy_bale_log WHERE chat_id=?', 'DELETE FROM ngy_bale_out WHERE chat_id=?'] as $q) {
            db()->prepare($q)->execute([$chat]);
        }
        out_json(['ok' => true]);
    } else { // run
        $off = (int)meta('offset');
        $T = $isCli ? 20 : 8;
        @set_time_limit($T + 40);
        $res = api('getUpdates', ['offset' => $off, 'timeout' => $T], $T + 20);
        $n = 0; $errs = [];
        if (!empty($res['ok']) && !empty($res['result'])) {
            foreach ($res['result'] as $u) {
                meta('offset', (string)((int)$u['update_id'] + 1));
                if (isset($u['message']['text'])) {
                    handle((int)$u['message']['chat']['id'], (string)$u['message']['text']);
                    $n++;
                }
            }
        } elseif (empty($res['ok'])) {
            $errs[] = 'getUpdates: ' . substr(json_encode($res, JSON_UNESCAPED_UNICODE), 0, 200);
        }
        // ارسال پاسخ‌های صف‌شده از پنل
        $sent = 0; $fails = 0;
        $st = db()->query('SELECT id, chat_id, text, tries FROM ngy_bale_out WHERE sent=0 AND tries<5 ORDER BY id LIMIT 20');
        foreach ($st->fetchAll(PDO::FETCH_ASSOC) as $o) {
            $r = send($o['chat_id'], $o['text']);
            if (!empty($r['ok'])) { db()->prepare('UPDATE ngy_bale_out SET sent=1 WHERE id=?')->execute([$o['id']]); $sent++; }
            else { db()->prepare('UPDATE ngy_bale_out SET tries=tries+1 WHERE id=?')->execute([$o['id']]); $fails++; }
        }
        out_json(['ok' => true, 'updates' => $n, 'sent' => $sent, 'fails' => $fails, 'errs' => $errs]);
    }
} catch (Exception $e) {
    out_json(['ok' => false, 'err' => $e->getMessage()]);
}

function out_json($x) { echo json_encode($x, JSON_UNESCAPED_UNICODE); exit; }

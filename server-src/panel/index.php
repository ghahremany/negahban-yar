<?php
// پنل توسعه‌دهندهٔ سوئیت «یار» — مشاهده/ویرایش دادهٔ سرور + ارسال پیام گروهی
// ورود: رمز + کد یک‌بارمصرف Google Authenticator
session_start();
require __DIR__ . '/totp.php';
require __DIR__ . '/config.php';

header('Content-Type: text/html; charset=utf-8');
header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');

$DB = null;
function db() {
    global $DB, $DB_HOST, $DB_NAME, $DB_USER, $DB_PASS;
    if ($DB === null) {
        $DB = new PDO('mysql:host=' . $DB_HOST . ';dbname=' . $DB_NAME . ';charset=utf8mb4',
            $DB_USER, $DB_PASS,
            [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_TIMEOUT => 15]);
    }
    return $DB;
}
function out_err($msg) { echo '<div class="err">⛔ ' . htmlspecialchars($msg) . '</div>'; }
function out_ok($msg)  { echo '<div class="ok">✅ ' . htmlspecialchars($msg) . '</div>'; }
function h($s) { return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }
function csrf_field() {
    if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(16));
    return '<input type="hidden" name="csrf" value="' . $_SESSION['csrf'] . '">';
}
function csrf_ok() {
    if (empty($_SESSION['csrf']) || empty($_POST['csrf'])) return false;
    return hash_equals($_SESSION['csrf'], (string)$_POST['csrf']);
}

// ---------- قفل تلاش ----------
$LOCK = __DIR__ . '/login.lock';
function lock_info() {
    global $LOCK;
    if (!file_exists($LOCK)) return [0, 0];
    $j = json_decode(file_get_contents($LOCK), true) ?: ['n' => 0, 't' => 0];
    if (time() - $j['t'] > 600) return [0, 0]; // ۱۰ دقیقه
    return [$j['n'], $j['t']];
}

// ---------- خروج ----------
if (isset($_GET['logout'])) { session_destroy(); header('Location: index.php'); exit; }

// ---------- ورود ----------
$logged = !empty($_SESSION['admin']);
if (!$logged) {
    [$n] = lock_info();
    $err = '';
    if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($n < 5)) {
        $pwd  = (string)($_POST['pwd'] ?? '');
        $code = (string)($_POST['code'] ?? '');
        $ok = hash_equals($ADMIN_HASH, hash_pbkdf2('sha256', $pwd, 'ngy-panel-salt', 100000, 64));
        if ($ok && totp_verify($TOTP_SECRET, $code)) {
            session_regenerate_id(true);
            $_SESSION['admin'] = true;
            if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(16));
            @unlink($LOCK);
            header('Location: index.php');
            exit;
        }
        $j = json_decode(@file_get_contents($LOCK), true) ?: ['n' => 0, 't' => 0];
        $j = ['n' => (time() - ($j['t'] ?? 0) > 600 ? 1 : $j['n'] + 1), 't' => time()];
        @file_put_contents($LOCK, json_encode($j));
        sleep(2);
        $err = 'رمز یا کد اشتباه است' . ($j['n'] >= 5 ? ' — ورود ۱۰ دقیقه قفل شد' : '');
    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $err = 'تلاش‌های زیاد — ۱۰ دقیقه صبر کن';
    }
    ?><!DOCTYPE html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
    <title>ورود — پنل «یار»</title><style>
    body{font-family:Tahoma,sans-serif;background:#0A2C40;display:grid;place-items:center;min-height:100vh;margin:0}
    .card{background:#fff;border-radius:20px;padding:34px 30px;width:min(360px,92vw);box-shadow:0 20px 60px rgba(0,0,0,.35)}
    h1{font-size:18px;color:#0A2C40;text-align:center;margin:0 0 6px} p.sub{font-size:12px;color:#78909C;text-align:center;margin:0 0 20px}
    label{display:block;font-size:12.5px;color:#546E7A;margin:12px 0 4px}
    input{width:100%;box-sizing:border-box;padding:11px 12px;border:1px solid #CFD8DC;border-radius:11px;font-size:15px;direction:ltr;text-align:center}
    input[dir=rtl]{text-align:right;direction:rtl}
    button{width:100%;margin-top:20px;padding:12px;border:0;border-radius:12px;background:linear-gradient(135deg,#12B5A5,#0FA3C9);color:#fff;font-size:15px;font-weight:bold;cursor:pointer}
    .err{background:#FFEBEE;color:#B71C1C;padding:9px 12px;border-radius:10px;font-size:12.5px;margin-top:14px;text-align:center}
    </style></head><body><div class="card">
    <h1>🧭 پنل توسعه‌دهنده</h1><p class="sub">سوئیت «یار» — ورود امن دو مرحله‌ای</p>
    <form method="post">
      <label>رمز ورود</label><input type="password" name="pwd" autocomplete="current-password" required>
      <label>کد یک‌بارمصرف Google Authenticator</label><input type="text" name="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" required>
      <button>ورود</button>
      <?php if ($err) echo '<div class="err">⛔ ' . h($err) . '</div>'; ?>
    </form></div></body></html><?php
    exit;
}

// ---------- عملیات POST ----------
$msg = ''; $ok_msg = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST' && csrf_ok()) {
    $act = $_POST['act'] ?? '';
    try {
        if ($act === 'bldg_save') {
            db()->prepare('UPDATE ngy_bldg SET name=?, units=?, addr=? WHERE code=?')
               ->execute([mb_substr(trim($_POST['name']), 0, 100), max(0, (int)$_POST['units']), mb_substr(trim($_POST['addr']), 0, 190), $_POST['code']]);
            $ok_msg = 'ساختمان ذخیره شد';
        } elseif ($act === 'bldg_del') {
            $c = $_POST['code'];
            foreach ([['DELETE FROM ngy_res WHERE code=?', $c], ['DELETE FROM ngy_msg WHERE code=?', $c], ['DELETE FROM ngy_data WHERE code=?', $c], ['DELETE FROM ngy_bldg WHERE code=?', $c]] as $q) {
                db()->prepare($q[0])->execute([$c]);
            }
            $ok_msg = 'ساختمان و همهٔ داده‌هایش حذف شد';
        } elseif ($act === 'res_save') {
            db()->prepare('UPDATE ngy_res SET name=?, mobile=?, unit=?, role=?, car=?, plate=?, park=?, nid=?, nres=?, status=? WHERE code=? AND uid=?')
               ->execute([mb_substr(trim($_POST['name']), 0, 80), substr(preg_replace('/[^0-9]/', '', $_POST['mobile']), 0, 15),
                          substr(trim($_POST['unit']), 0, 12), ($_POST['role'] === 'owner' ? 'owner' : 'tenant'),
                          mb_substr(trim($_POST['car']), 0, 38), substr(trim($_POST['plate']), 0, 18),
                          substr(trim($_POST['park']), 0, 18), substr(preg_replace('/[^0-9]/', '', $_POST['nid']), 0, 12),
                          max(0, (int)$_POST['nres']), in_array($_POST['status'], ['PENDING', 'OK', 'NO']) ? $_POST['status'] : 'PENDING',
                          $_POST['code'], strtoupper($_POST['uid'])]);
            $ok_msg = 'اهالی ذخیره شد';
        } elseif ($act === 'res_del') {
            db()->prepare('DELETE FROM ngy_res WHERE code=? AND uid=?')->execute([$_POST['code'], strtoupper($_POST['uid'])]);
            $ok_msg = 'رکورد اهالی حذف شد';
        } elseif ($act === 'msg_del') {
            db()->prepare('DELETE FROM ngy_msg WHERE code=? AND id=?')->execute([$_POST['code'], (int)$_POST['id']]);
            $ok_msg = 'پیام حذف شد';
        } elseif ($act === 'data_save') {
            $pl = (string)$_POST['payload'];
            if (strlen($pl) > 2000000) throw new Exception('حجم زیاد است');
            json_decode($pl); if (json_last_error() !== JSON_ERROR_NONE) throw new Exception('JSON نامعتبر: ' . json_last_error_msg());
            db()->prepare('INSERT INTO ngy_data (code, k, payload, ts) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE payload=VALUES(payload), ts=VALUES(ts)')
               ->execute([$_POST['code'], substr(trim($_POST['k']), 0, 64), $pl, time()]);
            $ok_msg = 'داده ذخیره شد';
        } elseif ($act === 'data_del') {
            db()->prepare('DELETE FROM ngy_data WHERE code=? AND k=?')->execute([$_POST['code'], $_POST['k']]);
            $ok_msg = 'کلید داده حذف شد';
        } elseif ($act === 'bale_reply') {
            $chat = (int)$_POST['chat'];
            $text = trim((string)$_POST['text']);
            if ($text === '' || $chat <= 0) throw new Exception('متن یا مقصد نامعتبر');
            if (mb_strlen($text) > 1200) $text = mb_substr($text, 0, 1200);
            db()->prepare('INSERT INTO ngy_bale_out (chat_id, text, sent, tries, ts) VALUES (?,?,0,0,?)')->execute([$chat, $text, time()]);
            if (!empty($_POST['logid'])) db()->prepare('UPDATE ngy_bale_log SET replied=1 WHERE id=?')->execute([(int)$_POST['logid']]);
            $ok_msg = 'به صف ارسال بله اضافه شد (بات cron می‌فرستد)';
        } elseif ($act === 'broadcast') {
            $text  = trim((string)$_POST['text']);
            $scope = $_POST['scope'] ?? '';       // all | code
            $to    = $_POST['to'] ?? 'modir';     // modir | negahban | residents
            $code  = strtoupper(trim($_POST['code'] ?? ''));
            if ($text === '') throw new Exception('متن خالی است');
            $targets = [];
            if ($scope === 'one') {
                $st = db()->prepare('SELECT code, name FROM ngy_bldg WHERE code=?'); $st->execute([$code]);
                if (!$r = $st->fetch(PDO::FETCH_ASSOC)) throw new Exception('ساختمان پیدا نشد');
                $targets[] = $r;
            } else {
                $targets = db()->query('SELECT code, name FROM ngy_bldg')->fetchAll(PDO::FETCH_ASSOC);
            }
            $n = 0;
            $ins = db()->prepare('INSERT INTO ngy_msg (code, dst, src, kind, payload, ts) VALUES (?,?,?,?,?,?)');
            $payload = json_encode(['ts' => time(), 'title' => '📢 اطلاعیهٔ مدیریت سامانه', 'body' => $text], JSON_UNESCAPED_UNICODE);
            $resIns = db()->prepare('SELECT uid FROM ngy_res WHERE code=? AND status=\'OK\'');
            foreach ($targets as $t) {
                if ($to === 'residents') {
                    $resIns->execute([$t['code']]);
                    foreach ($resIns->fetchAll(PDO::FETCH_ASSOC) as $r) {
                        $ins->execute([$t['code'], 'saken:' . $r['uid'], 'panel', 'NEWS', $payload, time()]);
                        $n++;
                        if ($n >= 2000) break 2;
                    }
                } else {
                    $ins->execute([$t['code'], $to, 'panel', 'NEWS', $payload, time()]);
                    $n++;
                }
            }
            $ok_msg = 'پیام برای ' . $n . ' مقصد ثبت شد';
        }
    } catch (Exception $e) { $msg = $e->getMessage(); }
}

// ---------- داده‌های نمایش ----------
$page = $_GET['page'] ?? 'dash';
$qcode = strtoupper(trim($_GET['code'] ?? ($_POST['code'] ?? '')));
?><!DOCTYPE html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
<title>پنل «یار» — توسعه‌دهنده</title>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:Tahoma,sans-serif;background:#F2F6F8;color:#12242F;font-size:14px}
  a{color:#0FA3C9;text-decoration:none}
  header{background:#0A2C40;color:#fff;padding:12px 18px;display:flex;align-items:center;gap:16px;position:sticky;top:0;z-index:5}
  header b{font-size:16px}
  header nav{display:flex;gap:14px;font-size:13.5px}
  header nav a{color:#CDE7F2;padding:6px 10px;border-radius:9px}
  header nav a.on{background:#12B5A5;color:#fff;font-weight:bold}
  header .out{margin-inline-start:auto;font-size:12.5px;color:#FFD2D2}
  main{max-width:1100px;margin:20px auto;padding:0 14px}
  .card{background:#fff;border:1px solid #E1EAF0;border-radius:16px;padding:18px;margin-bottom:16px;box-shadow:0 4px 14px rgba(10,44,64,.05)}
  .card h2{font-size:15.5px;color:#0A2C40;margin-bottom:12px}
  table{width:100%;border-collapse:collapse;font-size:12.8px}
  th,td{padding:8px 9px;border-bottom:1px solid #EDF2F5;text-align:right;vertical-align:top}
  th{color:#607D8B;font-size:11.5px;background:#F8FBFC}
  tr:hover td{background:#FBFEFF}
  .stat{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-bottom:16px}
  .st{background:#fff;border:1px solid #E1EAF0;border-radius:16px;padding:16px;text-align:center}
  .st .n{font-size:24px;font-weight:bold;color:#0A2C40}
  .st .t{font-size:12px;color:#78909C;margin-top:4px}
  input,select,textarea{font-family:inherit;font-size:13px;padding:7px 9px;border:1px solid #CFD8DC;border-radius:9px;width:100%}
  textarea{min-height:90px}
  button,.btn{display:inline-block;border:0;border-radius:10px;padding:8px 16px;background:linear-gradient(135deg,#12B5A5,#0FA3C9);color:#fff;font-weight:bold;font-size:13px;cursor:pointer;text-decoration:none}
  .btn.red{background:#E74C3C}
  .btn.gray{background:#607D8B}
  .pill{display:inline-block;padding:2px 10px;border-radius:99px;font-size:11px;font-weight:bold}
  .pill.OK{background:#E8F5E9;color:#1B5E20}.pill.PENDING{background:#FFF8E1;color:#8D6E00}.pill.NO{background:#FFEBEE;color:#B71C1C}
  .err{background:#FFEBEE;color:#B71C1C;padding:9px 12px;border-radius:10px;margin-bottom:12px;font-size:13px}
  .ok{background:#E8F5E9;color:#1B5E20;padding:9px 12px;border-radius:10px;margin-bottom:12px;font-size:13px}
  pre{background:#0E2A3B;color:#CFE8F2;padding:12px;border-radius:10px;font-size:12px;overflow:auto;direction:ltr;text-align:left;max-height:300px}
  form.inline{display:inline}
  .row{display:flex;gap:10px;flex-wrap:wrap}.row>*{flex:1;min-width:130px}
  .muted{color:#78909C;font-size:11.5px}
</style></head><body>
<header>
  <b>🧭 پنل «یار»</b>
  <nav>
    <a href="?page=dash" class="<?= $page === 'dash' ? 'on' : '' ?>">داشبورد</a>
    <a href="?page=bldgs" class="<?= $page === 'bldgs' ? 'on' : '' ?>">ساختمان‌ها</a>
    <a href="?page=res" class="<?= $page === 'res' ? 'on' : '' ?>">اهالی</a>
    <a href="?page=msg" class="<?= $page === 'msg' ? 'on' : '' ?>">پیام‌ها</a>
    <a href="?page=data" class="<?= $page === 'data' ? 'on' : '' ?>">داده‌ها</a>
    <a href="?page=bcast" class="<?= $page === 'bcast' ? 'on' : '' ?>">📢 پیام گروهی</a>
    <a href="?page=bale" class="<?= $page === 'bale' ? 'on' : '' ?>">💬 بله</a>
  </nav>
  <a class="out" href="?logout=1">خروج</a>
</header>
<main>
<?php
if ($msg) out_err($msg);
if ($ok_msg) out_ok($ok_msg);

try {
if ($page === 'dash') {
    $b  = db()->query('SELECT COUNT(*) c FROM ngy_bldg')->fetch()['c'];
    $rOK  = db()->query('SELECT COUNT(*) c FROM ngy_res WHERE status=\'OK\'')->fetch()['c'];
    $rP = db()->query('SELECT COUNT(*) c FROM ngy_res WHERE status=\'PENDING\'')->fetch()['c'];
    $m24 = db()->query('SELECT COUNT(*) c FROM ngy_msg WHERE ts>' . (time() - 86400))->fetch()['c'];
    echo '<div class="stat">
      <div class="st"><div class="n">' . $b . '</div><div class="t">ساختمان فعال</div></div>
      <div class="st"><div class="n">' . $rOK . '</div><div class="t">اهالی تأییدشده</div></div>
      <div class="st"><div class="n">' . $rP . '</div><div class="t">در انتظار تأیید</div></div>
      <div class="st"><div class="n">' . $m24 . '</div><div class="t">پیام ۲۴ ساعت اخیر</div></div>
    </div>';
    echo '<div class="card"><h2>آخرین ساختمان‌ها</h2><table><tr><th>کد (شمارهٔ مدیر)</th><th>نام</th><th>واحد</th><th>اهالی</th><th>ثبت</th></tr>';
    foreach (db()->query('SELECT * FROM ngy_bldg ORDER BY ts DESC LIMIT 10') as $r) {
        $st = db()->prepare('SELECT COUNT(*) c FROM ngy_res WHERE code=? AND status=\'OK\''); $st->execute([$r['code']]);
        echo '<tr><td dir="ltr">' . h($r['code']) . '</td><td>' . h($r['name']) . '</td><td>' . $r['units'] . '</td><td>' . $st->fetch()['c'] . '</td><td>' . date('Y/m/d H:i', $r['ts']) . '</td></tr>';
    }
    echo '</table></div>';
    echo '<div class="card"><h2>آخرین درخواست‌های عضویت</h2><table><tr><th>ساختمان</th><th>نام</th><th>واحد</th><th>نقش</th><th>وضعیت</th><th></th></tr>';
    foreach (db()->query('SELECT * FROM ngy_res WHERE status=\'PENDING\' ORDER BY ts DESC LIMIT 8') as $r) {
        echo '<tr><td dir="ltr">' . h($r['code']) . '</td><td>' . h($r['name']) . '</td><td>' . h($r['unit']) . '</td><td>' . ($r['role'] === 'owner' ? 'مالک' : 'مستأجر') . '</td><td><span class="pill PENDING">در انتظار</span></td>
        <td><form class="inline" method="post"><input type="hidden" name="act" value="res_save"><input type="hidden" name="csrf" value="' . $_SESSION['csrf'] . '"><input type="hidden" name="code" value="' . h($r['code']) . '"><input type="hidden" name="uid" value="' . h($r['uid']) . '"><input type="hidden" name="name" value="' . h($r['name']) . '"><input type="hidden" name="mobile" value="' . h($r['mobile']) . '"><input type="hidden" name="unit" value="' . h($r['unit']) . '"><input type="hidden" name="role" value="' . h($r['role']) . '"><input type="hidden" name="car" value="' . h($r['car']) . '"><input type="hidden" name="plate" value="' . h($r['plate']) . '"><input type="hidden" name="park" value="' . h($r['park']) . '"><input type="hidden" name="nid" value="' . h($r['nid']) . '"><input type="hidden" name="nres" value="' . (int)$r['nres'] . '"><input type="hidden" name="status" value="OK"><button>✅ تأیید</button></form></td></tr>';
    }
    echo '</table></div>';
}

elseif ($page === 'bldgs') {
    echo '<div class="card"><h2>ساختمان‌ها</h2><table><tr><th>کد</th><th>نام</th><th>واحد</th><th>آدرس</th><th>عملیات</th></tr>';
    foreach (db()->query('SELECT * FROM ngy_bldg ORDER BY ts DESC') as $r) {
        echo '<tr><td dir="ltr"><a href="?page=res&code=' . h($r['code']) . '">' . h($r['code']) . '</a></td><td>' . h($r['name']) . '</td><td>' . $r['units'] . '</td><td>' . h($r['addr']) . '</td>
        <td><form class="inline" method="post">' . csrf_field() . '<input type="hidden" name="act" value="bldg_del"><input type="hidden" name="code" value="' . h($r['code']) . '"><button class="btn red" onclick="return confirm(\'همهٔ داده‌های این ساختمان حذف شود؟\')">حذف کامل</button></form></td></tr>';
    }
    echo '</table></div>';
    if ($qcode) {
        $st = db()->prepare('SELECT * FROM ngy_bldg WHERE code=?'); $st->execute([$qcode]);
        if ($r = $st->fetch(PDO::FETCH_ASSOC)) {
            echo '<div class="card"><h2>ویرایش ساختمان ' . h($r['code']) . '</h2>
            <form method="post">' . csrf_field() . '<input type="hidden" name="act" value="bldg_save"><input type="hidden" name="code" value="' . h($r['code']) . '">
            <div class="row"><div><label>نام</label><input name="name" value="' . h($r['name']) . '"></div>
            <div><label>تعداد واحد</label><input name="units" value="' . (int)$r['units'] . '"></div></div>
            <div style="margin-top:8px"><label>آدرس</label><input name="addr" value="' . h($r['addr']) . '"></div>
            <button style="margin-top:12px">💾 ذخیره</button></form></div>';
        }
    }
}

elseif ($page === 'res') {
    if (!$qcode) {
        echo '<div class="card"><h2>اهالی — ساختمان را انتخاب کن</h2><form method="get"><input type="hidden" name="page" value="res">' . csrf_field() . '
        <div class="row"><div><label>کد ساختمان (شمارهٔ مدیر)</label><input name="code" dir="ltr" placeholder="09xxxxxxxxx"></div></div><button style="margin-top:10px">نمایش</button></form></div>';
        echo '<div class="card"><h2>درخواست‌های در انتظار (همهٔ ساختمان‌ها)</h2><table><tr><th>ساختمان</th><th>نام</th><th>واحد</th><th>همراه</th><th></th></tr>';
        foreach (db()->query('SELECT * FROM ngy_res WHERE status=\'PENDING\' ORDER BY ts DESC LIMIT 50') as $r) {
            echo '<tr><td dir="ltr">' . h($r['code']) . '</td><td>' . h($r['name']) . '</td><td>' . h($r['unit']) . '</td><td dir="ltr">' . h($r['mobile']) . '</td>
            <td><form class="inline" method="post">' . csrf_field() . '<input type="hidden" name="act" value="res_save"><input type="hidden" name="code" value="' . h($r['code']) . '"><input type="hidden" name="uid" value="' . h($r['uid']) . '"><input type="hidden" name="name" value="' . h($r['name']) . '"><input type="hidden" name="mobile" value="' . h($r['mobile']) . '"><input type="hidden" name="unit" value="' . h($r['unit']) . '"><input type="hidden" name="role" value="' . h($r['role']) . '"><input type="hidden" name="car" value="' . h($r['car']) . '"><input type="hidden" name="plate" value="' . h($r['plate']) . '"><input type="hidden" name="park" value="' . h($r['park']) . '"><input type="hidden" name="nid" value="' . h($r['nid']) . '"><input type="hidden" name="nres" value="' . (int)$r['nres'] . '"><input type="hidden" name="status" value="OK"><button>✅ تأیید</button></form>
            <form class="inline" method="post" style="margin-inline-start:6px">' . csrf_field() . '<input type="hidden" name="act" value="res_del"><input type="hidden" name="code" value="' . h($r['code']) . '"><input type="hidden" name="uid" value="' . h($r['uid']) . '"><button class="btn red" onclick="return confirm(\'حذف؟\')">حذف</button></form></td></tr>';
        }
        echo '</table></div>';
    } else {
        $st = db()->prepare('SELECT * FROM ngy_res WHERE code=? ORDER BY unit'); $st->execute([$qcode]);
        echo '<div class="card"><h2>اهالی ساختمان ' . h($qcode) . ' <a class="btn gray" style="float:left" href="?page=res">بازگشت</a></h2></div>';
        foreach ($st->fetchAll(PDO::FETCH_ASSOC) as $r) {
            echo '<div class="card"><form method="post">' . csrf_field() . '
            <input type="hidden" name="act" value="res_save"><input type="hidden" name="code" value="' . h($r['code']) . '"><input type="hidden" name="uid" value="' . h($r['uid']) . '">
            <div class="row">
              <div><label>نام</label><input name="name" value="' . h($r['name']) . '"></div>
              <div><label>همراه</label><input name="mobile" dir="ltr" value="' . h($r['mobile']) . '"></div>
              <div><label>واحد</label><input name="unit" value="' . h($r['unit']) . '"></div>
              <div><label>نقش</label><select name="role"><option value="tenant"' . ($r['role'] !== 'owner' ? ' selected' : '') . '>مستأجر</option><option value="owner"' . ($r['role'] === 'owner' ? ' selected' : '') . '>مالک</option></select></div>
            </div>
            <div class="row" style="margin-top:8px">
              <div><label>خودرو</label><input name="car" value="' . h($r['car']) . '"></div>
              <div><label>پلاک</label><input name="plate" value="' . h($r['plate']) . '"></div>
              <div><label>پارکینگ</label><input name="park" value="' . h($r['park']) . '"></div>
              <div><label>کد ملی</label><input name="nid" dir="ltr" value="' . h($r['nid']) . '"></div>
              <div><label>نفرات</label><input name="nres" value="' . (int)$r['nres'] . '"></div>
              <div><label>وضعیت</label><select name="status"><option value="PENDING"' . ($r['status'] === 'PENDING' ? ' selected' : '') . '>در انتظار</option><option value="OK"' . ($r['status'] === 'OK' ? ' selected' : '') . '>تأیید</option><option value="NO"' . ($r['status'] === 'NO' ? ' selected' : '') . '>رد</option></select></div>
            </div>
            <div style="margin-top:10px"><button>💾 ذخیره</button>
            <button class="btn red" formaction="index.php?page=res&code=' . h($qcode) . '" formmethod="post" onclick="this.form.act.value=\'res_del\';return confirm(\'حذف این رکورد؟\')" formnovalidate>حذف</button>
            <input type="hidden" name="act2" value=""></div>
            </form></div>';
        }
    }
}

elseif ($page === 'msg') {
    if (!$qcode) {
        echo '<div class="card"><h2>پیام‌ها — ساختمان را انتخاب کن</h2><form method="get"><input type="hidden" name="page" value="msg">' . csrf_field() . '
        <div class="row"><div><label>کد ساختمان</label><input name="code" dir="ltr"></div></div><button style="margin-top:10px">نمایش</button></form></div>';
    } else {
        $st = db()->prepare('SELECT * FROM ngy_msg WHERE code=? ORDER BY id DESC LIMIT 120'); $st->execute([$qcode]);
        echo '<div class="card"><h2>پیام‌های ' . h($qcode) . ' <a class="btn gray" style="float:left" href="?page=msg">بازگشت</a></h2><table><tr><th>#</th><th>به</th><th>از</th><th>نوع</th><th>متن</th><th>زمان</th><th></th></tr>';
        foreach ($st->fetchAll(PDO::FETCH_ASSOC) as $r) {
            $txt = mb_substr(json_decode($r['payload'], true) === null ? $r['payload'] : ($r['kind'] === 'NEWS' ? json_decode($r['payload'], true)['body'] ?? $r['payload'] : $r['payload']), 0, 90);
            echo '<tr><td>' . $r['id'] . '</td><td dir="ltr">' . h($r['dst']) . '</td><td dir="ltr">' . h($r['src']) . '</td><td>' . h($r['kind']) . '</td><td>' . h($txt) . '</td><td>' . date('m/d H:i', $r['ts']) . '</td>
            <td><form class="inline" method="post">' . csrf_field() . '<input type="hidden" name="act" value="msg_del"><input type="hidden" name="code" value="' . h($r['code']) . '"><input type="hidden" name="id" value="' . $r['id'] . '"><button class="btn red" onclick="return confirm(\'حذف پیام؟\')">✕</button></form></td></tr>';
        }
        echo '</table></div>';
    }
}

elseif ($page === 'data') {
    if (!$qcode) {
        echo '<div class="card"><h2>داده‌های وضعیت مشترک — ساختمان را انتخاب کن</h2><form method="get"><input type="hidden" name="page" value="data">' . csrf_field() . '
        <div class="row"><div><label>کد ساختمان</label><input name="code" dir="ltr"></div></div><button style="margin-top:10px">نمایش</button></form></div>';
    } else {
        $st = db()->prepare('SELECT * FROM ngy_data WHERE code=? ORDER BY k'); $st->execute([$qcode]);
        echo '<div class="card"><h2>کلیدهای دادهٔ ' . h($qcode) . ' <span class="muted">(charges / packages / news / guest_reqs / roster…)</span></h2></div>';
        foreach ($st->fetchAll(PDO::FETCH_ASSOC) as $r) {
            $pretty = json_encode(json_decode($r['payload'], true), JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
            if ($pretty === null) $pretty = $r['payload'];
            echo '<div class="card"><h2>🔑 ' . h($r['k']) . ' <span class="muted">' . date('Y/m/d H:i', $r['ts']) . '</span></h2>
            <form method="post">' . csrf_field() . '<input type="hidden" name="act" value="data_save"><input type="hidden" name="code" value="' . h($qcode) . '"><input type="hidden" name="k" value="' . h($r['k']) . '">
            <textarea name="payload" dir="ltr" style="min-height:140px;font-size:11.5px">' . h($pretty) . '</textarea>
            <div style="margin-top:8px"><button>💾 ذخیره</button>
            <button class="btn red" onclick="this.form.act.value=\'data_del\';return confirm(\'کلید حذف شود؟\')">حذف کلید</button></div></form></div>';
        }
        echo '<div class="card"><h2>کلید تازه</h2><form method="post">' . csrf_field() . '<input type="hidden" name="act" value="data_save"><input type="hidden" name="code" value="' . h($qcode) . '">
        <div class="row"><div><label>کلید</label><input name="k" dir="ltr"></div></div>
        <textarea name="payload" dir="ltr" style="margin-top:8px">[]</textarea>
        <button style="margin-top:8px">افزودن</button></form></div>';
    }
}

elseif ($page === 'bale') {
    $tot = db()->query('SELECT COUNT(*) c FROM ngy_bale')->fetch()['c'];
    $un = db()->query('SELECT COUNT(*) c FROM ngy_bale_log WHERE replied=0')->fetch()['c'];
    echo '<div class="stat">
      <div class="st"><div class="n">' . $tot . '</div><div class="t">عضو ثبت‌شدهٔ بله</div></div>
      <div class="st"><div class="n">' . $un . '</div><div class="t">سوال بی‌پاسخ</div></div>
    </div>';
    echo '<div class="card"><h2>سوالات بی‌پاسخ <span class="muted">— پاسخ‌ها به صف بات می‌رود و با اجرای بعدی cron ارسال می‌شود</span></h2>';
    $rows = db()->query('SELECT l.id, l.chat_id, l.msg, l.ts, b.name, b.family, b.mobile, b.city FROM ngy_bale_log l LEFT JOIN ngy_bale b ON b.chat_id=l.chat_id WHERE l.replied=0 ORDER BY l.id DESC LIMIT 50')->fetchAll(PDO::FETCH_ASSOC);
    if (!$rows) echo '<p class="muted">فعلاً سوالی بی‌پاسخ نیست.</p>';
    foreach ($rows as $r) {
        echo '<div style="border:1px solid #EDF2F5;border-radius:12px;padding:10px;margin-bottom:10px">
        <div style="font-size:13px">' . h($r['msg']) . '</div>
        <div class="muted" style="margin:4px 0">' . h(trim(($r['name'] ?? '') . ' ' . ($r['family'] ?? ''))) . ' • ' . h((string)$r['mobile']) . ' • ' . h((string)$r['city']) . ' • ' . date('Y/m/d H:i', $r['ts']) . '</div>
        <form method="post">' . csrf_field() . '<input type="hidden" name="act" value="bale_reply"><input type="hidden" name="chat" value="' . (int)$r['chat_id'] . '"><input type="hidden" name="logid" value="' . (int)$r['id'] . '">
        <textarea name="text" style="min-height:60px"></textarea>
        <button style="margin-top:6px">📤 ارسال پاسخ در بله</button></form></div>';
    }
    echo '</div>';
    echo '<div class="card"><h2>اعضای ثبت‌شده</h2><table><tr><th>نام</th><th>همراه</th><th>شهر</th><th>سوال‌ها</th><th>آخرین تماس</th></tr>';
    foreach (db()->query('SELECT b.*, (SELECT COUNT(*) FROM ngy_bale_log l WHERE l.chat_id=b.chat_id) q FROM ngy_bale b ORDER BY b.ts DESC LIMIT 200') as $r) {
        echo '<tr><td>' . h($r['name'] . ' ' . $r['family']) . '</td><td dir="ltr">' . h($r['mobile']) . '</td><td>' . h($r['city']) . '</td><td>' . $r['q'] . '</td><td>' . date('Y/m/d', $r['ts']) . '</td></tr>';
    }
    echo '</table></div>';
    echo '<div class="card"><h2>صف/تاریخچهٔ ارسال</h2><table><tr><th>#</th><th>به</th><th>متن</th><th>وضعیت</th></tr>';
    foreach (db()->query('SELECT * FROM ngy_bale_out ORDER BY id DESC LIMIT 30') as $r) {
        echo '<tr><td>' . $r['id'] . '</td><td dir="ltr">' . $r['chat_id'] . '</td><td>' . h(mb_substr($r['text'], 0, 70)) . '</td><td>' . ($r['sent'] ? '<span class="pill OK">ارسال شد</span>' : '<span class="pill PENDING">در صف (' . $r['tries'] . ' تلاش)</span>') . '</td></tr>';
    }
    echo '</table></div>';
}

elseif ($page === 'bcast') {
    echo '<div class="card"><h2>📢 ارسال پیام گروهی</h2>
    <form method="post">' . csrf_field() . '<input type="hidden" name="act" value="broadcast">
    <div class="row">
      <div><label>مقصد</label><select name="to">
        <option value="modir">مدیرها (اپ مدیر یار)</option>
        <option value="negahban">نگهبان‌ها (اپ نگهبان‌یار)</option>
        <option value="residents">همهٔ اهالی تأییدشده (ساکن‌یار)</option>
      </select></div>
      <div><label>دامنه</label><select name="scope" onchange="document.getElementById(\'onec\').style.display=this.value===\'one\'?\'block\':\'none\'">
        <option value="all">همهٔ ساختمان‌ها</option>
        <option value="one">یک ساختمان مشخص</option>
      </select></div>
    </div>
    <div id="onec" style="display:none;margin-top:8px"><label>کد ساختمان</label><input name="code" dir="ltr"></div>
    <div style="margin-top:10px"><label>متن پیام</label><textarea name="text" placeholder="متن اطلاعیه…"></textarea></div>
    <button style="margin-top:12px">📤 ارسال</button>
    <p class="muted" style="margin-top:8px">پیام‌ها در صندوق مقصدها می‌نشیند و اپ‌ها هنگام باز شدن/سرور خودکار می‌گیرندشان. سقف ۲۰۰۰ مقصد.</p>
    </form></div>';
}
} catch (Exception $e) { out_err('خطا: ' . $e->getMessage()); }
?>
</main></body></html>

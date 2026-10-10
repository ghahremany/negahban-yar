<?php
// هاب ارتباطی سوئیت «یار» — نسخهٔ چندساختمانی
// احراز: هدر X-NGY-KEY + کد ساختمان (= شمارهٔ همراه مدیر)
// همهٔ جداول کلید ساختمان دارند و با ایندکس پوشش داده شده‌اند تا چند ده ساختمان را جواب دهد.
header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

$APP_KEY = 'NGY-CLOUD-7c4e2f91a8d63b50e7f1c2a493d85b6e';
$MSG_TTL = 1209600;   // پیام‌ها ۱۴ روز
$MSG_KEEP = 400;      // حداکثر پیام هر صندوق
$DATA_MAX = 2000000;  // سقف هر کلید داده (~۲MB)
$DATA_ITEMS = 800;    // حداکثر آیتم آرایه موقع append

function out($ok, $extra = []) {
    echo json_encode(array_merge(['ok' => $ok], $extra), JSON_UNESCAPED_UNICODE);
    exit;
}

$key = isset($_SERVER['HTTP_X_NGY_KEY']) ? $_SERVER['HTTP_X_NGY_KEY'] : '';
if (!hash_equals($APP_KEY, (string)$key)) out(false, ['err' => 'auth']);

$op = isset($_REQUEST['op']) ? $_REQUEST['op'] : '';
$code = isset($_REQUEST['code']) ? strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string)$_REQUEST['code'])) : '';

try {
    $db = new PDO(
        'mysql:host=sql308.infinityfree.com;dbname=if0_43102805_negahbanyar;charset=utf8mb4',
        'if0_43102805',
        'KnBmZnL22C',
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_TIMEOUT => 12]
    );

    // ---------- اسکیمای چندساختمانی ----------
    // ngy_bldg: شناسنامهٔ هر ساختمان (کد = شمارهٔ همراه مدیر)
    $db->exec("CREATE TABLE IF NOT EXISTS ngy_bldg (
        code VARCHAR(16) PRIMARY KEY,
        name VARCHAR(120) NOT NULL DEFAULT '',
        units INT NOT NULL DEFAULT 0,
        addr VARCHAR(200) NOT NULL DEFAULT '',
        ts INT NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    // وضعیت مشترک هر ساختمان (کلید/مقدار؛ آرایه‌ها append میشوند)
    $db->exec("CREATE TABLE IF NOT EXISTS ngy_data (
        code VARCHAR(16) NOT NULL,
        k VARCHAR(64) NOT NULL,
        payload MEDIUMTEXT,
        ts INT NOT NULL,
        PRIMARY KEY (code, k),
        KEY kidx (code, ts)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    // صندوق پیام هر ساختمان
    $db->exec("CREATE TABLE IF NOT EXISTS ngy_msg (
        id INT AUTO_INCREMENT PRIMARY KEY,
        code VARCHAR(16) NOT NULL,
        dst VARCHAR(80) NOT NULL,
        src VARCHAR(80) NOT NULL,
        kind VARCHAR(32) NOT NULL,
        payload MEDIUMTEXT,
        ts INT NOT NULL,
        KEY dstidx (code, dst, id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    // اهالیِ ثبت‌شدهٔ هر ساختمان (ثبت‌نام ساکن‌یار + تأیید مدیر)
    $db->exec("CREATE TABLE IF NOT EXISTS ngy_res (
        id INT AUTO_INCREMENT PRIMARY KEY,
        code VARCHAR(16) NOT NULL,
        uid VARCHAR(20) NOT NULL,
        name VARCHAR(80) NOT NULL,
        mobile VARCHAR(15) NOT NULL,
        unit VARCHAR(12) NOT NULL DEFAULT '',
        role VARCHAR(8) NOT NULL DEFAULT 'tenant',      -- owner | tenant
        car VARCHAR(40) NOT NULL DEFAULT '',
        plate VARCHAR(20) NOT NULL DEFAULT '',
        park VARCHAR(20) NOT NULL DEFAULT '',
        nid VARCHAR(12) NOT NULL DEFAULT '',
        nres SMALLINT NOT NULL DEFAULT 0,               -- نفرات ساکن در واحد
        status VARCHAR(10) NOT NULL DEFAULT 'PENDING',  -- PENDING | OK | NO
        ts INT NOT NULL,
        UNIQUE KEY uq (code, uid),
        KEY midx (code, status, id),
        KEY mbidx (code, mobile)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // مهاجرت جدول‌های قدیمی (ستون‌های تازه اگر نبودند)
    try { $db->exec("ALTER TABLE ngy_bldg ADD COLUMN units INT NOT NULL DEFAULT 0"); } catch (Exception $e) {}
    try { $db->exec("ALTER TABLE ngy_bldg ADD COLUMN addr VARCHAR(200) NOT NULL DEFAULT ''"); } catch (Exception $e) {}

    // ---------- ابزار نرمال‌سازی موبایل ----------
    function normMobile($raw) {
        $m = preg_replace('/[^0-9]/', '', ($raw === null ? '' : $raw));
        if (strpos($m, '0098') === 0) $m = '0' . substr($m, 4);
        if (strlen($m) === 12 && strpos($m, '98') === 0) $m = '0' . substr($m, 2);
        if (strlen($m) === 10 && strpos($m, '9') === 0) $m = '0' . $m;
        return $m;
    }

    // ---------- ثبت ساختمان (کد = شمارهٔ همراه مدیر؛ idempotent) ----------
    if ($op === 'reg') {
        $name = trim((string)($_POST['name'] ?? ''));
        if (mb_strlen($name) > 100) $name = mb_substr($name, 0, 100);
        $mobile = normMobile((string)($_POST['mobile'] ?? ''));
        if (strlen($mobile) !== 11 || strpos($mobile, '09') !== 0) out(false, ['err' => 'mobile']);
        $units = (int)($_POST['units'] ?? 0); if ($units < 0 || $units > 500) $units = 0;
        $addr = mb_substr(trim((string)($_POST['addr'] ?? '')), 0, 190);
        $st = $db->prepare('SELECT code FROM ngy_bldg WHERE code=?');
        $st->execute([$mobile]);
        if ($r = $st->fetch(PDO::FETCH_ASSOC)) {
            // تکمیل اطلاعات اگر فرستاده شده باشد (بدون دست‌زدن به چیزی که نیامده)
            if ($name !== '') $db->prepare('UPDATE ngy_bldg SET name=? WHERE code=?')->execute([$name, $mobile]);
            if ($units > 0)   $db->prepare('UPDATE ngy_bldg SET units=? WHERE code=?')->execute([$units, $mobile]);
            if ($addr !== '') $db->prepare('UPDATE ngy_bldg SET addr=? WHERE code=?')->execute([$addr, $mobile]);
            out(true, ['code' => $r['code'], 'existing' => true]);
        }
        $db->prepare('INSERT INTO ngy_bldg (code, name, units, addr, ts) VALUES (?,?,?,?,?)')
           ->execute([$mobile, $name, $units, $addr, time()]);
        out(true, ['code' => $mobile]);
    }

    if ($code === '' || strlen($code) < 4) out(false, ['err' => 'code']);
    $st = $db->prepare('SELECT 1 FROM ngy_bldg WHERE code=?');
    $st->execute([$code]);
    if (!$st->fetch()) out(false, ['err' => 'nobldg']);

    // ---------- وضعیت مشترک ----------
    if ($op === 'data_get') {
        $k = substr((string)($_GET['key'] ?? ''), 0, 64);
        if ($k === '') out(false, ['err' => 'key']);
        $st = $db->prepare('SELECT payload, ts FROM ngy_data WHERE code=? AND k=?');
        $st->execute([$code, $k]);
        $r = $st->fetch(PDO::FETCH_ASSOC);
        if (!$r) out(false, ['err' => 'empty']);
        out(true, ['payload' => $r['payload'], 'ts' => (int)$r['ts']]);
    }

    if ($op === 'data_put') {
        $k = substr((string)($_POST['key'] ?? ''), 0, 64);
        $payload = (string)($_POST['payload'] ?? '');
        $mode = ($_POST['mode'] ?? 'replace') === 'append' ? 'append' : 'replace';
        if ($k === '') out(false, ['err' => 'key']);
        if (strlen($payload) > $DATA_MAX) out(false, ['err' => 'toolarge']);
        if ($mode === 'append') {
            $st = $db->prepare('SELECT payload FROM ngy_data WHERE code=? AND k=?');
            $st->execute([$code, $k]);
            $r = $st->fetch(PDO::FETCH_ASSOC);
            if ($r) {
                $arr = json_decode($r['payload'], true);
                if (!is_array($arr)) $arr = [];
                $new = json_decode($payload, true);
                if (is_array($new)) { foreach ($new as $it) $arr[] = $it; } else $arr[] = $payload;
                if (count($arr) > $DATA_ITEMS) $arr = array_slice($arr, -$DATA_ITEMS);
                $payload = json_encode($arr, JSON_UNESCAPED_UNICODE);
            }
        }
        $db->prepare('INSERT INTO ngy_data (code, k, payload, ts) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE payload=VALUES(payload), ts=VALUES(ts)')
            ->execute([$code, $k, $payload, time()]);
        out(true, ['ts' => time()]);
    }

    // ---------- ثبت‌نام اهالی (ساکن‌یار) ----------
    if ($op === 'res_reg') {
        $uid = strtoupper(substr(preg_replace('/[^A-Za-z0-9]/', '', (string)($_POST['uid'] ?? '')), 0, 20));
        if (strlen($uid) < 4) out(false, ['err' => 'uid']);
        $name = mb_substr(trim((string)($_POST['name'] ?? '')), 0, 80);
        $mobile = normMobile((string)($_POST['mobile'] ?? ''));
        $unit = substr(trim((string)($_POST['unit'] ?? '')), 0, 12);
        $role = ($_POST['role'] ?? 'tenant') === 'owner' ? 'owner' : 'tenant';
        $car = mb_substr(trim((string)($_POST['car'] ?? '')), 0, 38);
        $plate = substr(trim((string)($_POST['plate'] ?? '')), 0, 18);
        $park = substr(trim((string)($_POST['park'] ?? '')), 0, 18);
        $nid = substr(preg_replace('/[^0-9]/', '', (string)($_POST['nid'] ?? '')), 0, 12);
        $nres = (int)($_POST['nres'] ?? 0); if ($nres < 0 || $nres > 50) $nres = 0;
        if ($name === '' || strlen($mobile) !== 11 || $unit === '') out(false, ['err' => 'field']);
        // هر واحد فقط یک ثبت‌نام فعال؛ ثبتِ دوباره = ویرایش همان ردیف
        $st = $db->prepare('SELECT id, uid, status FROM ngy_res WHERE code=? AND unit=? ORDER BY id DESC LIMIT 1');
        $st->execute([$code, $unit]);
        $prev = $st->fetch(PDO::FETCH_ASSOC);
        if ($prev && $prev['uid'] !== $uid && $prev['status'] === 'OK') {
            out(false, ['err' => 'taken']); // واحد قبلاً به کسی تعلق گرفته
        }
        if ($prev && $prev['uid'] === $uid) {
            $db->prepare('UPDATE ngy_res SET name=?, mobile=?, role=?, car=?, plate=?, park=?, nid=?, nres=?, status=IF(status=\'NO\',\'PENDING\',status), ts=? WHERE id=?')
               ->execute([$name, $mobile, $role, $car, $plate, $park, $nid, $nres, time(), $prev['id']]);
            out(true, ['id' => (int)$prev['id'], 'status' => 'updated']);
        }
        $db->prepare('INSERT INTO ngy_res (code, uid, name, mobile, unit, role, car, plate, park, nid, nres, status, ts) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)')
           ->execute([$code, $uid, $name, $mobile, $unit, $role, $car, $plate, $park, $nid, $nres, 'PENDING', time()]);
        // اطلاع به مدیر
        $j = json_encode(['uid'=>$uid,'name'=>$name,'unit'=>$unit,'role'=>$role,'mobile'=>$mobile,
                          'car'=>$car,'plate'=>$plate,'park'=>$park,'nres'=>$nres], JSON_UNESCAPED_UNICODE);
        $db->prepare('INSERT INTO ngy_msg (code, dst, src, kind, payload, ts) VALUES (?,?,?,?,?,?)')
           ->execute([$code, 'modir', 'saken:'.$uid, 'REG_REQ', $j, time()]);
        out(true, ['id' => (int)$db->lastInsertId(), 'status' => 'PENDING']);
    }

    /** خواندن وضعیت ثبت‌نام این گوشی */
    if ($op === 'res_status') {
        $uid = strtoupper(substr(preg_replace('/[^A-Za-z0-9]/', '', (string)($_GET['uid'] ?? '')), 0, 20));
        $st = $db->prepare('SELECT uid, name, unit, role, status FROM ngy_res WHERE code=? AND uid=? ORDER BY id DESC LIMIT 1');
        $st->execute([$code, $uid]);
        $r = $st->fetch(PDO::FETCH_ASSOC);
        if (!$r) out(true, ['status' => 'none']);
        $r['id'] = (int)$r['id'];
        out(true, $r);
    }

    /** فهرست اهالی برای مدیریت (تأییدشده‌ها) یا خودش */
    if ($op === 'res_list') {
        $only = ($_GET['filter'] ?? '') === 'pending' ? 'PENDING' : 'OK';
        $st = $db->prepare('SELECT uid, name, mobile, unit, role, car, plate, park, nres, status, ts FROM ngy_res WHERE code=? AND status=? ORDER BY unit LIMIT 400');
        $st->execute([$code, $only]);
        $items = [];
        foreach ($st->fetchAll(PDO::FETCH_ASSOC) as $r) {
            $r['id'] = 0; $r['ts'] = (int)$r['ts'];
            $items[] = $r;
        }
        out(true, ['items' => $items]);
    }

    /** تأیید/رد توسط مدیر */
    if ($op === 'res_decide') {
        $uid = strtoupper(substr(preg_replace('/[^A-Za-z0-9]/', '', (string)($_POST['uid'] ?? '')), 0, 20));
        $ok = ($_POST['ok'] ?? '0') === '1';
        $status = $ok ? 'OK' : 'NO';
        $st = $db->prepare('UPDATE ngy_res SET status=? WHERE code=? AND uid=? ORDER BY id DESC LIMIT 1');
        $st->execute([$status, $code, $uid]);
        if ($st->rowCount() === 0) out(false, ['err' => 'none']);
        $j = json_encode(['uid'=>$uid,'ok'=>$ok], JSON_UNESCAPED_UNICODE);
        $db->prepare('INSERT INTO ngy_msg (code, dst, src, kind, payload, ts) VALUES (?,?,?,?,?,?)')
           ->execute([$code, 'saken:'.$uid, 'modir', $ok ? 'REG_OK' : 'REG_NO', $j, time()]);
        out(true, []);
    }

    // ---------- چرخهٔ مهمان دوطرفه (ساکن ⇄ نگهبان ⇄ مدیر) ----------
    if ($op === 'guest_req') {
        $db->exec("CREATE TABLE IF NOT EXISTS ngy_guest (
            id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(20) NOT NULL, uid VARCHAR(40) NOT NULL,
            gname VARCHAR(80), mobile VARCHAR(15), plate VARCHAR(40), gwhen VARCHAR(60),
            status VARCHAR(10) DEFAULT 'PENDING', ts INT,
            KEY code_status (code, status), KEY code_id (code, id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $uid = strtoupper(substr(trim((string)($_POST['uid'] ?? '')), 0, 40));
        $gn  = mb_substr(trim((string)($_POST['name'] ?? '')), 0, 80);
        $gm  = substr(preg_replace('/[^0-9+]/', '', (string)($_POST['mobile'] ?? '')), 0, 15);
        $gp  = mb_substr(trim((string)($_POST['plate'] ?? '')), 0, 40);
        $gw  = mb_substr(trim((string)($_POST['when'] ?? '')), 0, 60);
        if ($uid === '' || $gn === '') out(false, ['err' => 'fields']);
        $db->prepare('INSERT INTO ngy_guest (code, uid, gname, mobile, plate, gwhen, status, ts) VALUES (?,?,?,?,?,?,\'PENDING\',?)')
           ->execute([$code, $uid, $gn, $gm, $gp, $gw, time()]);
        $gid = (int)$db->lastInsertId();
        $j = json_encode(['id'=>$gid, 'uid'=>$uid, 'host'=>'', 'unit'=>'', 'guest'=>$gn, 'mobile'=>$gm, 'plate'=>$gp, 'when'=>$gw], JSON_UNESCAPED_UNICODE);
        $db->prepare('INSERT INTO ngy_msg (code, dst, src, kind, payload, ts) VALUES (?,?,?,?,?,?)')
           ->execute([$code, 'modir', 'saken:'.$uid, 'GU_GUEST_REQ', $j, time()]);
        out(true, ['id' => $gid]);
    }

    if ($op === 'guest_decide') {
        $db->exec("CREATE TABLE IF NOT EXISTS ngy_guest (
            id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(20) NOT NULL, uid VARCHAR(40) NOT NULL,
            gname VARCHAR(80), mobile VARCHAR(15), plate VARCHAR(40), gwhen VARCHAR(60),
            status VARCHAR(10) DEFAULT 'PENDING', ts INT,
            KEY code_status (code, status), KEY code_id (code, id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $gid = (int)($_POST['id'] ?? 0);
        $ok  = (($_POST['ok'] ?? '') === '1');
        $by  = trim((string)($_POST['by'] ?? ''));   // guard | modir
        $st = $db->prepare('SELECT * FROM ngy_guest WHERE code=? AND id=?'); $st->execute([$code, $gid]);
        if (!$r = $st->fetch(PDO::FETCH_ASSOC)) out(false, ['err' => 'none']);
        if ($r['status'] !== 'PENDING') out(false, ['err' => 'done']);
        $db->prepare('UPDATE ngy_guest SET status=? WHERE code=? AND id=?')->execute([$ok ? 'OK' : 'NO', $code, $gid]);
        $jS = json_encode(['id'=>$gid, 'guest'=>$r['gname'], 'ok'=>$ok], JSON_UNESCAPED_UNICODE);
        $jN = json_encode(['id'=>$gid, 'guest'=>$r['gname'], 'mobile'=>$r['mobile'], 'plate'=>$r['plate'],
                           'when'=>$r['gwhen'], 'ok'=>$ok, 'by'=>$by], JSON_UNESCAPED_UNICODE);
        $ins = $db->prepare('INSERT INTO ngy_msg (code, dst, src, kind, payload, ts) VALUES (?,?,?,?,?,?)');
        $ins->execute([$code, 'saken:'.$r['uid'], $by === 'guard' ? 'negahban' : 'modir', $ok ? 'GU_OK' : 'GU_NO', $jS, time()]);
        $ins->execute([$code, 'negahban', 'server', $ok ? 'GU_OK' : 'GU_NO', $jN, time()]);
        out(true, []);
    }

    if ($op === 'guest_list') {
        $f = ($_GET['filter'] ?? 'pending') === 'OK' ? 'OK' : 'PENDING';
        $st = $db->prepare('SELECT id, uid, gname, mobile, plate, gwhen, status, ts FROM ngy_guest WHERE code=? AND status=? ORDER BY id DESC LIMIT 200');
        $st->execute([$code, $f]);
        $items = [];
        foreach ($st->fetchAll(PDO::FETCH_ASSOC) as $r)
            $items[] = ['id' => (int)$r['id'], 'uid' => $r['uid'], 'guest' => $r['gname'], 'mobile' => $r['mobile'],
                        'plate' => $r['plate'], 'when' => $r['gwhen'], 'status' => $r['status'], 'ts' => (int)$r['ts']];
        out(true, ['items' => $items]);
    }

    // ---------- صندوق پیام ----------
    if ($op === 'msg') {
        $dst = substr(trim((string)($_POST['to'] ?? '')), 0, 80);
        $src = substr(trim((string)($_POST['from'] ?? '')), 0, 80);
        $kind = substr(preg_replace('/[^A-Za-z0-9_]/', '', (string)($_POST['kind'] ?? '')), 0, 32);
        $payload = (string)($_POST['payload'] ?? '');
        if ($dst === '' || $kind === '') out(false, ['err' => 'dst']);
        if (strlen($payload) > $DATA_MAX) out(false, ['err' => 'toolarge']);
        $db->prepare('INSERT INTO ngy_msg (code, dst, src, kind, payload, ts) VALUES (?,?,?,?,?,?)')
            ->execute([$code, $dst, $src, $kind, $payload, time()]);
        out(true, ['id' => (int)$db->lastInsertId()]);
    }

    if ($op === 'msgs') {
        $dst = substr(trim((string)($_GET['to'] ?? '')), 0, 80);
        $after = (int)($_GET['after'] ?? 0);
        if ($dst === '') out(false, ['err' => 'dst']);
        if (strpos($dst, 'saken:') === 0) {
            // ساکن: پیام شخصی خودش + اطلاعیهٔ سراسری «saken»
            $st = $db->prepare('SELECT id, src, kind, payload, ts FROM ngy_msg
                WHERE code=? AND (dst=? OR dst=\'saken\') AND id>? ORDER BY id ASC LIMIT 120');
            $st->execute([$code, $dst, $after]);
        } else {
            $st = $db->prepare('SELECT id, src, kind, payload, ts FROM ngy_msg
                WHERE code=? AND dst=? AND id>? ORDER BY id ASC LIMIT 120');
            $st->execute([$code, $dst, $after]);
        }
        $items = [];
        $last = $after;
        foreach ($st->fetchAll(PDO::FETCH_ASSOC) as $r) {
            $items[] = ['id' => (int)$r['id'], 'src' => $r['src'], 'kind' => $r['kind'],
                        'payload' => $r['payload'], 'ts' => (int)$r['ts']];
            $last = (int)$r['id'];
        }
        out(true, ['items' => $items, 'last' => $last]);
    }

    // نظافت دوره‌ای: پیام‌های کهنه
    if ($op === 'clean') {
        $db->prepare('DELETE FROM ngy_msg WHERE code=? AND ts<?')->execute([$code, time() - $MSG_TTL]);
        $db->prepare('DELETE FROM ngy_msg WHERE code=? AND id IN (
            SELECT id FROM (SELECT id FROM ngy_msg WHERE code=? ORDER BY id DESC LIMIT 1 OFFSET ?) t
        )')->execute([$code, $code, $MSG_KEEP]);
        out(true, []);
    }

    out(false, ['err' => 'op']);
} catch (Exception $e) {
    out(false, ['err' => 'db']);
}

<?php
// TOTP (RFC 6238) — سازگار با Google Authenticator — بدون وابستگی

function b32_decode($s) {
    $alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    $s = strtoupper(preg_replace('/[^A-Za-z2-7]/', '', $s));
    $bits = '';
    for ($i = 0; $i < strlen($s); $i++) {
        $pos = strpos($alphabet, $s[$i]);
        if ($pos === false) continue;
        $bits .= str_pad(decbin($pos), 5, '0', STR_PAD_LEFT);
    }
    $out = '';
    foreach (str_split($bits, 8) as $byte) {
        if (strlen($byte) === 8) $out .= chr(bindec($byte));
    }
    return $out;
}

function totp_code($secret, $slice = null) {
    if ($slice === null) $slice = floor(time() / 30);
    $key = b32_decode($secret);
    $bin = pack('N*', 0) . pack('N*', $slice);
    $h = hash_hmac('sha1', $bin, $key, true);
    $off = ord(substr($h, -1)) & 0x0F;
    $val = (unpack('N', substr($h, $off, 4))[1] & 0x7FFFFFFF) % 1000000;
    return str_pad($val, 6, '0', STR_PAD_LEFT);
}

/** پذیرش کد جاری ± یک پنجره (اختلاف ساعت) */
function totp_verify($secret, $code) {
    $code = preg_replace('/[^0-9]/', '', (string)$code);
    if (strlen($code) !== 6) return false;
    $slice = floor(time() / 30);
    foreach ([$slice - 1, $slice, $slice + 1] as $s) {
        if (hash_equals(totp_code($secret, $s), $code)) return true;
    }
    return false;
}

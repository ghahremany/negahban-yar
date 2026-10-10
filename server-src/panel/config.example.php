<?php
// الگوی تنظیمات پنل — نسخهٔ واقعی (config.php) را روی سرور بساز و کامیت نکن.
$ADMIN_NAME  = 'توسعه‌دهنده';
$ADMIN_HASH  = hash_pbkdf2('sha256', 'YOUR-PASSWORD', 'ngy-panel-salt', 100000, 64);
$TOTP_SECRET = 'BASE32SECRET16';   // کلید Google Authenticator
$DB_HOST = 'sqlXXX.infinityfree.com';
$DB_NAME = 'if0XXXX_negy';
$DB_USER = 'if0XXXX';
$DB_PASS = 'DB-PASSWORD';
$BALE_TOKEN = 'BALE-BOT-TOKEN';   // توکن بات پشتیبانی بله
$BALE_BASE = 'https://tapi.bale.ai/bot';

# سرور (نسخهٔ پشتیبان کد)

- `api/hub.php` در گیت نیست (رمز دیتابیس داخلش است).
- `panel/` پنل توسعه‌دهنده — `config.php` واقعی روی سرور است و کامیت نمی‌شود (الگو: config.example.php).
- `bale/bot.php` بات پشتیبانی بله — توکن در `panel/config.php` (متغیر $BALE_TOKEN).
- مسیر روی سرور: `htdocs/bale/bot.php` + `htdocs/panel/`

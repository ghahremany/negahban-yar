# انتقال پروژه به GitHub — ۵ قدم

> **نکتهٔ امنیتی:** فایل `release.keystore` (کلید امضای اپ) عمداً داخل مخزن نیست و نباید عمومی شود.
> از آن نسخهٔ پشتیبان خصوصی نگه دار؛ بدون آن، آپدیت روی نصب‌های قبلی ممکن نیست.

## ۱) ساخت مخزن در گیت‌هاب
- وارد github.com شو → **New repository**
- نام: `negahban-yar` (یا هر نامی) → Public یا Private → **Create**
- هیچ فایل اولیه‌ای (README و…) انتخاب نکن

## ۲) اتصال مخزن محلی (همین پوشهٔ `negahban-app/`)
```bash
cd negahban-app
git remote add origin https://github.com/USERNAME/negahban-yar.git
```
(اگر بین جلسات `remote` پاک شده بود، فقط همین خط را دوباره بزن — تاریخچه کامیت‌ها سر جایش است)

## ۳) ورود
اولین بار که push کنی، گیت‌هاب رمز نمی‌خواهد؛ **Personal Access Token** می‌خواهد:
- GitHub → Settings → Developer settings → Personal access tokens → Generate (دسترسی `repo`)
- در جواب login: نام کاربری، و در جواب password: همان توکن را بده

## ۴) ارسال
```bash
git push -u origin master --tags
```

## ۵) بعد از هر تغییر نسخهٔ جدید
اینجا تغییرات را بده؛ من نسخه را بالا می‌برم، `CHANGELOG.md` را به‌روز می‌کنم و commit + tag می‌زنم؛ فقط `git push` بزن.
فایل `.github/workflows/build.yml` هم روی گیت‌هاب خودش APK را می‌سازد (تب Actions → Artifact).

---
## اگر git نصب نیست (ویندوز): git-scm.com — سپس همین دستورها در Git Bash
## ساخت مجدد APK روی هر لینوکس: `./build.sh` (JDK 11+ و اینترنت کافی است؛ SDK خودکار دانلود می‌شود)

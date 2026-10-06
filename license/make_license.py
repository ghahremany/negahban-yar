#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
تولید کد لایسنس نگهبان‌یار — ابزار خصوصی سازنده
کد را به خریدار میدهی؛ در اپ وارد میکند و تا انقضا فعال میشود.

مثال‌ها:
  python3 make_license.py --months 1          # یک‌ماهه
  python3 make_license.py --years 1           # یک‌ساله
  python3 make_license.py --years 2 --note "مجتمع نگین"
  python3 make_license.py --until 1407-01-01  # تا تاریخ شمسی مشخص
"""
import argparse
import datetime
import hashlib
import hmac
import sys

# ⚠️ همین کلید داخل اپ (License.java) است — اگر عوض کردی، در بیلد بعدی اپ هم عوض کن
SECRET = b"NGY-LIC-7d21a9c4e5b83f016a2d84c7f3b59e2a"


def j2g_date(y, m, d):
    """جلالی به میلادی (الگوریتم jdf)"""
    if y >= 979:
        gy, jy = 1600, y - 979
    else:
        gy, jy = 621, y
    days = 365 * jy + (jy // 33) * 8 + ((jy % 33) + 3) // 4 + 78 + d + \
        ((m - 1) * 31 if m < 7 else (m - 7) * 30 + 186)
    gy += 400 * (days // 146097)
    days %= 146097
    if days > 36524:
        gy += 100 * ((days - 1) // 36524)
        days = (days - 1) % 36524
        if days >= 365:
            days += 1
    gy += 4 * (days // 1461)
    days %= 1461
    if days > 365:
        gy += (days - 1) // 365
        days = (days - 1) % 365
    gd = days + 1
    leap = (gy % 4 == 0 and gy % 100 != 0) or (gy % 400 == 0)
    sa = [0, 31, 29 if leap else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    gm = 0
    while gm < 12 and gd > sa[gm + 1]:
        gd -= sa[gm + 1]
        gm += 1
    return datetime.date(gy, gm + 1, gd)


def make_code(expiry: datetime.date, note: str = ""):
    days = (expiry - datetime.date(1970, 1, 1)).days
    b36 = _to36(days).upper()
    sig = hmac.new(SECRET, ("LIC|" + b36).encode(), hashlib.sha256).hexdigest()[:8].upper()
    code = f"NGY-{b36}-{sig}"
    return code, days * 86400


def _to36(n):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    if n == 0:
        return "0"
    out = ""
    while n:
        out = digits[n % 36] + out
        n //= 36
    return out


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--months", type=int)
    g.add_argument("--years", type=int)
    g.add_argument("--until", help="تاریخ شمسی انقضا: 1407-01-01")
    ap.add_argument("--note", default="", help="یادداشت (مثلاً نام مجتمع)")
    ap.add_argument("--count", type=int, default=1, help="تعداد کد یکسان برای خریداران مختلف")
    a = ap.parse_args()

    today = datetime.date.today()
    if a.until:
        try:
            yy, mm, dd = (int(x) for x in a.until.split("-"))
            expiry = j2g_date(yy, mm, dd)
        except Exception:
            sys.exit("قالب تاریخ: 1407-01-01")
    elif a.months:
        expiry = today + datetime.timedelta(days=30 * a.months)
    else:
        expiry = today + datetime.timedelta(days=365 * a.years)

    print(f"{'— ' + a.note + ' — ' if a.note else ''}انقضا: {expiry.isoformat()}  ({(expiry - today).days} روز دیگر)")
    for i in range(a.count):
        code, _ = make_code(expiry, a.note)
        print(f"  {code}")


if __name__ == "__main__":
    main()

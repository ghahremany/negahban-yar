#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ابزار چاپ پلاک‌های QR سامانهٔ گشت نگهبان
هر پلاک: NGHBN|<شناسه>|<امضای HMAC>  — اپ فقط امضای درست را می‌پذیرد.

استفاده:
  python3 make_plaques.py --key <کلید ۶۴ کاراکتری هگز از تنظیمات اپ> \
      [--building "مجتمع مسکونی نگین"] \
      [--stations "1=لابی,2=پارکینگ,3=حیاط,4=پشت بام,5=انبار"] \
      [--out plaques.html]

خروجی: یک فایل HTML آمادهٔ چاپ (A4) با QR بزرگ برای هر ایستگاه.
"""
import argparse
import hashlib
import hmac
import html
import sys

try:
    import qrcode
    import qrcode.image.svg
except ImportError:
    sys.exit("کتابخانهٔ qrcode نصب نیست:  pip install qrcode")


def plaque_payload(key_hex: str, station_id: str) -> str:
    sig = hmac.new(bytes.fromhex(key_hex), station_id.encode("utf-8"), hashlib.sha256).hexdigest()[:10]
    return f"NGHBN|{station_id}|{sig}"


def qr_svg(payload: str) -> str:
    img = qrcode.make(payload, image_factory=qrcode.image.svg.SvgPathImage,
                      box_size=12, border=2, error_correction=qrcode.constants.ERROR_CORRECT_M)
    try:
        return img.to_string(encoding="unicode")
    except TypeError:
        return img.to_string()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True, help="کلید هگز (از صفحهٔ تنظیمات اپ)")
    ap.add_argument("--building", default="مجتمع مسکونی")
    ap.add_argument("--stations", default="1=لابی,2=پارکینگ,3=حیاط,4=پشت بام,5=انبار")
    ap.add_argument("--out", default="plaques.html")
    ap.add_argument("--pdf", default=None,
                    help="مسیر خروجی PDF برای چاپ مستقیم با پرینتر (A4، دو پلاک در هر برگه). "
                         "متن‌های PDF لاتین است؛ برای طرح کامل فارسی از خروجی HTML چاپ بگیر.")
    a = ap.parse_args()

    key = a.key.strip().lower()
    try:
        bytes.fromhex(key)
    except ValueError:
        sys.exit("کلید نامعتبر است — باید هگز زوج‌تعداد باشد (در اپ: ۶۴ کاراکتر)")

    stations = []
    for part in a.stations.split(","):
        part = part.strip()
        if not part:
            continue
        if "=" in part:
            sid, name = part.split("=", 1)
        elif "|" in part:
            sid, name = part.split("|", 1)
        else:
            sid, name = str(len(stations) + 1), part
        stations.append((sid.strip(), name.strip()))

    cards = []
    for sid, name in stations:
        payload = plaque_payload(key, sid)
        svg = qr_svg(payload)
        cards.append(f"""
      <div class="plaque">
        <div class="bname">{html.escape(a.building)}</div>
        <div class="qr">{svg}</div>
        <div class="sname">ایستگاه {html.escape(name)}</div>
        <div class="note">این پلاک فقط با سامانهٔ گشت شبانه کار می‌کند — لطفاً دست‌کاری نشود</div>
        <div class="code">{html.escape(payload)}</div>
      </div>""")

    html_doc = f"""<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<title>پلاک‌های گشت — {html.escape(a.building)}</title>
<style>
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{ font-family: Tahoma, "Vazirmatn", sans-serif; background: #fff; padding: 24px; }}
  h1 {{ font-size: 20px; margin-bottom: 6px; }}
  .sub {{ color: #666; font-size: 13px; margin-bottom: 20px; }}
  .grid {{ display: flex; flex-wrap: wrap; gap: 18px; }}
  .plaque {{ width: 320px; border: 3px solid #1a5276; border-radius: 14px;
            padding: 16px; text-align: center; page-break-inside: avoid; }}
  .bname {{ font-size: 15px; font-weight: bold; color: #1a5276; }}
  .qr svg {{ width: 210px; height: 210px; margin: 10px 0; }}
  .sname {{ font-size: 22px; font-weight: bold; }}
  .note {{ font-size: 11px; color: #888; margin: 8px 0 4px; }}
  .code {{ font-size: 10px; color: #bbb; direction: ltr; font-family: monospace; }}
  @media print {{ body {{ padding: 0; }} .plaque {{ border-width: 2px; }} }}
</style>
</head>
<body>
  <h1>پلاک‌های ایستگاه گشت — {html.escape(a.building)}</h1>
  <div class="sub">هر پلاک را برش بدهید، روی مقوا/پلاستیک سفت بچسبانید و در محل ایستگاه نصب کنید. کلید امضا: <span style="direction:ltr;font-family:monospace">{html.escape(key[:8])}…</span></div>
  <div class="grid">{''.join(cards)}
  </div>
</body>
</html>"""

    with open(a.out, "w", encoding="utf-8") as f:
        f.write(html_doc)
    print(f"OK → {a.out}  ({len(stations)} پلاک)")

    # ---------- خروجی PDF برای چاپ مستقیم ----------
    if a.pdf:
        from PIL import Image, ImageDraw, ImageFont
        import qrcode as _q

        W, H = 1240, 1754  # A4 در ۱۵۰dpi
        slot = H // 2
        pages = []

        def font(sz):
            try:
                return ImageFont.load_default(size=sz)
            except TypeError:
                return ImageFont.load_default()

        for start in range(0, len(stations), 2):
            page = Image.new("RGB", (W, H), "white")
            d = ImageDraw.Draw(page)
            for j in range(2):
                idx = start + j
                if idx >= len(stations):
                    break
                sid, name = stations[idx]
                payload = plaque_payload(key, sid)
                qr = _q.make(payload, box_size=10, border=2,
                             error_correction=qrcode.constants.ERROR_CORRECT_M).convert("RGB")
                qr = qr.resize((560, 560))
                cy = j * slot
                d.rectangle([90, cy + 26, W - 90, cy + slot - 26], outline=(26, 82, 118), width=6)
                label = a.building if a.building.isascii() else "NIGHT PATROL"
                d.text((W // 2, cy + 44), label, fill=(26, 82, 118), font=font(34), anchor="mm")
                page.paste(qr, ((W - 560) // 2, cy + 80))
                d.text((W // 2, cy + 676), f"STATION {sid}", fill=(17, 17, 17), font=font(46), anchor="mm")
                d.text((W // 2, cy + 712), payload, fill=(187, 187, 187), font=font(16), anchor="mm")
            pages.append(page)

        pages[0].save(a.pdf, save_all=True, append_images=pages[1:], resolution=150.0)
        print(f"OK → {a.pdf}  (PDF چاپ مستقیم، {len(pages)} برگه)")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""คัดลอกไฟล์เว็บจากรากโปรเจกต์ลง www/ แล้วปรับให้เหมาะกับ APK

- แทน Google Fonts ด้วยฟอนต์ woff2 ที่ฝังไว้ใน www/fonts/ (เปิดออฟไลน์ได้จริง)
- ปิด service worker (ไฟล์อยู่ในแอปอยู่แล้ว ไม่ต้องแคชซ้ำ)
"""
import io, os, shutil

ROOT = os.path.dirname(os.path.abspath(__file__))
WWW = os.path.join(ROOT, 'www')
os.makedirs(WWW, exist_ok=True)

for f in ('index.html', 'manifest.webmanifest', 'stamp.png', 'icon-192.png', 'icon-512.png'):
    shutil.copy2(os.path.join(ROOT, f), os.path.join(WWW, f))

THAI_RANGE = "U+02D7, U+0303, U+0331, U+0E01-0E5B, U+200C-200D, U+25CC"
LATIN_RANGE = ("U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, "
               "U+02DC, U+2000-206F, U+20AC, U+2122, U+2212, U+FEFF, U+FFFD")

css = "<style>\n"
for w in (400, 500, 600, 700):
    for sub, rng in (('thai', THAI_RANGE), ('latin', LATIN_RANGE)):
        css += ("@font-face{font-family:'Noto Sans Thai';font-style:normal;font-weight:%d;"
                "font-display:swap;src:url(fonts/%s-%d.woff2) format('woff2');"
                "unicode-range:%s;}\n" % (w, sub, w, rng))
css += "</style>"

p = os.path.join(WWW, 'index.html')
s = io.open(p, encoding='utf-8').read()

lines = s.split('\n')
hit = [i for i, l in enumerate(lines) if 'fonts.googleapis.com/css2' in l]
if not hit:
    raise SystemExit('หา <link> ของ Google Fonts ใน index.html ไม่เจอ — sync-www.py ต้องแก้')
i = hit[0]
lines[i] = css
for j in (i - 1, i - 2):                 # ลบ <link rel=preconnect> สองบรรทัดข้างบน
    if 'preconnect' in lines[j]:
        lines[j] = ''
s = '\n'.join(lines)

old = "if('serviceWorker' in navigator && location.protocol.startsWith('http')){"
if old not in s:
    raise SystemExit('หาโค้ดลงทะเบียน service worker ไม่เจอ — sync-www.py ต้องแก้')
s = s.replace(old, "if(false){  /* ปิด SW ในเวอร์ชัน APK */", 1)

io.open(p, 'w', encoding='utf-8').write(s)
print('sync-www: เรียบร้อย')

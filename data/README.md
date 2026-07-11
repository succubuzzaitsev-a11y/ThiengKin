# Data outputs (gitignored — regenerable from scripts/)

โฟลเดอร์นี้เก็บ output JSON จาก `scripts/setup-chiangmai.mjs` และ `scripts/filter-data.mjs`

## ไฟล์ที่จะถูกสร้าง

| ไฟล์ | สร้างโดย | คำอธิบาย |
|------|---------|----------|
| `chiangmai-restaurants.json` | `node setup-chiangmai.mjs` | Raw data จาก Foursquare (max 200 ร้าน) |
| `chiangmai-restaurants-filtered.json` | `node filter-data.mjs` | หลัง filter rating ≥ 4.0 + มี review |
| `chiangmai-restaurants-final.json` | `node merge-data.mjs` | Foursquare + manual seed (final import) |

## วิธีใช้

```powershell
cd D:\thiengKin\scripts
$env:FOURSQUARE_API_KEY="your_key"
node setup-chiangmai.mjs       # → ../data/chiangmai-restaurants.json
node filter-data.mjs           # → ../data/chiangmai-restaurants-filtered.json
node merge-data.mjs            # → ../data/chiangmai-restaurants-final.json
```

## ทำไม gitignore?

- **Reproducible:** รัน script ใหม่ได้ทุกเมื่อ
- **ขนาด:** JSON 200 ร้าน ~ 200-500 KB — ไม่อยาก track
- **Sensitive:** บางทีมี contact info (tel, website) — ไม่ควร leak

ถ้าต้องการ track เวอร์ชันใดเวอร์ชันหนึ่ง (เช่น snapshot ก่อน refresh) → ใช้ git tag

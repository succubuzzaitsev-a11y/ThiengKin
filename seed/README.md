# Manual curated seed data (committed to git)

ร้านที่ Foursquare ไม่มี หรือมีแต่ข้อมูลไม่ครบ — เราเขียนเอง

## ไฟล์

| ไฟล์ | คำอธิบาย |
|------|----------|
| `chiangmai-manual.json` | ร้านดังเชียงใหม่ 30-50 ร้าน (manual curation) |
| `highway-stops.json` | ร้านริมทางหลวง กรุงเทพ-เชียงใหม่ (Travel Mode) |

## Schema (เหมือน chiangmai-restaurants.json)

```json
{
  "id": "manual_001",
  "name": "ร้านลุงโภชนา",
  "name_th": "ร้านลุงโภชนา",
  "category": "ก๋วยเตี๋ยว",
  "category_slug": "noodle",
  "lat": 18.7883,
  "lng": 98.9853,
  "address": "...",
  "district": "Mueang Chiang Mai",
  "province": "Chiang Mai",
  "tel": "...",
  "website": null,
  "rating": 4.7,
  "review_count": 280,
  "price": 2,
  "tags": ["local_favorite", "morning", "noodle"],
  "source": "manual",
  "fetched_at": "2026-07-11T..."
}
```

## หมายเหตุ

- `id` ใช้ prefix `manual_` เพื่อไม่ชนกับ Foursquare `fsq_xxx`
- `tags` ใช้สำหรับ badge ใน UI เช่น `local_favorite`, `morning`, `highway_stop`, `souvenir`
- `source: "manual"` เพื่อ track ว่าร้านนี้มาจากไหน

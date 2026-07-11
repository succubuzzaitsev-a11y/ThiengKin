# เที่ยงกิน — Best-of-Breed

> วิเคราะห์จุดเด่นของแอปที่มีอยู่ → เลือกของดีมารวม

---

## 🎯 กลยุทธ์

> **ไม่ต้องสู้ Wongnai/Google ตรงๆ** — เลือก "จุดเด่น" ของแต่ละเจ้ามารวมไว้ในที่เดียว

**สูตร:** เที่ยงกิน = Wongnai (categories) + Google (AI) + Kinraidee (random) + ของเราเอง (rural) = **จุดเด่นครบ ไม่มีจุดอ่อนของใครเลย**

---

## 🥇 Wongnai (เบอร์ 1 ไทย)

**สถิติ:** 1.4M ร้าน · 5.7M รีวิว · 84M รูป · 25M users (2026)

| Feature | ยืม? | Phase | วิธีทำ |
|---------|------|-------|--------|
| Categories ละเอียดแบบ local | ✅ | 1.5 | `category_slug` ใน DB: ก๋วยเตี๋ยว, ส้มตำ, ข้าวซอย, น้ำตก, ปิ้งย่าง |
| "555 อันดับ" → User Votes | ✅ | 1.5 | ปุ่ม "อร่อยจริง" เพิ่ม weight ใน AI ranking |
| "แนะนำสำหรับคุณ" | ✅ | 2 | AI เรียนรู้จาก history → weight categories ที่ชอบ |
| "Explore Hot and New" | ⚠️ | 2 | "ร้านเปิดใหม่" + "ร้านกำลังฮิต" filter |
| โฆษณา | ❌ | - | ไม่มี ads ในแอป |
| Booking/Deals | ❌ | - | Deep link ไป Hungry Hub แทน |
| Delivery | ❌ | - | คนละ business model |
| Member rewards | ❌ | - | ต้องทีมใหญ่ |

---

## 🤖 Google Maps + Gemini AI (Nov 2025)

**Features ใหม่:** Ask Maps, AI Review Summary, Contextual Attributes, Personalization

| Feature | ยืม? | Phase | วิธีทำ |
|---------|------|-------|--------|
| AI Review Summary | ✅ | 2 | เรียก Gemini Flash สรุป 5-10 reviews → 1 บรรทัด |
| Contextual Attributes | ✅ | 1.5 | Filter tags: WiFi, ที่จอด, Pet-friendly, เปิดดึก, รับบัตร |
| "Ask Maps" chat | ✅ | 2 | Gemini Flash chat: "อยากกินอะไรดี" |
| Personalization | ✅ | 2 | เรียนรู้จาก save/favorite patterns |
| "Inspiration curated" | ❌ | - | เฉพาะเจาะจงเกินไป ไม่เหมาะกับ use case |
| Live wait times | ❌ | - | ข้อมูลไม่มีในไทย |
| Track ทุก search | ❌ | - | Privacy issue |

---

## 🍜 Kinraidee (กินไรดี) + Jaew Pa Gin

**Kinraidee:** Random menu + calorie counter
**Jaew Pa Gin:** Editor curated lists

| Feature | ยืม? | Phase | วิธีทำ |
|---------|------|-------|--------|
| "วันนี้กินอะไรดี?" random button | ✅ | 1 | ปุ่มบนหน้า Home → random ร้านที่ filter ผ่าน |
| Quick presets | ✅ | 1 | 1 tap ได้ร้านตาม mood (Jaew Pa Gin style) |
| Calorie counter | ⚠️ | 3 | Optional — แสดงแคลถ้ามีข้อมูลเมนู |
| Random menu เฉยๆ | ❌ | - | ไม่มี GPS/distance = ไร้ประโยชน์ |
| Editor curated | ⚠️ | 2 | "คัดสรรโดย [user]" badge — community-driven |

---

## 📅 Hungry Hub + OpenRice

**Hungry Hub:** Booking + buffet deals (2,500 partners, 1.2M users, ฿4B GMV)
**OpenRice:** Price info + social features (HK/TW focused)

| Feature | ยืม? | Phase | วิธีทำ |
|---------|------|-------|--------|
| Multi-language | ✅ | 3 | Thai-first + English toggle (Hungry Hub 15 langs) |
| Price range filter | ✅ | 1.5 | `priceLevel` 1-4 → filter UI: $ / $$ / $$$ / $$$$ |
| Deep link ไป booking | ✅ | 3 | ปุ่ม "จองโต๊ะ" → open Hungry Hub app |
| Booking เอง | ❌ | - | ทำยาก, scale ไม่ได้ |
| Deals/Discount | ❌ | - | ต้อง partnership |
| Buffet focus | ❌ | - | เฉพาะกลุ่ม |

---

## 🌍 Dine AI + Place Review Analyzer + Umamii

**Dine AI:** LLM analyzes Google Maps reviews
**Review Analyzer:** Chrome extension
**Umamii:** Hyper-personalized AI (Vancouver startup)

| Feature | ยืม? | Phase | วิธีทำ |
|---------|------|-------|--------|
| Aspect scores | ✅ | 2 | รสชาติ/บริการ/ความคุ้ม/บรรยากาศ (Gemini Flash extract) |
| "เมนูแนะนำ" | ⚠️ | 2 | ถ้ามี review text เยอะ → Gemini extract เมนูเด่น |
| Social discovery | ❌ | - | ต้อง community ใหญ่ |
| Personalized dish | ❌ | - | ต้อง user data เยอะ |

---

## 🇹🇭 เฉพาะ "เที่ยงกิน" — ต่างจังหวัด

**นี่คือ USP ที่ไม่มีใครเหมือน:**

| Feature | Phase | Description |
|---------|-------|-------------|
| 🏘️ "คนท้องถิ่นแนะนำ" | 1.5 | Badge สำหรับร้านที่ user ในจังหวัด vote |
| 🌅 "เปิดเช้าตี 4-5" | 1.5 | ตลาดเช้า ก๋วยเตี๋ยวเช้า (เฉพาะ ตจว.) |
| 🚛 "ร้านริมทาง/ริมปั๊ม" | 1 | สำหรับคนขับรถทางไกล |
| 🎁 "ของฝากประจำจังหวัด" | 1.5 | น้ำพริก แหนม หมูยอ ของเฉพาะถิ่น |
| 🗺️ "เส้นทางทัวร์อาหาร" | 2 | เชียงใหม์ 1 วัน / ภูเก็ต 1 วัน |
| ✈️ Travel mode | 1 | "กำลังไปเชียงใหม์" preload ทั้งจังหวัด offline |
| 📡 Highway POI | 2 | Longdo Map API (ดีกว่า Google สำหรับทางด่วนไทย) |
| 🅿️ "ที่จอดรถ" | 1.5 | filter สำคัญสำหรับคนขับรถ |

---

## 📊 Comparison Matrix

| Feature | Wongnai | Google | Kinraidee | Hungry Hub | **เที่ยงกิน** |
|---------|---------|--------|-----------|------------|---------------|
| Categories ไทย | ✅ | ⚠️ | ⚠️ | ⚠️ | ✅ (ละเอียดกว่า) |
| Rating + Review | ✅ | ✅ | ❌ | ⚠️ | ✅ (เรตจาก Google) |
| AI Summary | ❌ | ✅ (US) | ❌ | ❌ | ✅ (ไทย-first) |
| Quick filters | ⚠️ | ⚠️ | ✅ | ⚠️ | ✅ (rural-focused) |
| Random | ❌ | ❌ | ✅ | ❌ | ✅ |
| Route-based | ❌ | ⚠️ | ❌ | ❌ | ✅ (killer) |
| Offline | ❌ | ❌ | ❌ | ❌ | ✅ |
| โฆษณา | ✅ | ❌ | ❌ | ✅ | ❌ |
| Booking | ⚠️ | ⚠️ | ❌ | ✅ | ⚠️ (deep link) |
| Delivery | ✅ | ❌ | ❌ | ✅ | ❌ |
| ต่างจังหวัด | ⚠️ | ⚠️ | ❌ | ⚠️ | ✅ (เน้น) |
| "ต้องเจอ" guarantee | ❌ | ❌ | ❌ | ❌ | ✅ (North Star) |
| ฟรี 100% | ⚠️ | ⚠️ | ✅ | ⚠️ | ✅ |

---

## 🎓 เรียนรู้จากแต่ละแอป

### จาก Wongnai
- **Database ร้านค้า = moat** — มี 1.4M ร้าน ทำให้ user ติด
- เราไม่มี → ใช้ Google Places (Phase 2) + manual curation (Phase 1.5)

### จาก Google Maps
- **AI summary = UX win** — คนไม่อ่าน 280 reviews แต่อ่าน 1 บรรทัด
- เราทำได้ด้วย Gemini Flash (free tier)

### จาก Kinraidee
- **Random = "หมดปัญหากินอะไรดี"** — pain point จริง
- เราทำได้ฟรี (on-device)

### จาก Hungry Hub
- **Deep link ไป booking apps** — ไม่ต้องทำเอง ใช้ ecosystem
- เราจะทำ deep link ไป Hungry Hub ใน Phase 3

### จาก Dine AI / Umamii
- **Personalization = future** — แต่ต้อง community ใหญ่
- เราเก็บไว้ทีหลัง เน้น local-first ก่อน

---

## 🏆 สรุปจุดเด่นที่ "เที่ยงกิน" มีคนเดียว

1. **Travel Mode (route-based)** — ไม่มีใครทำในไทย
2. **"เปิดแอปต้องเจอ" guarantee** — North Star ที่ชัด
3. **Offline-first corridor** — เน็ตหลุดก็ใช้ได้
4. **Rural-focused (ต่างจังหวัด)** — Wongnai เน้นกรุงเทพ
5. **ฟรี 100% (1-2 users)** — ไม่โฆษณา ไม่ track
6. **Best-of-breed UI** — เอาของดีจากทุกเจ้ามารวม

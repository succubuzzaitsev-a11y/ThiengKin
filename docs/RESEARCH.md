# เที่ยงกิน — Research Notes

> Market research · Competitor analysis · Reality checks

---

## 🔍 "100% Free" Reality Check

คำถาม: "Google Maps data ฟรี 100% ได้ไหม?"

**คำตอบสั้นๆ: ไม่ได้** — แต่ใกล้เคียงได้

| Option | ฟรี 100%? | Cost ใน 1-2 users |
|--------|----------|------------------|
| **Google Places API** | ❌ ต้องใส่บัตร | $0 (1-2 users ใช้ไม่ถึง $200 free/mo) |
| **Foursquare** | ✅ | $0 (100K/mo free) |
| **Overpass (OSM)** | ✅ | $0 (แต่ไม่มี review) |
| **Self-host scraper** | ✅ แต่ผิด ToS | โดนแบน IP ภายใน 1-3 ชม. |
| **SerpAPI (3rd party)** | ⚠️ | $0 แรก 100/mo, $75/mo ต่อ |

**ทางที่เราเลือก:** Foursquare (Phase 1) + Google Places via Edge Function (Phase 2)

---

## 🏪 Competitor Landscape (ไทย)

### 🥇 Wongnai (เบอร์ 1 ไทย)

| | |
|---|---|
| **ฐานข้อมูล** | 1.4 ล้านร้าน · 5.7 ล้านรีวิว · 84 ล้านรูป |
| **Users** | 25 ล้าน+ (2026) |
| **Business model** | โฆษณา + Booking + Delivery (ผูก LINE MAN) |
| **จุดแข็ง** | Data หนาแน่น · community ใหญ่ · แบรนด์แข็ง |
| **จุดอ่อน** | โฆษณาเยอะ · ไม่มี Travel Mode · ต่างจังหวัดบาง |
| **เราทำอะไรได้** | ไม่ต้องแข่ง — ยืม categories + 555 ranking pattern |

### 🤖 Google Maps (Nov 2025 — Gemini)

| | |
|---|---|
| **Features ใหม่** | Ask Maps, AI Review Summary, Contextual Attributes, Personalization |
| **Coverage** | 300M places · 500M contributors |
| **Business model** | Ad-free (Google), track ทุก search |
| **จุดแข็ง** | Data ครบที่สุด · AI ทรงพลัง · UX ดี |
| **จุดอ่อน** | Track · ไม่ specialized สำหรับ "หาร้าน" · ต้องกดหลายที |
| **เราทำอะไรได้** | ยืม AI Summary + Attributes · ไม่ต้อง build data เอง |

### 🛵 LINE MAN Wongnai (Delivery)

| | |
|---|---|
| **Focus** | Food delivery |
| **Market** | 77 จังหวัด · 500,000+ ร้าน |
| **Business model** | Commission + Ads |
| **ความต่างจากเรา** | Delivery vs Discovery — คนละเรื่องกัน |

### 🦖 Hungry Hub (Booking)

| | |
|---|---|
| **Partners** | 2,500 ร้าน (เน้น buffet) |
| **Users** | 1.2 ล้าน |
| **Revenue** | 90M+ THB (2024) → 110-120M (2025) |
| **จุดเด่น** | Buffet deals · 50% off |
| **ความต่างจากเรา** | Booking vs Discovery — เรา deep link ไปหากันได้ |

### 🦘 Foodpanda, GrabFood, ShopeeFood

| | |
|---|---|
| **Focus** | Delivery (intensive) |
| **Market** | 77 จังหวัด |
| **ความต่างจากเรา** | Delivery — ไม่ทับซ้อน |

### 🇨🇳 OpenRice (HK/TW)

| | |
|---|---|
| **Coverage ไทย** | น้อย |
| **จุดเด่น** | Price + Social features |
| **ความต่างจากเรา** | จีน/ฮ่องกง focused |

### 🌏 แอปเล็กที่น่าสนใจ

| แอป | จุดเด่น | เรายืม? |
|------|---------|---------|
| **Kinraidee (กินไรดี)** | Random + Calorie | ✅ Random button |
| **ผู้แนะนำอาหาร (myfoodrecommender)** | 11 ภาษา + Google Maps | ⚠️ Multi-lang Phase 3 |
| **Corner (curate & share)** | No ads + AI | ✅ No ads |
| **Umamii** (US/CA) | Hyper-personalized | ❌ ต้อง community ใหญ่ |
| **Dine AI** | LLM review analysis | ✅ AI Summary |
| **Jaew Pa Gin** | Editor curated | ⚠️ Community-driven |
| **iPick** | Pan-Asia | ❌ ต่างประเทศ |
| **NOSTRA Map** | Thai map detail | ⚠️ ใช้ map data ดี |

---

## 🌍 International Reference

### OpenTable (US)
- Restaurant booking leader
- $200B+ market
- เราไม่ทำ booking

### Yelp (US)
- Reviews + business
- เราไม่ทำ reviews platform

### TheFork (EU)
- Booking + Discovery
- TripAdvisor-like

### Tablelog (Japan)
- Restaurant reviews
- **⭐ Similar to เที่ยงกิน ในแง่ rating-focused**
- Local-first approach
- ขายดีมากในญี่ปุ่น

---

## 📊 Market Size (ประมาณการ)

### Thailand Food Discovery Market

| Segment | Size (2026) | เราเข้าถึง |
|---------|------------|-----------|
| **คนทำงานกรุงเทพ** | ~5M | Near-me mode |
| **นักท่องเที่ยวไทย+เทศ** | ~30M/ปี | Travel mode |
| **คน ตจว.** | ~50M | Rural + Travel mode |
| **Delivery** | ~$2B | ไม่ใช่ตลาดเรา |

**TAM:** ~$50M+ food discovery market (rough estimate)

**Phase 1 target:** 1-2 users (personal) — no monetization
**Phase 2 target:** 100-1,000 users (early adopter) — validate PMF
**Phase 3:** Decide monetization strategy

---

## 🛣️ "ต่างจังหวัด" Market Insight

### นักท่องเที่ยวไทย (2024-2025)

- **100M+ คน-ครั้ง/ปี** เดินทางท่องเที่ยวในไทย
- **70%** ไป ตจว. (ไม่ใช่กรุงเทพ)
- **80%** ใช้รถยนต์ส่วนตัว
- **Pain point:** "ถึงเมืองนี้แล้วกินอะไรดี?"

### คนท้องถิ่น

- **~50M คน** อยู่ ตจว.
- ต้องการ "ร้านดังที่คนท้องถิ่นรู้" ไม่ใช่แค่ chain restaurants
- Wongnai coverage ตจว. ~30% → gap ใหญ่

### ตลาดที่ Wongnai/Google ไม่ตอบโจทย์

- "ร้านริมทาง" — Google ไม่มี (ต้อง Yelp-like)
- "ร้านเปิดดึก" — Google มีแต่ต้อง filter เอง
- "ของฝาก" — ไม่มี category นี้
- "คนท้องถิ่นแนะนำ" — Google AI ไม่ personalized สำหรับ ตจว.

**= โอกาสของเรา**

---

## 🔍 คำค้นหาที่เกี่ยวข้อง (จาก web research)

| Query | Key findings |
|-------|--------------|
| "เที่ยงกิน" | ❌ ไม่มีแอปชื่อนี้ (ว่าง!) |
| "AI คัดร้านอร่อย" | มี Kinraidee, iPick, Jaew Pa Gin, OpenRice |
| "แอปหาร้านอร่อย GPS" | Wongnai (เบอร์ 1), Google Maps, Kinraidee |
| "AI restaurant recommendation" | Google Maps + Gemini (2025), Dine AI, Umamii |

---

## 💡 Strategic Insights

### 1. "ต้องเจอ" เป็น Killer Feature

ผู้ใช้ทุกคนเคยเจอ "ไม่เจอร้านใกล้ฉัน" — เป็น UX failure
เราการันตีว่า "เปิดแอปต้องเจอ" → North Star ที่ทุกคนเข้าใจ

### 2. Travel Mode = Real Pain

คนขับรถทางไกล ไม่มีแอปไหนตอบโจทย์ "ถึงเมืองนี้แล้วกินอะไร"
Google Maps ต้องกดหลายที Wongnai ไม่มี route-based
= โอกาสที่ชัดเจน

### 3. ต่างจังหวัด = Underserved

Wongnai เน้นกรุงเทพ Google เน้นทั่วโลก
ไม่มีใคร specialized สำหรับ "ร้านอร่อยต่างจังหวัดที่คนท้องถิ่นรู้จัก"
= Blue ocean

### 4. Personal Use First

Phase 1 = 1-2 users (คุณ + เพื่อน)
ไม่ต้อง scale ไม่ต้อง monetize
แค่ "ตัดสินใจเร็วขึ้น" = success

### 5. UI ตัด "AI" ออก = Smart Positioning

คนไทย 70% ยังไม่คุ้น "AI"
Apple/Google เองเลิกใช้
เราเน้น "Smart" + "สรุป" → เข้าถึงง่ายกว่า

---

## 📌 Key Takeaways

1. ✅ **ชื่อ "เที่ยงกิน" ว่าง** — ใช้ได้
2. ✅ **Travel Mode = Killer** — ไม่มีใครทำ
3. ✅ **ต่างจังหวัด = Blue ocean** — Wongnai ไม่ได้ focus
4. ✅ **Foursquare free tier** — 100K/mo เพียงพอสำหรับ 1-2 users
5. ✅ **Local-first** — เร็ว, ฟรี, offline
6. ✅ **Personal use first** — ไม่ต้อง scale
7. ⚠️ **API key security** — เก็บใน Edge Function เมื่อถึง Phase 2
8. ⚠️ **Foursquare coverage ตจว. บาง** — ต้อง manual curation

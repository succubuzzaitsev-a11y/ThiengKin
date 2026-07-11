# เที่ยงกิน (ThiengKin)

> **"เปิดแอปก็เจอร้าน"** — เพื่อนร่วมทางสายกิน
> แอป Android หาร้านอร่อยระหว่างทาง + near-me mode
> ธีม: 🔴 แดง + 🟡 เหลือง (จากโลโก้ Thieng Tham Development)

---

## 📣 Tagline (เลือกใช้)

| Version | ใช้ตอนไหน |
|---------|----------|
| **"เปิดแอปก็เจอร้าน"** ← แนะนำ | One-liner, App Store, splash screen |
| "กินดี ตลอดทาง" | Marketing, social media |
| "เพื่อนร่วมทางสายกิน" | Tagline ยาว, branding |

---

## 🎯 จุดเด่น 5 ข้อ

| # | จุดเด่น | ทำไม |
|---|--------|------|
| 1 | 🚗 **Travel Mode** (Killer) | ใส่จุดหมาย → ได้ 5-10 จุดแวะ + ETA + detour (ไม่มีใครทำในไทย) |
| 2 | ✅ **"ต้องเจอ" guarantee** | ไม่มี empty state — ขยายรัศมีอัตโนมัติ |
| 3 | 🏘️ **ต่างจังหวัด First** | "คนท้องถิ่นแนะนำ" + ร้านริมทาง + ของฝาก (Wongnai เน้นกรุงเทพ) |
| 4 | 🎨 **Best-of-Breed UI** | เอาของดีจากทุกแอป + ธีมแดง-เหลือง |
| 5 | 💸 **ฟรี 100% ไม่ track** | ไม่โฆษณา ไม่ track (1-2 users) |

---

## 📁 โครงสร้างโปรเจกต์

```
D:\thiengKin\
├── README.md                      ← คุณอยู่ที่นี่
├── docs/
│   ├── WORKFLOW.md                ← Workflow & Architecture (v3)
│   ├── MOCKUP.md                  ← Pro UI Mockup v2
│   ├── BEST-OF-BREED.md           ← วิเคราะห์ competitors
│   ├── ARCHITECTURE.md            ← Architecture decisions
│   ├── CHANGELOG.md               ← Version history
│   └── RESEARCH.md                ← งานวิจัย + competitor research
├── scripts/
│   ├── setup-chiangmai.mjs        ← ดึงร้านจาก Foursquare
│   ├── filter-data.mjs            ← filter + cleanup (to do)
│   └── package.json
└── android/                       ← Android project (to do)
    └── README.md
```

---

## 🎯 Quick Facts

| Item | Value |
|------|-------|
| **ชื่อแอป** | เที่ยงกิน (ThiengKin) |
| **Component name** | `ThiengKin` (Kotlin) |
| **Platform** | Android (Kotlin + Compose) |
| **Pilot** | เชียงใหม่ (Old City + รอบๆ 25 km) |
| **Backend** | Local-first (Phase 1) + Supabase Edge Function (Phase 2) |
| **Data** | Foursquare (Phase 1) + Google Places via Edge Function (Phase 2) |
| **Cost** | $0 ตลอดกาล (1-2 users) |
| **Brand** | Thieng Tham Development (บริษัทแม่) |
| **Tagline** | "เปิดแอปก็เจอร้าน" (one-liner) |
| **Theme** | 🔴 แดง `#DC2626` + 🟡 เหลือง `#FACC15` (จากโลโก้) |
| **North Star** | "เปิดแอปต้องเจอร้าน" — ไม่มี empty state |

---

## 🚀 Quick Start

### Phase 1 — Data Layer (เชียงใหม่)

```bash
# 1. ติดตั้ง
cd D:\thiengKin\scripts
npm install

# 2. ตั้ง Foursquare API key (สมัครฟรีที่ foursquare.com/products/places-api/)
$env:FOURSQUARE_API_KEY="your_key_here"

# 3. ดึงข้อมูล
node setup-chiangmai.mjs

# 4. Refresh รายสัปดาห์
node setup-chiangmai.mjs --refresh
```

### Phase 1 — Android App (Week 2+)

- [ ] Android project skeleton (Gradle + Compose + Room)
- [ ] GPS permission + Province picker
- [ ] Travel Mode UI (big buttons, dark)
- [ ] Near-me Mode UI (light, filter เต็ม)
- [ ] AI ranking formula 40/30/15/10/5
- [ ] Deep link Google Maps

### Phase 2 — Google Places (Week 6+)

- [ ] Supabase Edge Function (API key ใน Edge)
- [ ] เพิ่มจังหวัดที่ 2
- [ ] AI Review Summary (Gemini Flash)

---

## 📚 เอกสาร

อ่านตามลำดับนี้:

1. **[docs/WORKFLOW.md](docs/WORKFLOW.md)** — workflow & architecture ทั้งหมด
2. **[docs/MOCKUP.md](docs/MOCKUP.md)** — UI mockup (6 screens + design system)
3. **[docs/BEST-OF-BREED.md](docs/BEST-OF-BREED.md)** — วิเคราะห์ Wongnai/Google/Kinraidee + สิ่งที่ยืมมา
4. **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — Tech decisions + data flow
5. **[docs/CHANGELOG.md](docs/CHANGELOG.md)** — Version history (v1, v2, v3)
6. **[docs/RESEARCH.md](docs/RESEARCH.md)** — Market research + competitors

---

## ✅ ที่ล็อกแน่นอนแล้ว

- ✅ ชื่อ "เที่ยงกิน" — ว่าง (ไม่มีแอปชื่อนี้)
- ✅ Pilot = เชียงใหม่ (Old City center)
- ✅ 2 โหมด: Travel (หลัก, dark) + Near-me (light)
- ✅ AI ranking formula: 40/30/15/10/5
- ✅ Data flow: Foursquare (Phase 1) + Google Places via Edge Function (Phase 2)
- ✅ UI ตัด "AI" ออก — ใช้ "สรุปรีวิว (Ai)" แทน
- ✅ Cost: $0 ตลอดกาล (1-2 users)

## ❓ ที่รอตัดสินใจ

- จังหวัดที่ 2 (เชียงราย? ภูเก็ต? บ้านเกิด?)
- เริ่ม Phase 2 (Google Places) เมื่อไหร่

---

Built with ❤️ · 2026

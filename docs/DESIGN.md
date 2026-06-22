# Personal Expense Tracker — Technical Design Document (v2.0)

> Supersedes v1.0. Refined after deciding: **SMS auto-detection is the priority**,
> **Native Kotlin + Jetpack Compose**, and **local-first** (no cloud, no OTP).

---

## 1. Objective

An Android app that **automatically reads transaction SMS** (bank, card, UPI),
parses them into structured transactions, **auto-categorizes** spending
(grocery, travel, commute, food, education, etc.), and shows daily / weekly /
monthly expense dashboards and reports. Manual entry is supported as a fallback.
All data stays **on-device and encrypted**.

### Non-goals (v1)
- iOS support (Apple blocks SMS inbox access — not possible).
- Cloud sync / multi-device (deferred to v2; optional encrypted backup only).
- Login accounts / OTP (replaced by device biometric/PIN lock).

---

## 2. Core principle: local-first & private

- Raw SMS **never leave the device**.
- Database encrypted at rest (SQLCipher).
- App locked behind **biometric / device PIN** (AndroidX BiometricPrompt).
- No network permission required for core features → genuinely ₹0/month.
- A clear in-app privacy statement (also needed if ever published).

---

## 3. Architecture overview

```
┌──────────────────────────────────────────────────────────┐
│                       UI (Compose)                         │
│  Dashboard │ Transactions │ Reports │ Categories │ Settings│
└───────────────▲───────────────────────────▲───────────────┘
                │ StateFlow / ViewModel       │
┌───────────────┴─────────────────────────────────────────┐
│                     Domain layer                          │
│  Use cases: ImportSms, ParseSms, Categorize, Dedup,       │
│             Aggregate(period), Export                     │
└───────────────▲───────────────────────────▲──────────────┘
                │                             │
┌───────────────┴──────────┐   ┌──────────────┴─────────────┐
│  SMS subsystem            │   │  Data (Room + SQLCipher)   │
│  - Inbox reader           │   │  transactions, categories, │
│  - BroadcastReceiver      │   │  merchant_rules, accounts  │
│  - Parser (regex templates)│  └────────────────────────────┘
│  - Categorizer            │
└───────────────────────────┘
```

**Pattern:** MVVM + Repository, single-activity Compose, Kotlin Coroutines/Flow.
Suggested DI: Hilt.

---

## 4. SMS subsystem (the core feature)

### 4.1 Capture
- **Permission:** `READ_SMS` (and `RECEIVE_SMS` for live capture). Requested at
  onboarding with a clear rationale screen *before* the system dialog.
- **Initial import:** on first run, read existing inbox via `ContentResolver`
  (`content://sms/inbox`), batch-process historically.
- **Live capture:** `BroadcastReceiver` on `SMS_RECEIVED_ACTION`; enqueue new
  messages to a WorkManager job for parsing (keeps receiver light).
- **Sender filter:** only process messages from financial sender IDs.
  Pattern examples (India): `*-HDFCBK`, `*-ICICIB`, `*-SBIINB`, `*-AXISBK`,
  `*-KOTAKB`, `*-PYTM`, `*-GPAY`, UPI handles, card issuers. Maintained as an
  editable list so users can add their bank.

### 4.2 Parser (rules-based)
Bank SMS are semi-structured. Maintain **per-bank/per-format regex templates**.
Each template extracts:

| Field | Example |
|-------|---------|
| amount | 450.00 |
| direction | debit / credit |
| merchant / payee | SWIGGY |
| account / card tail | XX1234 |
| balance (optional) | 12,300 |
| datetime | 12-Jun-25 |
| reference no | 5512 |

**Worked example**
> "Rs.450.00 debited from a/c XX1234 on 12-Jun-25 to SWIGGY via UPI. Ref 5512. Avl Bal Rs.12,300"

→ `{ amount: 450.0, type: DEBIT, merchant: "SWIGGY", acct: "1234",
     datetime: 2025-06-12, ref: "5512", category: "Food" }`

- Target ~10–15 templates for major banks/UPI + a generic heuristic fallback
  (find `Rs.|INR` + number + debit/credit keywords).
- Unparseable financial SMS go to an **"Needs review"** queue, not dropped.

### 4.3 Categorization
- **Seed rules:** keyword → category lookup table:
  - SWIGGY, ZOMATO, restaurant → **Food**
  - UBER, OLA, METRO, fuel/petrol → **Commute**
  - IRCTC, MAKEMYTRIP, indigo, hotel → **Travel**
  - BIGBASKET, DMART, grocery, kirana → **Grocery**
  - AMAZON, FLIPKART, MYNTRA → **Shopping**
  - electricity/BSES/recharge/DTH → **Bills**
  - school, college, fees, udemy → **Education**
  - else → **Uncategorized**
- **User overrides:** re-categorizing a merchant writes a `merchant_rules` row so
  future SMS from that merchant auto-apply the chosen category. This is the
  cheap "learning" — no ML needed for v1.

### 4.4 De-duplication
A purchase can trigger 2 SMS (bank + UPI app). Dedup rule:
**same amount + same account tail + within 60s window** → keep one, link the other.
Manual entries are never auto-merged (user-owned), but flagged if a matching SMS
arrives.

---

## 5. Data model (Room)

```
transactions
  id (PK), amount, type(DEBIT|CREDIT), category_id (FK),
  merchant, account_tail, datetime, source(SMS|MANUAL),
  raw_sms_id (nullable), payment_mode, note, is_reviewed, dedup_group

categories
  id (PK), name, icon, color, is_default

merchant_rules
  id (PK), merchant_pattern, category_id (FK), user_overridden

accounts
  id (PK), masked_number, bank, type(BANK|CARD|WALLET)

sms_senders   -- editable allow-list
  id (PK), pattern, enabled
```

---

## 6. Features

### 6.1 Dashboard
Total income, total expense, savings, category breakdown (pie), quick actions.
Period selector: **Today / This Week / This Month / Custom**.
Define explicitly: week starts Monday, month = calendar month, device timezone.

### 6.2 Transactions
List + search/filter by date, category, amount, payment mode, source.
Tap to edit, re-categorize, split, or mark as transfer (excluded from spend).

### 6.3 Reports
Monthly report, pie (by category), bar (by day/week), spend trend line.
Library: **Vico** (Compose-native) or MPAndroidChart.

### 6.4 Export
- **CSV/Excel** of transactions (Apache POI or simple CSV writer).
- **PDF** monthly report (Android PdfDocument).
- Shared via Android share sheet; no upload.

### 6.5 Income & manual entry
Manual add/edit for income (salary, freelance, rent, dividends) and any expense
SMS missed.

---

## 7. Security

- DB encryption: **SQLCipher** (key in Android Keystore).
- App lock: **BiometricPrompt** with PIN fallback; lock on background after N min.
- No `INTERNET` permission in v1 manifest → strong privacy guarantee.
- In-app privacy explainer at onboarding.

---

## 8. Tech stack

| Concern | Choice |
|---------|--------|
| Language / UI | Kotlin + Jetpack Compose (single activity) |
| Architecture | MVVM + Repository, Coroutines/Flow |
| DI | Hilt |
| Local DB | Room + SQLCipher |
| Background | WorkManager (parse queue), BroadcastReceiver (capture) |
| Charts | Vico (or MPAndroidChart) |
| Auth | AndroidX Biometric |
| Build | Gradle (Kotlin DSL); release APK via `assembleRelease` |
| Min SDK | 26 (Android 8) suggested; Target latest |

---

## 9. Permissions & Play Store note

- Required: `READ_SMS`, `RECEIVE_SMS`, `USE_BIOMETRIC`.
- For **personal use / sideloaded APK**: no restrictions — install directly.
- For **Play Store publish**: `READ_SMS` is restricted; needs a Permissions
  Declaration justifying SMS as core function. Approval is **not guaranteed** for
  finance apps — plan a fallback (e.g., user-paste / notification-listener path).

---

## 10. Cost

**₹0/month.** Fully on-device, no cloud, no OTP, no paid APIs.

---

## 11. Build milestones

1. **Project scaffold** — Compose app, navigation, Room + SQLCipher, biometric lock.
2. **SMS capture** — permissions flow, inbox import, live receiver, sender filter.
3. **Parser** — 3–4 major bank templates + fallback + "needs review" queue.
4. **Categorizer** — seed rules + user overrides.
5. **Dashboard** — period selector + totals + pie.
6. **Transactions** — list/search/edit/re-categorize.
7. **Reports + export** — charts, CSV, PDF.
8. **Manual entry + income.**
9. **Polish** — dedup, settings, edge cases.

---

## 12. Key risks

| Risk | Mitigation |
|------|------------|
| SMS formats vary wildly across banks | Template + fallback + review queue; easy to add templates |
| Play Store `READ_SMS` rejection | Target sideload first; design a non-SMS fallback path |
| Duplicate SMS double-counting | Dedup rule (amount+acct+time window) |
| Sensitive data leak | On-device only, encrypted, no INTERNET permission |
| Parsing wrong amounts | Always editable; "needs review" before trusting |

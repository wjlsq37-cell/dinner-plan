# PWA Design QA

## Evidence

- Source visual truth:
  - `C:\Users\admin\AppData\Local\Temp\codex-clipboard-596f5f69-20b0-4a82-9d52-81f86055900d.png`（首页）
  - `C:\Users\admin\AppData\Local\Temp\codex-clipboard-09fb171a-74e5-4a03-86fb-a795b002e646.png`（附近）
  - `C:\Users\admin\AppData\Local\Temp\codex-clipboard-9ecb9b34-bd28-4809-883a-be3cac2e50a3.png`（收藏）
  - `C:\Users\admin\AppData\Local\Temp\codex-clipboard-fa507265-d2d4-45ee-827c-c612d1d4ec48.png`（设置）
- Browser-rendered implementation: `pwa-app/test-results/visual-qa-capture-reference-states-desktop-chrome/*.png`
- Viewport: 390 × 844, device scale factor 1.
- State: clean IndexedDB defaults; nearby page uses deterministic mocked restaurant data matching the reference density.

## Comparison

### Full-view evidence

- Home: header, decision card, two action cards, inspiration empty state and fixed navigation align with the reference composition and mobile density.
- Nearby: location/search card, three sorting pills, mint status tile, compact restaurant cards and bottom navigation align with the reference hierarchy.
- Saved: title row, three filter tabs and 120px compact saved cards align with the reference rhythm.
- Settings: developer entry, preference cards, selected chips, search-radius card, switches and bottom navigation align with the reference.

### Focused-region evidence

- Typography: PingFang-compatible system stack, 30px home title, 22px screen titles, 24px decision headline and 12–16px supporting text reproduce the Android hierarchy without clipping.
- Spacing/layout: 18px page gutter, 8px card radius, fine warm border, 10–14px vertical gaps and compact fixed navigation match the source. No horizontal overflow at 390px.
- Colors/tokens: canvas `#FFFBF4`, primary `#C8372B`, location `#176C49`, surface white and warm/mint selected states match Android theme tokens.
- Images/assets: API-provided restaurant and recipe images use cover cropping; missing remote images fall back to library icons without broken-image frames. Reference-specific merchant photos remain data-dependent.
- Copy: primary titles, subtitles, tabs, actions, empty states, preference labels and navigation match the Android source and supplied screenshots.
- Interaction states: tabs, chips, switches, location editing, loading, error, empty and saved states were rendered and checked. Browser console produced no application errors during capture.

## Comparison History

1. First implementation pass:
   - P2: nearby header and restaurant cards were vertically too compact.
   - P2: saved items reused search-result actions and were taller than the Android cards.
2. Fixes:
   - Increased nearby header/search rhythm and restaurant-card spacing.
   - Added the dedicated compact saved-card treatment and preserved independent heart actions.
3. Post-fix evidence:
   - Second 390 × 844 capture shows matching page hierarchy, card proportions and navigation placement.
   - No actionable P0, P1 or P2 mismatch remains.

## Primary Interactions Tested

- Bottom navigation and browser history.
- Cook mode/source controls and successful-query restoration.
- Preference select, deselect and refresh persistence.
- Manual location fallback and secure geolocation success.
- Saved filters and independent unsave actions.
- Reduced-motion CSS behavior.

## Follow-up Polish

- P3: restaurant and recipe thumbnails vary with upstream API image availability, so exact source-photo crops cannot be deterministic offline.
- P3: Android system status/navigation bars are outside the browser viewport and therefore are not reproduced by web content.

final result: passed

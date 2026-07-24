# Android Launch, Icon and Settings Refinement — Design QA

final result: passed

## Latest cold-start and compact-control refinement

- Source finding: `C:/Users/admin/AppData/Local/Temp/codex-clipboard-f908e5d6-1e97-44f2-94d4-b4e2ffd41e9f.png`
- Emulator evidence:
  - `app/build/outputs/dinner_plan_launch_native_sheet.png`
  - `app/build/outputs/dinner_plan_home_final.png`
  - `app/build/outputs/dinner_plan_settings_theme_icon.png`
- Android 12+ system launch no longer shows the selected circular launcher icon on white. The mandatory system layer uses the launch artwork's sampled peach background and a transparent system icon, then hands off to a lightweight native full-screen image before Compose initializes.
- The supplied full-screen artwork begins its 1.8-second minimum hold from its first drawn frame; normal foreground returns and Activity recreation do not replay a completed process launch.
- The decision action is now 62% of the available card width, capped at 220dp, with 42dp minimum height and tighter content padding. Both labels fit on one line without covering the heading.
- The theme row now uses the same 20dp icon inside the same circular soft-primary surface as the other settings entries.
- Focused `:app:assembleDebug` passed; no extended test matrix was run.

## Current comparison target

- Source visual truth:
  - `C:/Users/admin/Downloads/ChatGPT Image 2026年7月24日 14_16_15.png`
  - `C:/Users/admin/Downloads/ChatGPT Image 2026年7月24日 14_31_12.png`
  - `C:/Users/admin/Documents/xwechat_files/wxid_gix9y68u0fbz22_e0d0/temp/RWTemp/2026-07/9e20f478899dc29eb19741386f9343c8/d95abdf784b1ea581dba8e84186e4961.png`
- Emulator evidence:
  - `build/splash_probe_4.png`
  - `build/chidian_after.png`
  - `build/chidian_settings.png`
  - `build/chidian_icon_selected.png`
  - `build/chidian_launcher.png`
- Combined comparison: `build/launch-settings-qa/comparison.png`

## Current result

- The supplied launch artwork fills the Android viewport with centered crop behavior; no text or decorative element was recreated in code.
- The icon chooser follows the reference hierarchy: centered title, back action, two large icon cards and explicit selected/unselected indicators.
- The new launcher artwork remains legible under the emulator's circular adaptive mask and switches without killing the active app.
- The main settings page now exposes compact “口味与偏好”, “搜索设置”, theme and icon rows; the Girl Pink bear remains the last scroll item.
- New secondary settings pages omit the bottom navigation and preserve the existing editable preference controls.
- The decision button is centered at the bottom of the unchanged 180dp card, with a narrower responsive width and no text clipping.
- No P0, P1 or P2 visual issue remains in the requested states.

## Current verification

- `:app:testDebugUnitTest` passed.
- `:app:assembleDebug` passed.
- Cold-start artwork, icon selection, launcher alias state and the two theme decision-card layout were checked on the connected Android emulator.

## Comparison target

- Source visual truth:
  - `C:/Users/admin/AppData/Local/Temp/codex-clipboard-ae166bd8-d798-4325-b9ca-98d3a8262f47.png`
  - `C:/Users/admin/AppData/Local/Temp/codex-clipboard-3a72d05d-1b60-4154-99a6-7666259dbe0e.png`
- Implementation:
  - `build/theme-qa/home-no-subcopy-final.png`
  - `build/theme-qa/settings-theme-popup-final.png`
  - `build/theme-qa/saved-footer-final.png`
- Combined comparison evidence: `build/theme-qa/refinement-comparison.png`
- State: Girl Pink theme; home idle; settings scrolled to the footer with the theme popup open; saved list populated and scrolled to its footer.

## Viewport and normalization

- Android emulator capture: 1080 × 2400 px at density 420, approximately 411dp wide.
- Reference phone content crop: 389 × 866 px.
- For the combined board, implementation captures were downsampled to 389 × 866 px and placed beside the matching reference phone-content crop.
- Desktop emulator chrome and screenshot arrows were excluded from fidelity judgments.

## Full-view comparison

- The decision-card supporting sentence identified by the first annotation is removed. The heading and primary action now occupy the card without clipping or an empty text band.
- The settings theme row remains compact. Its two options appear in a separate anchored popup rather than expanding the settings card.
- The settings bear appears after the theme row, making it the final visual element in the scroll content.
- The Girl Pink saved screen ends with a centered strawberry bunny illustration and does not alter saved-card behavior.

## Required fidelity surfaces

- Fonts and typography: existing Android type scale, weights, line heights, and labels remain unchanged; removing the decision subcopy preserves the intended title hierarchy.
- Spacing and layout rhythm: the decision card was reduced to 180dp after removing one text row; theme popup and both footer illustrations retain clear spacing above the persistent navigation.
- Colors and visual tokens: all new surfaces reuse the existing Girl Pink palette and Material theme colors.
- Image quality and asset fidelity: existing local transparent PNG illustrations are reused at their native aspect ratios with `ContentScale.Fit`; no generated placeholders or code-drawn assets were introduced.
- Copy and content: only the annotated decision supporting sentence was removed. Theme names remain “默认主题” and “少女粉”.

## Focused evidence

- The combined board keeps the decision card and settings footer readable at matched phone-content scale, so a separate crop was unnecessary.
- `build/theme-qa/saved-footer-final.png` provides the additional saved-screen footer state not present in the source screenshots.

## Comparison history

- Initial user findings: decision-card small copy remained visible; settings bear preceded the theme row; theme options expanded inline; saved screen lacked a footer illustration.
- Fixes: removed the supporting copy, placed the bear after the theme row, replaced the inline expansion with `DropdownMenu`, and added a Girl Pink saved footer illustration.
- Post-fix evidence: `build/theme-qa/refinement-comparison.png` and `build/theme-qa/saved-footer-final.png`.
- No actionable P0, P1, or P2 issues remain in the requested states.

## Verification

- `:app:testDebugUnitTest` passed.
- `:app:assembleDebug` passed.
- Theme selection still applies immediately and the popup dismisses after selection.

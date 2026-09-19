## Issues Fixed

### ISSUE 1 — Remove Dola.ai branding, replace with PK AI
- Renamed DolaAiAssistant.kt to PkAiAssistant.kt
- Replaced all user-facing Dola.ai text with PK AI in Super Chat
- Renamed drawables from bg_dola_badge_* to bg_pkai_badge_*
- Updated system prompts and persona to respond as PK AI
- Updated test file PkAiAssistantTest.kt accordingly

### ISSUE 2 — Fix IllegalArgumentException in ImageLoadHelper
- Crash: View.setTag(int key, Object value) was called with a plain int constant (0x0101_0001) instead of a proper application resource ID
- Added item name="tag_image_load_job" type="id" to res/values/ids.xml
- Updated ImageLoadHelper.kt to use R.id.tag_image_load_job for all setTag() / getTag() calls
- This preserves the duplicate-load prevention logic for recycled RecyclerView items while fixing the crash

## Verification
- Build and Sign APK and AAB: success
- Verify AI Provider APIs: success

No new secrets or API keys were added.

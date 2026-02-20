# G1 Companion Project Memory

## Project
- Name: G1 Companion
- Path: C:\Users\hendr\Documents\G1 companion
- Target: Samsung Galaxy Z Flip 7 + Even Realities G1 glasses
- Stack: Kotlin + Jetpack Compose, Hilt DI, DataStore, OkHttp WebSocket

## Key facts (from EvenDemoApp source)
- BLE Service UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E (Nordic UART)
- TX char (app writes): 6E400002-…
- RX char (glasses notify): 6E400003-…
- Left arm name contains "_L_", right arm contains "_R_"
- SEND ORDER: left first, then right (strict)
- Handshake bytes on connect: [0xF4, 0x01]
- ACK success: 0xC9, failure: 0xCA
- AI text command: 0x4E (seq, total_pkg, current_pkg, newscreen, pos_h, pos_l, curr_page, max_page, data)
- Display: 488 px wide, 21 pt font, 5 lines/page, split 3+2 across two packets
- MTU: 251 bytes, max payload: 191 bytes
- Heartbeat: 0x25 every 8 seconds
- Mic command 0x0E targets RIGHT arm only
- Max 10 retries per write

## Reddit grounding
- Lens out-of-sync fix: resend current page (not full restart)
- HUD cooldown ~10-20s after double-tap: app uses 2s debounce before sending

## Architecture
- GlassesRepository orchestrates scan → connect → display
- DualBleConnection holds two BleArmConnection instances
- TextPager handles 488px wrapping and packet building
- OpenAiRealtimeClient: WebSocket to wss://api.openai.com/v1/realtime
- MainViewModel is single shared ViewModel
- Navigation: Onboarding → Dashboard → (Resync, Developer)
- Developer mode: unlocked by 5x tap on version label

## CI/CD
- ci.yml: builds debug APK on every push/PR, uploads artifact
- release.yml: triggered by tag v*, signs with env var keystore, creates GitHub Release
- Signing env vars: EVENAI_KEYSTORE_BASE64, EVENAI_KEYSTORE_PASSWORD, EVENAI_KEY_ALIAS, EVENAI_KEY_PASSWORD
- versionCode = CI run number via -PversionCode, versionName from tag via -PversionName

# Source-Grounded Decisions

## From even-realities/EvenDemoApp (GitHub)

| Fact | How it is used |
|------|---------------|
| Nordic UART Service UUID `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | Hardcoded as `G1Protocol.SERVICE_UUID` |
| TX characteristic `6E400002-…` (app writes to glasses) | `G1Protocol.CHAR_TX_UUID` |
| RX characteristic `6E400003-…` (app reads from glasses) | `G1Protocol.CHAR_RX_UUID` |
| Device name suffix `_L_` = left arm, `_R_` = right arm | `GlassesScanner.isLeftDevice()` and `isRightDevice()` |
| Dual independent BLE connections (`leftGatt`, `rightGatt`) | `DualBleConnection` holds two `BleArmConnection` instances |
| Send to **left first**, wait for response, then send to **right** | `DualBleConnection.sendBoth()` sequential coroutine chain |
| Success byte `0xC9`, failure byte `0xCA` in response | `G1Protocol.ACK_SUCCESS` / `ACK_FAILURE` |
| Handshake init bytes `[0xF4, 0x01]` | Sent in `BleArmConnection.onServicesDiscovered()` |
| `0x4E` command byte for AI text result | `G1Protocol.CMD_AI_RESULT` |
| Packet fields: seq, total_pkg, current_pkg, newscreen, pos_h, pos_l, curr_page, max_page, data | `G1Protocol.buildTextPacket()` mirrors this layout exactly |
| `newscreen` upper nibble: `0x30` = displaying, `0x40` = complete, `0x50` = manual | `DisplayMode` enum maps these values |
| 488 px display width, 21 pt font, 5 lines per screen | `TextPager.PAGE_WIDTH_PX`, `FONT_SIZE_PT`, `LINES_PER_PAGE` |
| Page split: first 3 lines one packet, last 2 lines second packet | `TextPager.buildPackets()` splits at index 3 |
| MTU 251 bytes, data payload ≤ 191 bytes per packet | `G1Protocol.MAX_PAYLOAD` = 191 |
| Heartbeat command `0x25`, 8-second interval | `HeartbeatManager` sends every 8 s |
| Microphone command `0x0E`, targets right arm only | `GlassesRepository.startMic()` sends to right arm |
| LC3 audio format, sequence byte `0xF1` | `AudioStreamHandler` tags each packet with `0xF1` + seq |
| Max recording 30 seconds | `MicUseCase` enforces 30 s timeout |
| Max 10 retries per command | `BleArmConnection.writeWithRetry()` loops up to 10 times |
| Touchbar single tap `0xF5 0x01`, double tap `0xF5 0x00` | `G1Protocol.CMD_TAP_SINGLE`, `CMD_TAP_DOUBLE` |

## From Reddit field report (Daves_Archives, r/EvenRealities)

| Observation | Design decision |
|-------------|----------------|
| "Setup screens were out of sync on the lenses. Restarting the setup resolved the issue." | `ResyncScreen` detects when left/right displays are mismatched and offers a one-tap resync that re-sends the current page to both arms |
| "This glitch happened again later when scrolling through my notes" | `TextPager` tracks displayed page independently for left and right arms; on mismatch it re-sends both |
| "HUD will stay off for around 10-20 seconds after double-click" | After widget switch, the app waits 2 s before sending new content to avoid sending during the HUD cooldown |
| "HUD doesn't activate immediately after using some other features" | Widget switch includes a 2 s debounce before transmitting |
| "AI delivers answers in 3-6 seconds" | OpenAI streaming starts displaying the first token as soon as it arrives, so perceived latency is under 2 s |
| "Battery only lost 6% over 6 hours" | App avoids unnecessary BLE writes; heartbeat is the only idle traffic |
| Calendar sync "took a bit longer" possibly due to phone settings | App does not rely on calendar; features are self-contained |

## Galaxy Z Flip 7 considerations

- Targets API 35 (Android 15) with `minSdk 31` for Bluetooth permissions introduced in Android 12
- `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are declared as runtime permissions (Android 12+)
- Foldable-aware: UI uses `WindowSizeClass` to adapt layout on the outer cover display vs inner display

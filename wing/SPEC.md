# WING 通訊層 - 技術規格書

**版本**：V1.0001  
**建立日期**：2026-05-12  
**負責人**：DMT Subagent (WING)

---

## 1. 目標

建立 WING Android 通訊層，功能：
- LAN 自動搜尋 Mixer
- wapi TCP 連線管理
- 讀取所有頻道 default data
- 用自然文字顯示狀態

---

## 2. 技術選型

| 項目 | 技術 |
|------|------|
| **平台** | Android（Java）|
| **wapi 方案** | C source port to Java (自研 binary protocol) |
| **通訊層** | TCP socket (port 2222) |
| **Min SDK** | Android API 24 (7.0) |
| **架構** | Clean Architecture + MVVM |

---

## 3. 功能範圍

### 3.1 LAN Discovery
- 發送 UDP broadcast "WING?" 到 port 2222
- 解析 "WING," 回覆取得：device info（name, model, IP, firmware, serial）

### 3.2 wapi TCP 連線（主要方案）
- TCP port 2222
- Binary protocol 封裝/解碼
- Protocol format: `[0xdf][channel][len_le][command][payload][0xdf]`
- wapi functions：
  - `wOpen()` / `wClose()` — 連線管理
  - `wGetFloat()` / `wSetFloat()` — float 參數讀寫
  - `wGetInt()` / `wSetInt()` — int 參數讀寫
  - `wSubscribe()` / `wRenew()` — 參數訂閱

### 3.3 Token System
- 32-bit token enum，命名如 `CH_1_MIX_FADER`
- Helper methods: `chMixFader(ch)`, `chMixOn(ch)`, `chPan(ch)`, `chEqGain(ch,band)`, `chHaGain(ch)`
- 支援 channels (1-48), buses (1-16), main, aux

### 3.4 讀取頻道資料（初始化 Query）
- 掃描所有相關頻道
- 讀取參數：
  - fader 值（0.0-1.0，+12dB 為满刻度）
  - mute 狀態
  - pan 值（-1.0 到 +1.0）
  - preamp gain（dB）
  - EQ band gains（dB）

### 3.5 自然文字顯示
- 將 binary/data 轉換成可讀格式
- Example: "CH01 Fader: 75.0% (-3.2 dB), Mute: OFF, Pan: L40/R40"

---

## 4. 輸出格式

```
wing/
├── app/                          # 測試 App
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/.../app/
│       │   └── MainActivity.java
│       └── res/layout/
│           └── activity_main.xml
├── lib/                          # wapi 通訊層 library
│   ├── build.gradle
│   └── src/main/java/com/mixer/wing/
│       ├── wapi/
│       │   ├── WApi.java                # High-level API wrapper
│       │   ├── WApiConnection.java       # TCP socket + wOpen/wClose
│       │   ├── WApiBinaryEncoder.java   # binary request encoder
│       │   ├── WApiBinaryDecoder.java   # binary response decoder
│       │   ├── WApiTokens.java          # token enum (V1.0001)
│       │   └── WApiException.java       # error handling
│       ├── discovery/
│       │   └── WingDiscovery.java       # LAN device discovery
│       └── util/
│           └── WingStatusReader.java    # status formatting
├── build.gradle
├── settings.gradle
├── gradle.properties
└── SPEC.md
```

---

## 5. 限制

- Binary protocol 格式基於有限文件reverse-engineered，需要實際設備驗證
- Token 結構為合理猜測，需要比對 actual WING firmware 修正
- OSC fallback 未實作（目前專注 wapi）
- 只做通訊層，UI 在 Step 3 討論

---

## 6. 版本格式

```
V#.@@@@
# = 大版號（人為提出修改才改變）
@ = 4碼小版號（每次修改自動增加）

V1.0001 - 初始實作
  - wapi binary protocol encoder/decoder
  - WApiTokens enum + helper methods
  - WApiConnection TCP socket manager
  - WApi high-level wrapper
  - WingDiscovery LAN discovery
  - WingStatusReader natural text formatter
  - Android test app skeleton
```
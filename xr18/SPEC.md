# XR18 通訊層 - 技術規格書

**版本**：V1.0000  
**建立日期**：2026-05-12  
**負責人**：DMT Subagent (XR18)

---

## 1. 目標

建立 XR18 Android 通訊層，功能：
- LAN 自動搜尋 Mixer（廣播 discovery）
- OSC 連線管理
- 讀取所有頻道 default data
- 用自然文字顯示狀態

---

## 2. 技術選型

| 項目 | 技術 |
|------|------|
| **平台** | Android（Kotlin）|
| **OSC Library** | `com.illposed.osc:osc-java` |
| **Min SDK** | Android API 24 (7.0) |
| **架構** | Clean Architecture + MVVM |

---

## 3. 功能範圍

### 3.1 LAN Discovery（自動搜尋）
- 發送 UDP broadcast 到 255.255.255.255:10024
- 解析 `/xinfo` 回覆取得：device name, model, IP, firmware version
- 顯示找到的 Mixer 清單

### 3.2 OSC 連線管理
- OSC Client：發送到 XR18 port 10023
- OSC Server：監聽本地 port 接收推播
- `/xremote` 訂閱：每 8 秒發一次保持連線

### 3.3 讀取頻道資料（初始化 Query）
- 掃描 16 個頻道
- 讀取參數：
  - `/ch/xx/mix/fader` — fader 值
  - `/ch/xx/mix/on` — mute 狀態
  - `/ch/xx/mix/pan` — pan 值
  - `/headamp/xx/gain` — preamp gain
  - `/ch/xx/eq/on` — EQ on/off
  - `/ch/xx/eq/1/g` 到 `/eq/4/g` — EQ band gain

### 3.4 自然文字顯示
- 將 OSC 資料轉換成可讀格式
- Example: `/ch/02/mix/fader = 0.606` → "CH02 Fader: 60.6% (-8.2 dB)"

---

## 4. 輸出格式

- Android Module：可編譯的 Gradle module
- Source code 位置：`~/.openclaw/workspace/code-projects/mixer-ctrl/xr18/`
- 包含：`app/`（測試 App）、`lib/`（通訊層 library）

---

## 5. 限制

- 只做通訊層，不做 UI
- UI 在 Step 3 討論
- 專注於 OSC 協定解析

---

## 6. 版本格式

```
V#.@@@@
# = 大版號（人為提出修改才改變）
@ = 4碼小版號（每次修改自動增加）
```
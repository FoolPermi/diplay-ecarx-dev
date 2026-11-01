# DiPlay · 領克 01 CarPlay

[简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [English](README.md#english)

DiPlay 讓你在領克車機上使用 iPhone 的導航、音樂、Siri 和通話。適配**億咖通車機 753 版本**（領克高通 820A 平台），目前於 **2021 款領克 01** 驗證；同平台的 753 車機理論上通用。

每個功能的開啟位置、前提條件、如何確認生效，見 [功能與使用說明](docs/FEATURES.zh-CN.md)（簡體中文）。

| 功能 | 需要開啟的設定 |
| --- | --- |
| 儀表和 HUD 導航提示 | **設定 → 導航 → 億咖通導航**，開啟「儀表導航」 |
| 車道指引 | 同一頁面開啟「儀表車道指引」 |
| 歌曲資訊和封面 | **設定 → 音訊 → 車機媒體整合**，選擇「DiPlay 直接接管」或「HY 代理」，歌曲資訊隨所選後端輸出 |
| 日夜模式跟隨車輛 | **設定 → 顯示 → 顯示方式與效能**，把「CarPlay 日夜模式」設為「跟隨車輛」 |
| 音樂走 CarPlay、保留藍牙電話 | **設定 → 音訊 → 藍牙音訊**，開啟「CarPlay 期間停用藍牙媒體」，關閉「透過車輛藍牙播放 CarPlay 音訊」 |
| 無線連接 | 在連接設定中選擇車機熱點、Wi-Fi Direct 或既有 Wi-Fi，依照提示完成設定 |
| 開機自動連接 | **設定 → 連接 → 自動連接**，開啟「車機啟動後開啟」和「開啟 DiPlay 時連線」 |
| 方向盤語音鍵叫 Siri | **設定 → 車輛 → 方向盤按鍵**，開啟「方向盤喚醒 Siri」；直接接管模式選短按／長按，HY 代理模式選 155/156 或 312/303 |

**文中「HY」指 HYWebview（`com.hyphp.hywebview`），適配版本 3.6.1。** DiPlay 直接接管模式下，請關閉 HY 的媒體接管及同一語音鍵的方控接管；HY 代理模式請在兩邊選擇相同的 Siri 模擬按鍵。

**方向盤 Siri**：在「設定 → 車輛 → 方向盤按鍵」用總開關開啟或關閉。DiPlay 直接接管時選擇短按／長按；HY 代理時選擇 155/156 或 312/303，並在 HYWebview 的方向盤設定裡配置相同模擬按鍵。原有 Siri 橋接入口繼續可用，不受按鍵總開關影響。不提供按鍵學習；未連接 CarPlay 或通話中不接管。

只顯示導航提示和歌曲資訊時，保持「整屏鏡像到儀表」關閉。修改設定後，依照畫面提示重新連接。

目前 USB 連接仍有問題；拔掉 USB 後會自動回到無線。遇到異常，請到 **設定 → 診斷 → 儲存診斷報告**。

基於 [DiPlay](https://github.com/shihabal3amri/DiPlay)、[DiPlay-ECARX](https://github.com/jiopia/DiPlay-ECARX) 和 [xcertplay](https://github.com/shilapi/xcertplay)，部分介面改編自 [DiAuto](https://github.com/shihabal3amri/DiAuto)。許可與第三方聲明見 [LICENSE](LICENSE) 及 [相關說明](docs/THIRD_PARTY_NOTICES.md)。

CarPlay 及相關標識屬於 Apple；本專案與 Apple、領克、億咖通無隸屬或背書關係。

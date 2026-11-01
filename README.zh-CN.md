# DiPlay · 领克 01 CarPlay

[简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [English](README.md#english)

DiPlay 让你在领克车机上使用 iPhone 的导航、音乐、Siri 和通话。适配**亿咖通车机 753 版本**（领克高通 820A 平台），当前在 **2021 款领克 01** 上验证；同平台的 753 车机理论上通用。

每个功能的开启位置、前提条件、怎么确认生效，见 [功能与使用说明](docs/FEATURES.zh-CN.md)。

| 功能 | 需要开启的设置 |
| --- | --- |
| 仪表和 HUD 导航提示 | **设置 → 导航 → 亿咖通导航**，开启「仪表导航」 |
| 车道指引 | 同一页面开启「仪表车道指引」 |
| 歌曲信息和封面 | 同一页面开启「仪表媒体卡片」 |
| 日夜模式跟随车辆 | **设置 → 显示 → 显示与性能**，把「CarPlay 日夜模式」设为「跟随车辆」 |
| 音乐走 CarPlay、保留蓝牙电话 | **设置 → 音频 → 蓝牙音频**，开启「CarPlay 期间停用蓝牙媒体」，关闭「通过车辆蓝牙播放 CarPlay 音频」 |
| 无线连接 | 在连接设置中选择车机热点、Wi-Fi Direct 或已有 Wi-Fi，按提示完成配置 |
| 开机自动连接 | **设置 → 连接 → 自动连接**，开启「车机启动后打开」和「打开 DiPlay 时连接」 |
| 方向盘语音键叫 Siri | 无需设置：选择开启方向盘唤醒 Siri，按当前媒体模式配置短长按或 HY 虚拟键 |

**DiPlay 直接接管模式下，请关闭 HY 的媒体代理及同一语音键的方控接管；HY 代理模式请在两边选择相同的 Siri 模拟按键。**

**方向盘 Siri**：在「设置 → 车辆 → 方向盘按键」用总开关开启或关闭。DiPlay 直接接管时选择短按／长按；HY 代理时选择 155/156 或 312/303，并在 HY 中配置相同模拟按键。原有 Siri 桥接入口继续可用，不受按键总开关影响。不提供按键学习；未连接 CarPlay 或通话中不接管。

只显示导航提示和歌曲信息时，保持「整屏镜像到仪表」关闭。设置修改后，按界面提示重新连接。

目前 USB 连接仍有问题；拔掉 USB 后会自动回到无线。遇到异常，请到 **设置 → 诊断 → 保存诊断报告**。

基于 [DiPlay](https://github.com/shihabal3amri/DiPlay)、[DiPlay-ECARX](https://github.com/jiopia/DiPlay-ECARX) 和 [xcertplay](https://github.com/shilapi/xcertplay)，部分界面改编自 [DiAuto](https://github.com/shihabal3amri/DiAuto)。许可与第三方声明见 [LICENSE](LICENSE) 和 [相关说明](docs/THIRD_PARTY_NOTICES.md)。

CarPlay 及相关标识属于 Apple；本项目与 Apple、领克、亿咖通无隶属或背书关系。

# 753 车机 ADB 依赖与平台权限清单

更新：2026-11-02（北京时间）。适用于 **2021 款领克 01、亿咖通 753、高通 820A、Android 9 / API 28**，以及本项目的平台签名安装包。

源码基线：`85dd3684bac089f23e69eda46b358c11b9de3623`。本次核对的最新本地产物为 **v113**，包名 `com.shihab.diplay`，版本名 `0.2-car-test-dev-85dd3684`。v113 的构建记录说明其唯一源码差异是将 `versionCode` 从 112 改为 113。

本文供后续权限与 ADB 改造参考。**“当前实现用了 ADB”“权限上可以替代”“已经实车验证替代成功”是三个不同结论。第 1～8 节以 v113 为分析基线；后续自启与热点源码调整见第 9 节，尚未纳入上述 APK，也没有确认车机当前安装的版本就是 v113。**

## 1. 判断原则

1. 以这台车机的平台签名包评估，优先使用已经授予的系统权限和车机接口，尽量降低或消除日常功能对 ADB 的要求。
2. 平台签名应用可声明并获得许多签名级权限，其中不少能力普通 ADB shell 不具备。不能把普通第三方应用的权限边界直接套在本项目上。
3. 签名本身不等于所有权限已经授予。必须区分 Manifest 是否声明、系统是否实际授予、调用是否成功，以及功能效果是否通过实车验收。
4. 平台签名应用仍有自己的 UID。历史实车记录中 DiPlay 的 UID 为 `10125`，不是 `system` 的 `1000` 或 `shell` 的 `2000`；明确校验 UID、SELinux 或文件访问规则的接口需要单独确认。
5. ADB 应作为必要时的异常兜底或可选诊断通道，不应成为权限已具备时的统一前置检查。

Android 对签名权限的基本规则：应用声明权限，且签名与权限声明方匹配时，可在安装阶段获得对应签名权限。参考 [Android 权限定义](https://developer.android.com/reference/android/R.attr#protectionLevel)及 [WRITE_SECURE_SETTINGS](https://developer.android.com/reference/android/Manifest.permission#WRITE_SECURE_SETTINGS)。

## 2. 已有实车权限证据

以下是历史实车回读，不能当作本轮对 v113 安装后权限的重新核验。

| 权限 | 已有证据 | 对本项目的意义 |
| --- | --- | --- |
| `RECEIVE_BOOT_COMPLETED` | v104 实车记录 `granted=true` | 可接收开机广播；是否实际送达还取决于固件和应用状态 |
| `WRITE_SETTINGS` | 同上 | 修改系统设置，支持当前热点控制路径 |
| `SYSTEM_ALERT_WINDOW` | 同上 | 悬浮窗显示无需重新借 ADB 授权 |
| `PACKAGE_USAGE_STATS` | 同上 | 已有使用情况访问权限；实际功能还须以应用的访问检查为准 |
| `NETWORK_SETTINGS`、`TETHER_PRIVILEGED`、`OVERRIDE_WIFI_CONFIG` | 同上 | 车机热点的系统调用已有权限基础 |
| `BLUETOOTH_PRIVILEGED` | 同上 | 支持直接调整蓝牙媒体策略 |
| `CHANGE_COMPONENT_ENABLED_STATE` | 同上；11 月 1 日另有实车确认停包权限可用 | 可直接控制原厂导航、HiCar 包的启用状态 |
| `MEDIA_CONTENT_CONTROL`、`OPENAPI_MEDIACENTER_PERMISSION` | v104 实车记录 `granted=true` | 系统媒体及 ECARX 媒体中心权限基础 |
| `DIM_MESSAGING`、`OPENAPI_NAVI_PERMISSION`、`CAR_VENDOR_EXTENSION`、`CAR_CONTROL_AUDIO_VOLUME` | 同上，表中省略命名空间 | 仪表、导航及相关车机接口权限基础 |
| `READ_LOGS` | 11 月 1 日实车记录明确回读 `granted=true`；当前 Manifest 已声明且限定至 API 28 | HY 后台日志监听无需持续连接 ADB；后台唤醒与识别效果仍待验收 |

车机权限注册表另确认：`WRITE_SECURE_SETTINGS` 为 `signature|privileged|development`，`CONNECTIVITY_INTERNAL`、`MANAGE_USB`、`CONTROL_VPN` 为 `signature|privileged`。这说明存在平台签名授予路径，**不说明当前包已经声明或获得这些权限**。

证据位置见第 8 节。

## 3. 当前已经可以不用 ADB 的功能

| 功能 | 当前实现与条件 | 代码依据 |
| --- | --- | --- |
| 开机打开 DiPlay | 保存自启设置；收到 `BOOT_COMPLETED` 后直接 `startActivity`，无 ADB 前置检查 | [BootReceiver](../common/src/main/java/com/shilapi/xcertplay/BootReceiver.kt)、[DiPlayActivity](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt) |
| 车机热点正常开启、关闭 | Android 9 直接调用系统热点接口；已有权限与固件支持是前提。通用启动路径仍保留 ADB 兜底 | [CarHotspotTethering](../shared/src/main/java/com/shilapi/xcertplay/network/CarHotspotTethering.kt)、[ManualHotspotControl](../shared/src/main/java/com/shilapi/xcertplay/network/ManualHotspotControl.kt) |
| 读取、修改车机热点配置 | Android 9 走应用内直接读写路径。Android 11+ 的 ADB 路径不应算成本车的必需条件 | [CarHotspotConfigRead](../shared/src/main/java/com/shilapi/xcertplay/network/CarHotspotConfigRead.kt)、[CarHotspotConfigWrite](../shared/src/main/java/com/shilapi/xcertplay/network/CarHotspotConfigWrite.kt) |
| 仪表地图、HUD、车道输出 | 使用车机 DIM／导航接口、Presentation 和 MQTT；当前无 ADB 调用 | [EcarxHudBridge](../shared/src/main/java/com/shilapi/xcertplay/hud/EcarxHudBridge.kt)、[EcarxClusterSession](../common/src/main/java/com/shilapi/xcertplay/EcarxClusterSession.kt)、[EcarxProjectionChannel](../shared/src/main/java/com/shilapi/xcertplay/hud/EcarxProjectionChannel.kt) |
| 原厂导航暂停、恢复及 HiCar 开关 | 使用组件管理权限直接操作 PackageManager | [EcarxFactoryNavigationRestore](../shared/src/main/java/com/shilapi/xcertplay/hud/EcarxFactoryNavigationRestore.kt)、[EcarxHicarControl](../shared/src/main/java/com/shilapi/xcertplay/hud/EcarxHicarControl.kt) |
| 媒体卡片、蓝牙媒体策略 | 直接调用 ECARX 媒体中心和蓝牙系统接口 | [EcarxMediaCenterTransport](../common/src/main/java/com/shilapi/xcertplay/mediacenter/EcarxMediaCenterTransport.kt)、[CarBluetoothProfileGuard](../shared/src/main/java/com/shilapi/xcertplay/bluetooth/CarBluetoothProfileGuard.kt) |
| HY 后台 Siri 日志监听 | 已获 `READ_LOGS` 时，由应用自身启动 logcat 监听；ADB 按钮仅在缺权限时尝试授权 | [HySiriInputLog](../common/src/main/java/com/shilapi/xcertplay/HySiriInputLog.kt)、[CarPlayMediaSession](../common/src/main/java/com/shilapi/xcertplay/CarPlayMediaSession.kt) |
| 悬浮窗与使用情况访问 | 已获批后的正常使用无需 ADB；当前“一键配置”入口仍有多余的 ADB 检查，见下一节 | [UsbPermissionSetup](../common/src/main/java/com/shilapi/xcertplay/UsbPermissionSetup.kt) |
| 正常 USB、VPN 系统授权 | 系统授权界面可用时走正常授权流程；缺界面时的 ADB 补救是另一条路径 | [IphoneUsbHost](../shared/src/main/java/com/shilapi/xcertplay/transport/IphoneUsbHost.kt)、[CarPlayHostActivity](../common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt) |
| 基本诊断报告导出 | 没有 ADB 仍可保存应用诊断，只缺少部分底层附加信息 | [DiPlayActivity](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt)、[AdbDiagnostics](../shared/src/main/java/com/shilapi/xcertplay/adb/AdbDiagnostics.kt) |

“当前不用 ADB”只描述调用路径，不代表所有仪表、音频或连接效果已完成实车验收。

## 4. 当前用了 ADB，但有直接替代路径

| 功能 | 当前代码怎样用 ADB | 无 ADB 的替代方向 | 当前状态 |
| --- | --- | --- | --- |
| USB／地图一键权限配置 | `UsbPermissionSetup.Operation.run()` 先连接 ADB，再跳过已经获批的权限 | 先检查权限；全部满足则直接成功，仅处理缺失项 | 可先消除多余 ADB 检查，无须新增权限；尚未改代码 |
| 开启 USB 自动确认无障碍服务 | shell 写 `enabled_accessibility_services`、`accessibility_enabled` | 声明并获得 `WRITE_SECURE_SETTINGS`，直接写 `Settings.Secure` | 当前未声明该权限；直接路径及实车授予待验证 |
| 开启、恢复方向盘无障碍服务 | `WheelKeyService.enableOverAdb()` 写安全设置，必要时移除后重新加入服务以恢复绑定 | 同上；保留其他服务、重新绑定间隔、取消条件及写后回读 | 当前未实现直接路径；不能只改首次启用而遗漏崩溃／更新后的恢复 |
| Wi-Fi Direct 暂停、恢复扫描 | 通过 shell helper 调用 `enableWifiConnectivityManager` 等接口 | 本车可评估声明 `CONNECTIVITY_INTERNAL`，由应用直接调用 Android 9 对应接口 | 当前未声明，仍依赖 ADB；直接调用及固件行为待验证 |
| USB 授权界面缺失时为 iPhone 授权 | `IphoneUsbAdbGrant` 启动 shell helper，调用 `grantDevicePermission` | 声明并获得 `MANAGE_USB`，在应用内直接调用 USB 管理接口 | 当前未声明；仅为缺界面的异常补救候选，不代表正常 USB 必须 ADB |
| VPN 授权界面缺失时补救 | `VpnPreConsent.grant()` 执行 `appops … ACTIVATE_VPN allow` | 评估声明 `CONTROL_VPN`，调用系统 VPN 包授权接口 | 当前未声明；本车接口契约和授权回读待验证 |
| 热点启动的通用 ADB 兜底 | 查询固件命令或运行 shell 热点 helper | 优先沿用本车已有系统热点接口；若发生失败，先核对调用与权限，不能直接认定必须 ADB | Android 9 已有直接路径；是否需要进一步改动取决于实车失败证据 |

对应源码：[UsbPermissionSetup](../common/src/main/java/com/shilapi/xcertplay/UsbPermissionSetup.kt)、[WheelKeyService](../common/src/main/java/com/shilapi/xcertplay/WheelKeyService.kt)、[WifiScanPause](../shared/src/main/java/com/shilapi/xcertplay/network/WifiScanPause.kt)、[WifiScanPauseMain](../shared/src/main/java/com/shilapi/xcertplay/network/WifiScanPauseMain.kt)、[IphoneUsbAdbGrant](../shared/src/main/java/com/shilapi/xcertplay/transport/IphoneUsbAdbGrant.kt)、[VpnPreConsent](../common/src/main/java/com/shilapi/xcertplay/VpnPreConsent.kt)。

注意：USB helper 中只允许 `shell/root` UID 的检查是本项目 helper 自己的边界，不能拿它证明车机 USB 服务只允许这两个 UID。评估应用内替代时，要查真正系统接口的权限检查，并保留设备、包名与目标 UID 的授权范围校验。

对于当前已经具备的悬浮窗、使用情况及日志权限，不应再强制通过 `appops` 或 `pm grant` 重复授权。也不应为消除少量 ADB 调用，预先申请一整套尚无实际用途的系统权限。

## 5. 哪些必须用 ADB

**目前尚未发现本车日常功能中，已经被证明必须依赖 ADB 且平台签名权限无法替代的项目。**

这不等于已经证明所有替代方案都能在实车工作。按当前原样代码运行，第 4 节部分入口仍会连接 ADB；减少这些依赖需要实现改动和验证。

以下项目单独处理，不纳入本车日常功能的必需条件：

| 项目 | 当前是否使用 ADB | 后续判断 |
| --- | --- | --- |
| 诊断中的 adbd 状态、5555 监听地址、部分 `/proc`、启动参数、设备节点信息 | 是，`AdbDiagnostics` 尝试通过 shell 读取 | 部分内容可能可直接读取；其余受文件及 SELinux 规则约束，尚未逐项验证。保留可选 ADB 诊断不会阻止日常功能脱离 ADB |
| Android 11+ 热点配置读写 | 当前代码使用 ADB | 不适用于本车 Android 9，不能据此要求本车常开 ADB |
| Android 13+“自动加入热点”修复 | v113 基线代码使用 ADB；后续源码已删除 | 改用系统热点接口、标准连接流程与车机设置，见第 9 节 |
| 厂商自启／后台限制 | 当前没有已接通的 ADB 自启白名单修复 | 是否有直接车机接口、是否确实需要额外配置尚未确认；不能仅凭旧文案认定 ADB 必需 |

当前本地 ADB 客户端连接 `127.0.0.1:5555`，使用应用自己的密钥，不支持 TLS 无线调试配对。ADB 可连接也不代表每个 shell 命令都获准执行。见 [LocalAdb](../shared/src/main/java/com/shilapi/xcertplay/adb/LocalAdb.kt)。

## 6. 开机自启的结论与版本差异

当前链路：

```text
开启“车辆启动后打开 DiPlay” → 保存设置
BOOT_COMPLETED → BootReceiver 读取设置 → startActivity 打开 DiPlay
应用打开后 → 按独立设置开启热点／自动连接 CarPlay
```

- 当前自启开关及 `BootReceiver` 均不检查 ADB，也不通过 shell 启动应用。
- 旧版在启用自动车机热点、模式为 `MANUAL` 时，用 `adbToggle` 包装自启开关；先连 ADB、授予 `SYSTEM_ALERT_WINDOW`，成功后才保存。`BOOT_LAUNCH` 这个名称对应的是悬浮窗权限，不是开机广播权限或厂家白名单。
- 提交 **`1a5712bc`**（`fix(hotspot): allow boot auto-start without an ADB check`）删除了这段前置检查；v113 的来源提交已包含它。
- 当前有 `carHotspotBootSwitchSavesWithoutAdb` 回归测试。它证明开关可以不经 ADB 保存，不证明车机已完成关闭 ADB 后的真实重启验收。
- 应用已打开但热点、USB 或 VPN 补救失败，属于自动连接阶段的问题，不应记为自启开关依赖 ADB。
- 开机广播能否送达、厂家是否允许拉起页面仍须实车核对。休眠唤醒也不能直接当成完整开机；本轮未确认该车各种唤醒路径的广播行为。

可从应用导出的诊断报告读取：

| 字段 | 能说明什么 |
| --- | --- |
| `received=false` | 尚未记录到开机广播；需结合安装时间、是否完整重启及固件策略判断 |
| `received=true`、`launchEnabledAtBoot=false` | 收到广播，但当时自启设置关闭 |
| `launchResult=failed`、`failureClass=…` | 启动请求抛出异常 |
| `launchResult=startActivity-returned` | 启动调用返回；不能单独证明厂家系统实际显示了页面 |

依据：[StartupDiagnosticSnapshot](../common/src/main/java/com/shilapi/xcertplay/StartupDiagnosticSnapshot.kt)、[SimplifiedSettingsUiTest](../common/src/test/java/com/shilapi/xcertplay/SimplifiedSettingsUiTest.kt)、[StartupDiagnosticSnapshotTest](../common/src/test/java/com/shilapi/xcertplay/StartupDiagnosticSnapshotTest.kt)。

## 7. 后续改造与验收顺序

1. **先去掉多余前置检查**：已具备权限时直接使用，不因 ADB 不可用阻止保存开关或宣告权限不足。
2. **优先处理无障碍服务**：评估 `WRITE_SECURE_SETTINGS` 的实际授予，同时覆盖 USB 自动确认、方向盘首次启用和异常恢复。不能覆盖其他无障碍服务，不能省略写后回读。
3. **再处理实际使用的连接模式**：车机热点已有直接路径；仅在使用 Wi-Fi Direct 时评估扫描暂停的直接调用。原有暂停记录、结束恢复和进程重启恢复逻辑必须保留。
4. **USB／VPN 缺界面补救按需求处理**：它们不是当前所有连接的前提。既有实车决定暂不继续排查 USB 问题，不应仅为清空 ADB 调用数量扩大该范围。
5. **最后评估附加诊断**：可选 shell 诊断与日常功能分开，不应让诊断读取失败阻止应用工作。

每项直接替代至少需要以下证据，才能从“候选”改记为“已实现／已验证”：

- 对应 Manifest 声明、运行时权限回读与实际调用结果；不能只看平台签名或方法返回值。
- 关闭网络 ADB、不依靠电脑先连一次的实车测试。
- 首次开启、关闭、应用重开，以及该功能已有的崩溃／更新后恢复场景。
- 自启区分“收到广播”“打开 DiPlay”“自动连上 CarPlay”；扫描控制区分“暂停成功”和“结束后恢复成功”。

旧资源中的“开机 ADB 修复”“车辆电量需要 ADB”等文案不能作为当前接入证据。当前 `CarPlayHostActivity` 创建 controller 时传入 `vehicleStatusProvider = null`，车辆电量读取不应列成当前实际 ADB 功能。

## 8. 证据与复核入口

### 仓库内

- [公共模块 Manifest](../common/src/main/AndroidManifest.xml)：现有热点、日志、悬浮窗及使用情况权限。
- [共享模块 Manifest](../shared/src/main/AndroidManifest.xml)：车机 DIM／导航、蓝牙及组件管理权限。
- [实车验证范围](LYNK_01_PENDING_VALIDATION.md)：权限前提与效果验收边界。
- 第 3～6 节链接的实际实现与测试。后续修改应从这些入口继续追踪，不以本清单替代运行时检查。

### 本地工作区证据

以下材料位于仓库外的同一工作区，**仅本地可用，不随仓库克隆分发**。文中已摘录本次结论需要的权限和版本信息；无须将整份车机 dump、私人日志或签名资产加入仓库。

- [v104 实车权限与权限定义](../../logs/cap-v104-20261031-session1/boot-audit/package-details.txt)：`Package [com.shihab.diplay]` 的安装权限约在 7301～7369 行；权限注册表包含上述候选系统权限的保护级别。
- [2026-11-01 实车记录](../../docs/实车验证记录-2026-11-01.md)：`READ_LOGS granted=true`、停包权限确认，以及尚未完成的功能验收。
- [v113 构建记录](../../artifacts/dev-release-20261102-85dd3684/BUILD.txt)、[APK 核验记录](../../artifacts/dev-release-20261102-85dd3684/verification.json)。APK SHA-256：`18c06a645d21416b5f8e09fc9930b0a0dc12e1298fe4addc839d65628e676446`。
- 本轮核对了 v113 APK 的 Manifest、自启开关与 `BootReceiver`。未安装该包、未重新采集车机权限、未执行关闭 ADB 后的实车重启或直接替代验证。

## 9. 2026-11-02 自启与热点调整

本次只处理开机打开应用、手动热点、自动热点及相关提示，不扩展到无障碍、Wi-Fi Direct 扫描、USB 或 VPN 权限改造。

| 环节 | 调整后的行为 |
| --- | --- |
| 开机打开 DiPlay | 沿用现有普通开关与 `BootReceiver`，保存和启动均不检查 ADB |
| 手动／自动热点权限预检 | 共用 `CarHotspotTethering.permitted()`：已有 `TETHER_PRIVILEGED` 或已允许修改系统设置，均可尝试系统热点接口；最终仍以系统调用与热点状态回读为准 |
| 本车热点启动与停止 | 沿用 Android 9 直接接口、状态检查及既有失败恢复；不新增权限声明，不新增 ADB 授权要求 |
| 原有通用 ADB 兜底 | 保留为可选兼容路径，不作为开关或平台调用的前置条件。ADB 不可用不能否定已经成功的系统调用 |
| 热点配置提示 | 明确 Android 9 平台签名路径无需 ADB，修正原先误写“Android 8 到 10 不支持”的文案 |
| “自动加入热点”实验修复 | 已从所有版本页面移除，并删除专用 ADB helper、修复事务、资源与对应测试；统一使用系统热点接口及车机设置 |

无系统热点权限时仍需用户允许“修改系统设置”或手动开启车机热点；不以强制打开 ADB 代替实际权限检查。热点已开启、凭据保护、取消、超时及连接中的操作限制维持原有规则。

回归依据：[CarHotspotLaunchFlowTest](../common/src/test/java/com/shilapi/xcertplay/CarHotspotLaunchFlowTest.kt) 的 `platformPermissionStartsAndStopsWithoutWriteSettingsOrAdb` 覆盖仅授予平台热点权限、未允许修改系统设置时，应用启动准备与手动关闭都走直接系统接口，且不加载 ADB 密钥；[SimplifiedSettingsUiTest](../common/src/test/java/com/shilapi/xcertplay/SimplifiedSettingsUiTest.kt) 覆盖 Android 9／13 均保留标准热点控件、不再出现实验修复入口，并生成当前车机 1920×1080 中文截图。

删除实验修复不会主动修改设备配置。Android 13+ 若曾通过旧版修复写入广播信息并需恢复，须使用对应旧版及原有私有备份；本车 Android 9 从未支持这项修复。旧流程仅作为历史研究保留在 [热点自动加入记录](WIRELESS_HOTSPOT_JOIN.md) 中。

本轮验证：仓库要求的 shared／common／home 单元测试、mobile／home／maphost／siribridge 的 Lint 与 Debug 构建全部通过，已人工核对上述中文截图。未生成新的平台签名发布包，未进行实车断开 ADB 后的完整重启验收。

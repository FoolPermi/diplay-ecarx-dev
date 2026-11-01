# 753 导航接管路线决策（停包方案）

2026-11-01 · 领克 01（DCY11，753 车机）· 本文档为决策证据，随仓库保存。

## 决策

导航接管采用**停包方案**：CarPlay 导航开始时禁用原厂 `ecarx.naviservice`，结束后重新启用并延迟 2 秒启动服务（对齐 HY/DCYTools 1.13 的顺序，但实现方式不同，见下）。**不保留 MQTT 距离纠偏（`EcarxDimDistanceGuard`）作为兜底**；该守护通道停用，代码仅保留在仓库历史中。

## 背景与取舍

原厂导航插件会通过共享 MQTT 主题向仪表写入错误的"1 米"距离，与 CarPlay 投屏的导航数据冲突。曾并行评估两条路线：

| 维度 | 停包（采用） | MQTT 纠偏（弃用） |
|---|---|---|
| 脏数据来源 | 从源头移除写入方，实测归零 | 事后纠正，覆盖面不可枚举 |
| 依赖面 | 包名 + 平台签名权限（`CHANGE_COMPONENT_ENABLED_STATE`），失败可检测 | 无文档的内部主题 + 逐字节复刻的载荷格式，固件变更即静默失效 |
| 代价 | 原厂首页导航卡片绑定被删、需手工重加（已确认接受） | 错误值短暂上总线；车道面缺陷（去重跳过导致原厂清空后不重发）已复现 |
| 失败模式 | 恢复失败＝原厂导航不可用，严重但可检测、可修复 | 静默失效，脏值回归而无告警 |

选择停包的核心理由：**纠偏是数据面的寄生方案，能力边界未知且失效不可见；停包的接口粗但稳，失败可检测。** 宁可接受可修复的严重错误，也不维持一条会静默失效的通道。

## 决策前提（均已由车主确认）

1. 实车停包权限可用（DiPlay 车测包为 platform 签名，权限属签名级）。
2. 首页导航卡片消失、恢复后手工重加一次，可接受。

## 实现要求

- **停包**：不照搬 HY 的 `Runtime.exec("pm disable ...")`（DCYTools 的隐式 shell 身份路径），直接调用 `PackageManager.setApplicationEnabledSetting("ecarx.naviservice", DISABLED_USER, 0)`，前后读回包状态校验并记日志。台架重放已验证的正是 `DISABLED_USER`（enabled=3）。
- **恢复**：设回 `COMPONENT_ENABLED_STATE_DEFAULT` → 延迟 2 秒 → `startForegroundService` 启动 NaviService（Android 9 普通后台 startservice 会被拒绝，台架已证实）→ 校验进程存活。
- **异常退出恢复（HY 未做，必须补）**：停包前写持久标记，恢复成功后清除；应用启动与投屏会话结束时检查"标记存在且无活动导航"即自动恢复原厂包。
- **守护停用**：停包成功时不得启动 `EcarxDimDistanceGuard`；两条通道不并行。若停包失败，按"可检测错误"处理（告警 + 记录），不静默降级到纠偏。

## 落地前待验证

1. 生产 App 内（非 ADB）执行停包/恢复的完整链路。
2. 异常退出（强杀/崩溃）后的原厂状态自动恢复。
3. 同手机/同地图/同路线 + 仪表录像的实车对照：无 1 米、退出导航后原厂导航可用（卡片手工重加一次）。

## 证据索引（工作区 lyno-carplay）

- `docs/753导航接管方案对比.md` — 两方案完整对比与决策更新（本文档的源文件）。
- `analysis/20261101-hy-vs-diplay/` — 同路线三轮对照：启用期原厂 24 次调用含 3 次 1 米，停用期 0/0；车道丢失复现记录。
- `analysis/20261101-oem-widget/` — 原厂桌面（launcher3 v999）复测：停包删卡片绑定，HY 延迟 2 秒恢复顺序同样不回来，手工重加得到新绑定。
- `handoff-support/emulator-lab/mock/results/internal-navigation-takeover-20261030/` — 75 秒台架：启用期 90 次原厂调用、11 次 1 米；停用期 0/0，DiPlay 452 次不受影响。
- `logs/cap-v104-20261031-session1/analysis/navigation-distance/` — 纠偏能力边界实车证据：v104 密集注入漏末条、需人工恢复（后经 `83c3d41a` 修复并于 v106 实车验证，但车道面缺陷仍在）。
- `analysis/dcytools113-20261029/sources/` — HY 停包机制反编译源码（`r1/b.java` case 6/7、`ApiService.p0` 的 Runtime.exec 实现）。

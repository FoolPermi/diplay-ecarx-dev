# RFCOMM 建链与 iAP2 bootstrap 局部恢复

## 范围与边界

基于 `origin/dev` 的 `9f9f9001`。与 PR #34（Bonjour 异步 teardown）、#35（热点就绪分级确认）独立：不修改其整栈关闭流程与热点采样算法。热点、AirPlay listener、Bonjour、MFi client、endpoint、listener identity、无线 run generation 和 first-TCP watchdog 属于整个无线会话；局部恢复只替换 Bluetooth bootstrap 的 socket、stream、CSM 和 attempt token。

重试策略由 `WirelessBootstrapRecovery` 管理，Android 资源仍由 `CarPlayController` 所有。所有重试运行在既有 worker，关闭旧 CSM 和 RFCOMM 后才创建新 socket，不复用失败 socket，不扫描并猜测其他已配对手机。

## 阶段与预算

| 阶段 | 超时 | 可恢复失败的处理 |
| --- | --- | --- |
| RFCOMM socket 创建／connect | connect 8s | 同目标新 socket 一次，退避 400ms；该预算耗尽后，可切换一次唯一已连接的备用 iPhone |
| CSM link 就绪 | 5s | 与 bootstrap 共用一次重建预算，退避 800ms |
| identification、MFi、订阅 | 三者合计 15s，且受原控制总预算上限约束 | 临时 EOF、握手超时等最多重建一次 RFCOMM + CSM，重新完整认证 |
| 认证后的无线配置交换 | 保留原 transport-wait 60s | StartSession 前临时断链可消耗尚未使用的 bootstrap 重建预算；明确 unavailable 或 transport-wait 到期不局部重试 |
| StartSession 后／Wi-Fi handoff | 保留 first-TCP 30s、handoff 45s 及原长期控制策略 | 不再局部重建；继续由原有 watchdog 和 handoff 规则处理，不能把 session 存在当成成功渲染证据 |

建链、bootstrap、备用目标各有一个恢复预算，整个 wireless run 共享，不随目标切换或成功建链重置。因此最多 4 个 bootstrap socket，不是对每个手机简单循环 5 次。假蓝牙诊断所用随机 UUID 控制探测不属于 bootstrap socket，也不可用于实际协议通信。

实测 RFCOMM connect 为 147–1714ms；8s 是保留超过最大样本 4 倍余量的保守起点，避免原来每个死 socket 都等待 15s。400ms 用于让失败 RFCOMM 资源退出，协议重建使用更长的 800ms 让手机释放旧 CSM。上述数值是工程预算，不代表已证实的实车成功率或延迟改善，仍需真实 ECARX 753 / Android 9 的对照测试。

## 允许恢复的条件

每次尝试前、失败后决定重试前、退避后以及发布 socket 时，必须确认：

- 原无线 run 仍有效，未关闭、失败或切换 generation。
- 热点对象未更换，且 `validateReady()` 重新验证当前接口、地址；不缓存跨周期的 ifindex/scopeId。
- 原 listener identity 对应的 AirPlay service 仍 attached，监听 socket 未关闭；不是仅看端口相同。
- 原 Bonjour 对象仍运行且未关闭。
- 蓝牙 radio 为 ON，有对应版本所需权限，目标仍在配对列表中。
- 没有发送 StartSession，没有开始 handoff、tunnel、AirPlay session 或无线 active。

明确的权限、radio、目标解绑、无 socket manager、假蓝牙、identification 拒绝、MFi AuthenticationFailed／非法协议响应不属于临时失败。MFi 等待超时或通道提前关闭使用明确的 retryable 标志，与认证拒绝分开。嵌套 cause 中的权限、中断、取消和 fatal 错误也禁止重试。无线失败状态还显式携带 `automaticRetryAllowed`：权限／蓝牙关闭／目标丢失与明确协议拒绝要求用户操作，Host 不得将已经停止的局部恢复升级为新的整栈自动重试；手动「重试」仍可在用户修复条件后启动新会话。

## 取消、迟到回调与资源清理

每个 bootstrap 尝试有独立 token；局部恢复不递增整个 wireless generation，避免让保留的 Wi-Fi listener、connection proof 和 watchdog 失效。关闭 bootstrap 时先使 token 失效，再按 CSM → stream → socket 清理，清空旧 fake-link 引用。协议回调、route、artwork、UI 状态和假蓝牙探测检查 run + attempt；旧探测提交失败前还必须持锁复核身份。耗时控制探测在资源锁外执行。

connect worker 只操作捕获的 socket；等待线程每 100ms 检查取消，超时／取消关闭该 socket，worker 迟到返回成功时再次关闭，不能把连接提交给下一次尝试。退避也每 100ms 检查取消。CSM 和握手使用有界协议调用；旧 channel 的关闭会唤醒接收等待。MFi 硬件或远端认证调用沿用其自身 I/O 超时，本补丁不通过强制中断正在进行的硬件操作来重用认证设备。

handshake timeout 不延长较短的总控制 deadline；握手成功后的控制循环不再受短 handshake deadline 影响。所有 terminal 快照保留手机明确 `wirelessUnavailableReported` 的证据，避免将拒绝误判为可重试断链。

## 验证与车测

主机自动化覆盖：重试次数／退避、跨目标共享预算、不可恢复分类、基础设施失效、generation／backoff 取消、旧 attempt 失效、资源关闭顺序与保留 Wi-Fi、connect 取消和迟到成功、listener owner、握手与控制预算分离、unavailable terminal 快照；同时运行原有 first-TCP、handoff、蓝牙 transport 与无线启动回归及仓库 CI 命令。

实车仍需验证（主机 mock 不能证明真实 RFCOMM／MFi）：

1. 同一目标临时 `socket.connect()` 失败 → 400ms → 新 socket 成功；日志包含 attempt、CONNECT、耗时，热点/listener/Bonjour 不重复启动。
2. connect 成功后 CSM 超时或 identification/MFi EOF → 旧 CSM/stream 关闭 → 800ms → 新 socket + 新 CSM；能完整认证进入 tunnel。
3. 两种预算耗尽后只上报一次最终失败；备用目标仅选唯一已连接 iPhone，预算不重置、不在两台之间来回切换。
4. 连接、握手和退避中关闭蓝牙、撤权、解绑目标、关闭热点、退出／重连：没有后续局部新建 socket；迟到旧 connect 不复活会话。
5. 手机明确拒绝认证／无线不可用：没有无意义局部重试，错误原因不丢失。
6. StartSession、first TCP、type-130、首帧与 fallback/晚到 tunnel 各时序下，RFCOMM 结束不破坏正在运行的 Wi-Fi 投屏；first-TCP deadline 不因局部恢复被重置。
7. 记录每阶段耗时与连续失败恢复时长，与报告的 11 次连续失败、84.914s 恢复和 34.626s 握手卡顿对照。不得在无实车数据时宣称成功率提升。

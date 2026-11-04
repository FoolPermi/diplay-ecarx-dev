# 车机热点与现有 Wi-Fi：启动耗时采样

仅针对 **Built-in car hotspot / MANUAL** 与 **Existing Wi-Fi / Same LAN / EXISTING_WIFI**；不新增 P2P 采样。
记录使用 `SystemClock.elapsedRealtime()`（含休眠的 Android 自启动单调毫秒）；不与 `System.nanoTime()` 的差值相减。

## 导出与采样

- 车机：DiPlay → 设置 → 诊断 → 保存诊断报告，查看 `Wireless startup timeline`，保留最近 80 个阶段事件。时间线还写入 `DiPlay-Startup` logcat 标签；不保存 SSID、设备地址、密码、MFi 内容或异常消息。
- 开发台架可执行 `adb logcat -v threadtime -s DiPlay-Startup:I`，但量产运行**不依赖 ADB**。
- 分别测试整车冷启动、ACC 唤醒、应用冷启动。关闭/开启热点自启动、已有热点、已有同 LAN 为不同实验条件；固定设备、iOS、频段和版本，交错至少 10 轮，所有超时/失败均保留。
- `BOOT_BROADCAST` 仅表示 `BOOT_COMPLETED` 已被应用收到，**不等于 ACC 上电时刻**；ACC 真实起点需外部摄像/车辆信号。设备若只休眠唤醒，可能根本没有此广播。
- `VIDEO_FIRST_FRAME` 表示主屏视频解码首次回调；`UI_FIRST_FRAME_CALLBACK` 表示 Host 主线程处理该回调，**均不是物理屏幕首次可操作/可见时刻**。最终应配合外部录像验证画面出现与输入可操作。

## 阶段与判读

1. `LAUNCHER_CREATE` → `AUTO_CONNECT_DISPATCH` → `PROJECTION_REQUEST` → `HOST_CREATE`：自动启动及页面流转。
2. `HOTSPOT_PREPARE_BEGIN/END`、`NETWORK_BACKEND_READY`、`ENDPOINT_READY`：系统热点开关、接口/地址；现有 Wi-Fi 不创建热点。
3. `CONTROLLER_START`、`LOCAL_MFI_READY`、`WIRELESS_RUN_START`、`AIRPLAY_LISTENING`、`BONJOUR_STARTED`：控制器、监听准备。
4. `RFCOMM_CONNECTED`、`IAP2_CSM_READY`、`IPHONE_MFI_ACCEPTED`、`START_SESSION_SENT`：蓝牙与协议。
5. `AIRPLAY_TCP_ACCEPTED`、`AIRPLAY_SESSION_ACTIVE`、`VIDEO_FIRST_FRAME`、`UI_FIRST_FRAME_CALLBACK`：Wi-Fi 建链、会话、视频。
6. `WIRELESS_FAILED` 为失败检查点；结合现有失败类型日志分析，不将恢复超时预算作为正常连接耗时。

`owner` 为进程内控制器标识，`generation` 为同一控制器的重试代次，`attempt` 为蓝牙 bootstrap 尝试序号。一次失败/回退可能产生多个代次。时间线为有限环形记录，不是独立的系统开机监控服务，也不自动计算连接成功率。

## 基线

#41 的 START → 首帧约 3.685s（热点新版中位数）是在热点已开启、手机已入网的台架条件下测得，不可套用到车机上电→首屏。新增时间线用于补齐应用及网络启动阶段，并由实车数据决定下一轮优化。

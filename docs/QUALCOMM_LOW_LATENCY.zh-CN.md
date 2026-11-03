# 高通私有低延迟解码实车验证

面向 Android 9 / ECARX 753 / 骁龙 820A。复用「设置 → 高级 → 视频与音频 → 高通低延迟解码（实验性）」开关，默认关闭，保存后由用户选择重新连接，不会自动中断当前连接。

## 请求与回退

开启后，识别到 `OMX.qcom.*` 或 `c2.qti.*` 解码器时，向 `MediaCodec.configure()` 提交 `vendor.qti-ext-dec-low-latency.enable=1`。Android 9 不调用 Android 11 才提供的标准低延迟 API，也不依赖 Android 12 的私有参数枚举接口。其他厂商解码器不接收这个参数。

私有模式配置或启动失败后，释放候选解码器，使用相同的 operating rate、优先级和 CSD，撤掉私有参数重新尝试。运行中出现解码器 IllegalStateException（含 CodecException）或输入缓冲区持续不可用时，也在重建前撤掉私有请求。本视频流后续重建保持回退，新的连接可重新尝试；仍失败时沿用现有最小格式与软件解码回退路径。静默忽略参数、无异常的花屏或卡顿不能自动识别，请关闭开关并重连。

`vendor.qti-ext-dec-picture-order.enable` 会改变画序，不能视作低延迟的同义参数。本开关不再无条件设置它，避免对没有确认无 B 帧的流改变重排。原有流畅视频的 H.264 路径仍可在新系统枚举到它时请求；发生本开关的回退后也撤掉它。

## 停车对照测试（不需要 ADB）

1. 使用按 [BUILD.md](BUILD.md) 的 standalone 流程提供认证资料的测试 APK。相同包名覆盖安装必须使用原签名；不要为解决签名冲突而卸载正式版。普通源码 Debug 包不含认证资料，不能用于独立 CarPlay 连接。
2. 在诊断设置打开调试日志，先关闭低延迟解码并重新连接 CarPlay。保持相同手机、连接方式、H.264、分辨率、帧率和流畅视频设置。
3. 持续拖动地图、切换页面，再停止操作，记录响应、花屏、卡顿及恢复情况，导出诊断日志。
4. 开启低延迟解码、重新连接，重复相同操作并导出日志；再关闭重连复测。重点检查首次出图、前后台切换与反复重连。首次失败可能来自整个格式被拒绝，回退日志不直接证明私有参数不受支持。

## 诊断怎么读

- `decoder=... api=28`：实际选到的解码器与系统版本（API 版本同时出现在私有请求尝试日志）。
- `QTI low-latency attempt ... request=vendor.qti-ext-dec-low-latency.enable=1`：准备向该候选提交私有请求。
- `configure/start returned`：配置和启动调用返回，没有声明底层已生效。
- `qtiLowLatencyRequested`：开关请求；`qtiLowLatencySubmitted`：当前成功启动的候选是否带私有请求；`qtiLowLatencyFallback`：本视频流是否已经撤掉请求。
- `QTI low-latency fallback ... phase=...`：配置/启动失败，或运行异常与输入停滞后的回退原因；随后应出现没有私有请求的新解码器与首帧日志。
- `QTI driver-reported output format ...`：仅为输出格式中的驱动报告值。`unreported` 表示没有可读值，不代表不支持；0 或 1 可能是缓存值，也不能单独判定成败。
- `first frame rendered` / `QTI first output released for rendering`：已将首个输出缓冲区释放给渲染，不能证明屏幕呈现时刻或硬件低延迟模式。
- `video stats ... decode p50=... p90=... recoveries=...`：已有的入队至输出统计，可作开关 A/B 对照。它不是纯硬件处理时间或触摸到显示的端到端延迟，仍受帧间隔、排队和线程调度影响；静止画面的长间隔另看 `heldDecodeN` / `heldDecodeMax`。

所有模式诊断保留 `effectiveness=unverified`。需结合连续操作时的统计、画面稳定性和实车响应判断是否有帮助。

## 官方源码依据与适用范围

Android 9 的高通 SDM845 OMX 实现将 [low-latency 与 picture-order 分别注册为输出方向扩展](https://android.googlesource.com/platform/hardware/qcom/sdm845/media/+/android-9.0.0_r37/mm-video-v4l2/vidc/vdec/src/omx_vdec_extensions.hpp)。对应的 [V4L2 实现](https://android.googlesource.com/platform/hardware/qcom/sdm845/media/+/android-9.0.0_r37/mm-video-v4l2/vidc/vdec/src/omx_vdec_v4l2.cpp) 展示了驱动控制与缓存报告路径。

更接近目标的 Android 9 [MSM8996 扩展表](https://android.googlesource.com/platform/hardware/qcom/media/+/android-9.0.0_r37/msm8996/mm-video-v4l2/vidc/vdec/src/omx_vdec_extensions.hpp) 列有 picture-order，未列出 low-latency；两者不能互相替代为支持证据。

这些源码只证明部分 Android 9 高通实现存在私有低延迟接口，不证明 820A / 753 的具体车厂固件采用相同实现。配置返回、驱动报告值、单元测试和 APK 构建均不能替代目标车机验证。

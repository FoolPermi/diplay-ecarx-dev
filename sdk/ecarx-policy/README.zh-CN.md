# ECARX GKUI Policy SDK 接口库

从目标车机运行库恢复的 **compileOnly SDK**，对应 GKUI **3.3.9 / 339，修订号 2**。
用途是让 Java / Kotlin 按原始接口编译；运行实现由车机提供。

**当前仅保留接口源码和可独立构建的 JAR，不接入应用，也不作为应用测试依赖。**
应用当前只将已找到的 AdaptAPI 编译库用于 SDK 直调；麦克风 Policy 继续反射调用。

## 来源与版本

来源为本地 dump：

```text
jimu-ext/extract/20261031-092437/framework/ecarx.gkui.openapi.impl.jar
SHA-256: e77681de3693910f099e0d70d35e8e7a9875de444a5e2e38258b27994df96921
```

该文件包含 DEX，接口经反编译整理，并以 DEX 的类定义和方法描述符核对。
运行库中的 `com.ecarx.BuildConfig` 给出版本 `3.3.9` / `339`；
`ECarXAPIBase` 的运行时初始化给出修订号 `2`。
SystemUI Plugin 内的 3.3.0 声明与 HY 内的 EAS 4.6.20 都不是这个版本，未混入本库。

## 恢复范围

保留原包名、可见性、继承关系、方法返回值和参数类型、常量、嵌套回调及注解：

```text
com.ecarx.sdk.ECarXAPIBase
com.ecarx.sdk.openapi.ECarXApiClient
  ├─ Callback
  └─ ConnectionCallbacks
com.ecarx.sdk.policy
  ├─ IPolicyAPI ← InternalPolicyAPI ← PolicyAPI extends ECarXAPIBase
  ├─ IAudioPolicy / IMicResourceCallback
  ├─ IAudioAttributes / IStoragePolicy / IVoiceAssistantPolicy
  └─ IStateAPI ← StateAPI extends ECarXAPIBase
com.ecarx.sdk.log.ILog / LogProxy
```

`IPolicyAPI` 定义各 Policy 的获取方法，`PolicyAPI` 保留原来的静态工厂。
`IAudioPolicy` 的麦克风方法为：

```java
boolean requestMic(@MicRequestType int requestType, IMicResourceCallback callback);
boolean releaseMic(IMicResourceCallback callback);
```

`isNeedrequestMic()` 是 HY 的应用封装方法，不属于厂商接口。
未恢复 PolicyAPIImpl、Binder 客户端或服务实现；具体方法体以 `SDK stub` 异常占位。
运行时初始化的版本字段保留为非常量表达式，避免编译时把车机版本内联进应用。

## 构建与使用

```sh
./gradlew :ecarx-policy-sdk:sdkJar
```

产物：`sdk/ecarx-policy/build/libs/ecarx-policy-3.3.9-stubs.jar`。
以后决定启用时可使用模块依赖（当前未配置）：

```groovy
compileOnly project(':ecarx-policy-sdk')
```

独立工程可使用 JAR：

```groovy
compileOnly files('libs/ecarx-policy-3.3.9-stubs.jar')
```

不得使用 `implementation` 将这些占位类打入 APK。普通 Android 没有对应类时，
应用边界须跳过仲裁；接口存在但服务拒绝或抛错时也继续原有直接录音路径。
Shared 测试使用独立的运行时接口 fixture 验证反射调用与录音生命周期，
Common 测试不带这些厂商类型，验证缺失 API 的情况。

该恢复范围不包含 MediaCenter 或新平台 EAS；后续恢复它们时应按各自实际版本独立核对契约。

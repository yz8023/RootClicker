# Root连点器 · RootClicker

一个**走 root 权限、不依赖无障碍服务**的 Android 连点器。

界面基于 [ZeroUI](https://github.com/yz8023/ZeroUI)（Jetpack Compose 设计系统，Miuix / Material 3 双风格）。

> 市面上的连点器大多用无障碍 `dispatchGesture`，每次启动都要重新授权，注入频率低、多指同点也会被系统吃掉。
> 这个项目换成 root 方案：用 `app_process` 起一个常驻的注入进程，直接向 `InputManager` 注入 `MotionEvent`，
> 没有无障碍权限弹窗，单点延迟可以压到毫秒级，长按、多指同点、滑动都能做。

## 功能

| 分类 | 功能 |
| --- | --- |
| 点位 | 点位数量不设上限；悬浮窗取点（十字/点击即取，切到目标应用取点）；手动输入坐标；单点编辑、删除、清空 |
| 点击模式 | **依次点击**（逐点）/ **同时点击**（同一帧多指按下）/ **随机点击**；点位顺序可打乱 |
| 时序 | 点击间隔、点位间隔、按压时长（≥600 ms 即长按）、启动延迟、循环次数（0 = 无限）、坐标随机抖动 |
| 条件点击 | 屏幕颜色匹配：等待指定像素颜色出现才开点（可设容差、采样间隔、超时） |
| 录制 | root 直读 `/dev/input` 的原始触摸事件，录点击 / 长按 / 滑动；自动保存为脚本；支持播放速度、循环次数 |
| 悬浮窗 | 可拖动控制条（开始 / 停止 / 取点 / 编辑 / 关闭）+ 屏幕上的点位序号标记，标记可拖动改坐标 |
| 运行 | 前台服务常驻，退出应用后继续连点；通知栏一键启停 |
| 其他 | 注入通道自检、运行日志、震动反馈、root / 悬浮窗 / 通知权限引导、界面主题（跟随 ZeroUI） |

## 工作原理

```
┌──────────────┐  su -c   ┌────────────────────────────────────────┐
│ App 进程      │ ───────► │ app_process（uid 0，常驻）              │
│ ClickEngine   │  stdin   │ io.Injector                            │
│  (时序调度)    │ ◄─────── │  └─ InputManager.injectInputEvent(...) │
└──────────────┘  stdout  └────────────────────────────────────────┘
                                     │ MotionEvent（DOWN/MOVE/UP）
                                     ▼
                             系统输入管线 → 前台任意应用
```

* **注入**：`app_process` 以 root 身份加载注入器 dex，反射拿到 `InputManager`，用
  `injectInputEvent(event, INJECT_INPUT_EVENT_MODE_ASYNC)` 直接把触摸事件塞进系统输入队列。
  root uid 本身就通过 `INJECT_EVENTS` 权限校验，所以不需要无障碍、不需要 `adb`、不需要壳。
  APK 是 multidex，而 `app_process` 只认 classpath 里的 `classes.dex`，因此运行时会扫描 APK
  里所有 `classes*.dex`，用 `DexFile` 验证哪个真正定义了注入器类，再把它复制出来作为 classpath
  （见 `InjectorDex.kt`）。
* **兜底**：注入进程起不来（个别 ROM 的 SELinux 限制）时，自动退回 `su -c input tap x y`，
  功能可用但频率会低很多，设置页可以看到当前走的是哪条通道。
* **录制**：`getevent -lt <触摸设备>`，把 `ABS_MT_POSITION_X/Y`、`ABS_MT_TRACKING_ID`、`BTN_TOUCH`
  按 `SYN_REPORT` 分帧解析成「按下点 → 抬起点 → 时长」的手势，坐标归一化到 0~1 保存，回放时再乘回屏幕尺寸。
* **条件点击**：`screencap -p` 取一帧，解码后取目标点附近 5×5 的平均色与目标色按容差比对。
  截图本身有 100~300 ms 开销，所以只用于「等待颜色出现」，不参与点击循环。

## 快速开始

1. 安装 APK（Releases 里有已签名的 `app-release.apk`，或在本地构建）。
2. 打开应用 → 允许 root（Magisk / KernelSU / SuperSU 弹窗）→ 允许悬浮窗 → 允许通知。
3. 点「悬浮窗取点」→ 应用自动退到后台 → 在目标应用界面上点一下就是一个点位，点「完成」结束。
4. 回到应用调整间隔、按压时长、模式 → 点「开始连点」（会自动退到后台，用悬浮条或通知停止）。

> 首次运行欢迎页会检查 root / 悬浮窗 / 通知三项，缺哪项都可以继续，功能页里会提示。

## 本地构建

需要 JDK 21 + Android SDK 37（`compileSdk 37`，`minSdk 24`）。

```bash
./gradlew :app:assembleDebug      # 调试包
./gradlew :app:assembleRelease    # 发布包（用仓库内 keystore 签名）
./gradlew :app:testDebugUnitTest  # 单元测试（含连点引擎时序测试）
```

产物：`app/build/outputs/apk/release/app-release.apk`。

`keystore/roottap.jks`（别名 / 口令均为 `roottap`）随仓库提供，只是为了让你每次构建出的
APK 能原地升级；自己长期分发请换成私钥，不要把这个 keystore 用在别的项目上。

## 已知限制

* 只能用于**已 root** 的设备，未 root 时应用无法注入（会提示授权失败）。
* 录制按单指记录：多指手势会被简化成第一根手指的轨迹。
* 颜色条件的轮询频率受 `screencap` 限制，最快约 300 ms 一次。
* 坐标按屏幕绝对像素保存，设备旋转或分辨率变化时按比例缩放；横竖屏切换后最好重新取点。
* 部分 ROM（如带强 SELinux 策略的定制系统）可能拦截 `app_process`，此时会自动使用 `input` 兜底通道。

## 免责声明

本项目仅用于自动化自己的操作、测试与辅助场景（如批量确认自己的应用、游戏中重复性操作等）。
请遵守你所在地区法律与所使用应用的用户协议；由使用本工具产生的一切后果由使用者自行承担。

## 致谢与许可

* 界面与设计系统：[ZeroUI](https://github.com/yz8023/ZeroUI)（Apache-2.0）、[Miuix](https://github.com/compose-miuix-ui/miuix)、Jetpack Compose。
* 本项目同样以 Apache-2.0 发布，见 [LICENSE](LICENSE)。

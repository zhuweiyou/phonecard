# AGENTS.md · phonecard 开发指南（开发者 / AI）

> 本文件面向**开发者与 AI Agent**：记录项目结构、构建与发布方式、卡片开发与注册机制的调研结论。
> 面向使用者的说明（下载 / 安装 / 添加桌面）见 [README.md](README.md)。

## 项目概况

- 应用名：`手机桌面卡片`；包名：`com.zhuweiyou.phonecard`
- 一个 APK 内可包含多张**标准 Android AppWidget** 桌面卡片，一张卡片 = 一个 `<receiver>`。
- 现有卡片：

| 卡片 | 类 | 显示名（选择列表里） |
| --- | --- | --- |
| 01 白色时钟 | `card/WhiteClockCard` | `手机桌面卡片-白色时钟` |

## 目录结构

```
phonecard/
├── AndroidManifest.xml                    # 每张卡片一个 <receiver>
├── build.sh                               # 纯命令行构建（aapt2/javac/d8/zipalign/apksigner）
├── .github/workflows/release.yml          # 手动填版本号 -> 构建 APK -> 打 tag -> 发 Release
├── AGENTS.md                              # 本文件：开发/AI 指南
├── README.md                              # 使用者视角：下载 + 使用方法
├── keystore.jks                           # 本地签名密钥（已 .gitignore，不入库；CI 从 Secrets 注入）
├── res/
│   ├── layout/card_white_clock.xml        # 卡片 01 布局（字号/间距/颜色都在这）
│   ├── xml/card_white_clock_info.xml      # 卡片 01 的 appwidget 配置（尺寸/预览）
│   ├── values/strings.xml                 # app_name / card_white_clock
│   └── drawable/ic_launcher.xml
└── src/com/zhuweiyou/phonecard/
    ├── MainActivity.java                  # 说明页：本包提供哪些卡片
    └── card/
        └── WhiteClockCard.java            # 卡片 01：手机桌面卡片-白色时钟（自包含，无基类）
```

## 构建（本地）

```bash
./build.sh                 # 产物：phonecard.apk
adb install -r phonecard.apk
```

工具链：Android SDK `/opt/homebrew/share/android-commandlinetools`（build-tools 34.0.0 + android-34）、JDK `/opt/homebrew/opt/openjdk`。

`build.sh` 支持用环境变量注入版本号 / 签名 / 产物名（不给则用默认值，行为不变）：

```bash
VERSION_NAME=1.0.0 VERSION_CODE=10000 OUTPUT_APK=phonecard-v1.0.0.apk ./build.sh
```

可注入的变量：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `VERSION_NAME` | `1.0` | manifest `versionName` |
| `VERSION_CODE` | `1` | manifest `versionCode` |
| `KEYSTORE_FILE` | `keystore.jks` | 签名密钥文件 |
| `KEYSTORE_PASS` / `KEY_PASS` | `android` | store / key 口令 |
| `KEY_ALIAS` | `androiddebugkey` | 密钥别名 |
| `OUTPUT_APK` | `phonecard.apk` | 产物路径 |
| `ANDROID_SDK_ROOT` / `ANDROID_HOME` | 本机路径 | SDK 根目录 |
| `ANDROID_BUILD_TOOLS_DIR` / `ANDROID_PLATFORM_JAR` | 由 SDK 推导 | 手动指定 build-tools / android.jar |

签名：本地用 `keystore.jks`（已在 `.gitignore`，**不入库**）；CI 从仓库 Secrets 注入同一把密钥 ——
`KEYSTORE_BASE64`（base64 后的 keystore 文件）、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
证书是 Android Debug 自签名（别名 `androiddebugkey`，口令 `android`，指纹 `SHA-256:5A:BD:CD:1D…91:D0:26`），
**固定不变**，因此多次发布签名一致、可覆盖升级；但属调试证书，自用/侧载足够，上架应用商店需换正式 release keystore。

## 发布 Release（GitHub Actions）

仓库：`git@github.com:zhuweiyou/phonecard.git`，默认分支 `main`。

`.github/workflows/release.yml` **仅手动触发**（`workflow_dispatch`，Actions 页 → Run workflow 填表）：

- **`main` 分支只放代码，push 不会构建**；只有手动填表才构建并发布版本。
- 填两个字段：`version`（`vX.Y.Z`，如 `v1.0.0`）+ `description`（Release 正文）。
- 流程：校验版本格式/查重 tag → 构建 release APK → `apksigner verify` 验签 → 打 `vX.Y.Z` tag → `gh release create` 建 GitHub Release 并挂上 `phonecard-vX.Y.Z.apk`。
- `versionCode` 由版本号推导：`major*10000 + minor*100 + patch`（`v1.0.0` → `10000`），每段须 < 100。
- 签名密钥来自 Secrets：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
  （需先在仓库 Settings → Secrets and variables → Actions 配好；缺 `KEYSTORE_BASE64` 会直接失败）。

## 新增一张卡片（4 步）

本项目每张卡片是**自包含**的 `AppWidgetProvider`，**没有共享基类**（只有一张卡，避免过度抽象；将来卡多了、确有重复逻辑再抽）。

1. 新建 `card/XxxCard.java`，`extends AppWidgetProvider`：`onUpdate` 里 `updateAppWidget` 渲染；要响应系统变化就在 `onReceive` 里处理（照抄 `WhiteClockCard`）；
2. 新建 `res/layout/card_xxx.xml`（只能用 RemoteViews 支持的控件：TextView/ImageView/LinearLayout/FrameLayout/Button 等）；
3. 新建 `res/xml/card_xxx_info.xml`（尺寸/预览）；
4. Manifest 里复制一个 `<receiver>`，改 `android:name`、`android:label`、intent-filter、`android:resource`。

`strings.xml` 里加 `card_xxx` 作为显示名，命名建议统一成 `手机桌面卡片-XXX`，在选择列表里好认（第三方小组件一多，名字前缀很重要）。

## 刷新机制（App 端零刷新）

- **时间**、**日期** 都是布局里的 `TextClock`：
  - 时间：`format12Hour=h:mm` / `format24Hour=HH:mm`；
  - 日期：`format12Hour/format24Hour = M月d日  E`。
  由桌面进程自己走时，跨分/跨天/改时间/换时区/换语言都会自动更新 → 不需要我们刷新、不重绘、不闪；
- **不申请任何权限、不发广播、不排闹钟**（`minSdk 29` 也不需要），最省电；
- **唯一例外**：`MY_PACKAGE_REPLACED`（App 升级）时整块重刷一次，让新的布局/字号生效（否则旧 RemoteViews 缓存不更新）。

## 样式微调

| 想改什么 | 改哪里 |
| --- | --- |
| 时间字号 / 粗细 | `res/layout/card_white_clock.xml` 的 `@+id/time`（默认 80sp、bold、`#FFFFFF`） |
| 日期字号 / 粗细 | 同文件 `@+id/date`（默认 17sp、sans-serif-medium+bold、`#FFFFFF`） |
| 两行间距 | `@+id/time` 的 `android:layout_marginTop`（默认 6dp） |
| 卡片占几格 / 最小尺寸 | `res/xml/card_white_clock_info.xml` |
| 加半透明底板 | 给根 `LinearLayout` 加 `android:background="@drawable/xxx"` |

改完 `./build.sh` + `adb install -r` 即可，桌面卡片会立刻刷新。

## 卡片开发与注册方式（调研结论）

> 目的：先把「桌面卡片到底怎么开发、怎么注册」搞清楚，再写代码。下面是结论，代码按此实现。

### 一、先分清两种"卡片"

| | ① 系统卡片（ColorOS「卡片中心」里那些） | ② 标准 Android 桌面小组件（AppWidget） |
| --- | --- | --- |
| 例子 | 小布建议、日程、倒数日、快递、英语句子 | 百度地图-打车卡、快手热榜、支付宝小组件 |
| 归属 | OPPO/一加自家服务体系 | 任意第三方 APK |
| 技术 | 私有：UI 引擎解析 DSL（`config.json` / `card-config.json` / `capability.json`），卡片布局预置在 APK 的 `assets` 里，由系统"卡片中心/负一屏/小布建议"统一托管 | 公开：`AppWidgetProvider` + `RemoteViews` |
| 上架方式 | 必须走 OPPO 开放平台（智慧服务/负一屏/流体云/泛在卡片）申请接入，桌面主卡位置还由系统"智慧大脑"决策 | 装完 APK 自动出现在「插件」列表，用户自己拖到桌面 |
| 第三方能否自行注册进"卡片中心" | **不能**（无公开注册入口，需平台合作/系统签名/预装） | 可以（这是唯一自助路径） |

结论：**普通开发者能自主做、能自助安装的"卡片"，就是标准 AppWidget。** 「卡片中心」是厂商私域，不做平台接入进不去；但外观可以做到几乎一样。

参考：
- Android 官方《应用 widget 概览》 https://developer.android.com/develop/ui/views/appwidgets/overview
- Android 官方《创建高级 widget》 https://developer.android.com/guide/topics/appwidgets/advanced
- OPPO 开放平台「卡片开发概述」（config.json / card-config.json / 生命周期 / 模板） https://open.oppomobile.com/new/developmentDoc/info?id=12965
- OPPO 开放平台「UI 引擎接入指南」（DSL 布局预置 assets） https://open.oppomobile.com/documentation/page/info?id=11719

### 二、AppWidget 的注册方式（和 Activity 完全不同）

卡片不是 Activity，不需要页面、不需要后台常驻界面。注册分三处：

1. **Manifest 里声明一个 `<receiver>`**，带 `APPWIDGET_UPDATE` 的 intent-filter；
2. **`<meta-data android:name="android.appwidget.provider" android:resource="@xml/xxx_info"/>`** 指向配置 XML；
3. **appwidget-provider XML** 描述尺寸、预览、初始布局、刷新周期。

```xml
<receiver android:name=".card.WhiteClockCard" android:exported="true"
          android:label="@string/card_white_clock">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/card_white_clock_info"/>
</receiver>
```

```xml
<!-- res/xml/card_white_clock_info.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="320dp" android:minHeight="150dp"
    android:targetCellWidth="5" android:targetCellHeight="2"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/card_white_clock"
    android:previewLayout="@layout/card_white_clock"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"/>
```

要点：
- `android:exported="true"`：Android 12+ 带 intent-filter 的 receiver 必须显式声明，否则不生效。
- `previewLayout`（API 31+）优先于 `previewImage`，选卡片时显示的预览就是它。
- `targetCellWidth/Height` 是给 Android 12+ 启动器看的"期望占几格"；`minWidth/minHeight` 用 `70×格数−30` 估算（5 格≈320dp）。
- `updatePeriodMillis` 最小 30 分钟，做秒级时钟没用 → 设 0；本项目连闹钟都不用，时间、日期都交给 `TextClock`。

### 三、一个 APK 注册多张卡片（本项目采用）

机制：**一张卡片 = 一个 `<receiver>` + 一个 `AppWidgetProvider` 子类**。同一个 APK 里放 N 个 receiver，就注册 N 张卡片，各自有独立类、独立布局、独立 provider XML、独立 `android:label`（就是选择列表里显示的名字）。

当前实现是**自包含单文件**（只有一张卡，不做基类抽象）：

```
src/com/zhuweiyou/phonecard/card/
└── WhiteClockCard.java   # 卡片 01：手机桌面卡片-白色时钟（extends AppWidgetProvider，无基类）
```

- `onUpdate` 渲染每张实例（`updateAppWidget`）；
- `onReceive` 只处理 `MY_PACKAGE_REPLACED`（升级后整块重刷）；时间/日期由 `TextClock` 自走，无需广播；
- `getAppWidgetIds(self)` 遍历本卡片全部实例，用户摆多份也都能刷到；
- PendingIntent 的 requestCode 用 `widgetId*10+slot`，多实例/多卡片互不覆盖；
- **不排闹钟**：时间、日期都由 `TextClock` 自走，App 端零刷新，见「刷新机制」。

### 四、刷新与限制（实测 + 文档结论）

- 时钟类不建议靠 `AlarmManager` 周期刷（`setRepeating` 在 Doze 下被合并/延后，`setExact*` 又要精确闹钟权限）；本项目 `updatePeriodMillis=0`，时间**和日期**都用 `TextClock` 自走，App 端**零刷新、零闹钟、零权限**。
- 精确闹钟（`setExact*`）在 Android 12+ 需要 `SCHEDULE_EXACT_ALARM` 权限，不建议为了一张卡片去申请。
- RemoteViews 不支持自定义 View、不支持动画、不支持任意 Canvas；渐变/圆角用 shape drawable。
- 点击交互：日期 → `MAIN + CATEGORY_APP_CALENDAR` 打开系统日历；时间 → 用 `queryIntentActivities`
  找出包名含 `alarmclock/deskclock/.clock` 的时钟 App 直接启动（不硬编码厂商包名，跨机型通用）。
  注意 Android 11+ 包可见性：`resolveActivity` 在没声明 `<queries>` 时会误判为 null，本项目已声明。
- 字体：时间 80sp **bold**、日期 17sp **sans-serif-medium + bold**（原卡片偏细）。
- 卡片尺寸由启动器按格子给，内部一律 `match_parent` + 居中排布，避免固定死宽高。
- ColorOS 的「卡片中心」不会收录第三方 AppWidget；它们统一出现在 **卡片中心 → 最底部「插件」→ 选择微件** 列表里（也等价于系统原生 AppWidget 选择器）。
- 应用可以不建 Activity 做成"纯卡片包"；本项目保留一个 Activity，只是为了给用户一个"这个包提供哪些卡片"的说明页。

#### 4.1 时钟为什么"晚几秒才跳"以及最终方案

一开始用 `AlarmManager` 每分钟发广播改文本，试过两种写法都踩坑：

- `setRepeating(RTC, 60s)`：Android 4.4+ 重复闹钟一律 inexact，被系统批量合并；
- `setExactAndAllowWhileIdle`：Android 12+ 要 `SCHEDULE_EXACT_ALARM`；
  只声明 `USE_EXACT_ALARM` 时，平台仍会把 alarm 降级成"窗口"，
  `dumpsys alarm` 实测 `window=+36s830ms` —— 这就是"整分钟过去三十多秒卡片才跳"的真凶。

**最终方案：时间不用广播刷，改用 `TextClock`。**

```xml
<TextClock
    android:format12Hour="h:mm"
    android:format24Hour="HH:mm"
    android:textSize="80sp" ... />
```

- `TextClock` 由桌面进程自己按系统时钟走时，**整分即跳、不需要任何闹钟、不需要任何权限**，也不存在 RemoteViews 重绘闪烁；
- `TextClock` 在 RemoteViews 允许的控件列表里，实测 ColorOS 16 可正常 inflate；
- 日期同样用 `TextClock`（`format…="M月d日  E"`），跨天/换时区/改语言由桌面进程自己刷新，App 端无需广播；
- 点击跳转：日期 → `MAIN + CATEGORY_APP_CALENDAR`；时间 → `queryIntentActivities` 找包名含
  `alarmclock/deskclock/.clock` 的时钟 App 直接启动（不硬编码厂商包名）。
  注意 Android 11+ 包可见性：未声明 `<queries>` 时 `resolveActivity` 会误判 null，本项目已声明。
- **升级后重刷**：App 升级时 `MY_PACKAGE_REPLACED` 会触发整块重刷，避免桌面一直用旧布局（旧 RemoteViews 缓存不刷新会出现"时间不动"）。

### 五、我们这张卡片的实现要点

- 透明背景（`@android:color/transparent`），文字直接浮在壁纸上，与原卡片一致；
- 日期 `M月d日  E`（17sp）与时间 `HH:mm`/`h:mm`（80sp）**都用 `TextClock` 自走**，跟随系统 12/24 小时制，App 端零刷新；
- 数字颜色统一 `#FFFFFF`，**没有**一加红 `#E62F2F`；
- 不显示天气；
- 轻微 `shadow` 保证浅色壁纸下的可读性；
- 参数全部集中在 `res/layout/card_white_clock.xml`，改字号/间距/颜色只需改这一个文件。

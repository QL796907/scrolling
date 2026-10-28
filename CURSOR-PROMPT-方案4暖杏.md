# 设计笔记：方案 4「暖杏双折」（已落地，勿再执行）

**状态：已完成。** 本文只保留视觉记录。应用已经是浅色暖杏主题，启动图标已经是奶油底上的圆润双上折。不要按本文重做换肤、替换图标，或补一套深色主题。

颜色、主题、图标以这些文件为准：

- 颜色：`app/src/main/res/values/colors.xml`
- 主题：`app/src/main/res/values/themes.xml`（`Theme.AutoSwipe`，父主题 `Theme.Material3.Light.NoActionBar`）
- 主题（API 27）：`app/src/main/res/values-v27/themes.xml`
- 主题（API 29）：`app/src/main/res/values-v29/themes.xml`（`android:forceDarkAllowed` 为 false，保持浅色）
- 自适应图标：`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- 前景：`app/src/main/res/drawable/ic_launcher_fg.xml`
- 背景：`app/src/main/res/drawable/ic_launcher_background.xml`（纯色 `@color/launcher_bg`）

`app/src/main/AndroidManifest.xml` 里 `android:icon` 与 `android:roundIcon` 都是 `@mipmap/ic_launcher`，`android:theme` 是 `@style/Theme.AutoSwipe`。

## 验收（已核对）

- [x] 默认主题为浅色暖杏：`Theme.Material3.Light`，页面背景 `@color/bg`（`#FFF6EE`）
- [x] 主色为柔珊瑚 `@color/accent`（`#FF8F66`），按钮与主开关走这套颜色
- [x] 图标：奶油底 `@color/launcher_bg`（`#FFF0E6`）+ 圆角双上 chevron（`#FF8F66`），图形落在自适应图标中心安全区
- [x] 主界面、预设页、更新对话框使用 `Theme.AutoSwipe` 与上述颜色
- [x] 图标内无文字，也无短视频品牌元素
- [x] 正文 `@color/ink`（`#2A211C`）配在浅底上

下文是对照用的视觉说明。`colors.xml` 里没有单独的按下色、成功浅底、占位灰或阴影色；不要为了补齐本文去新增资源。

---

## 气质

暖杏、奶油纸、圆润、友好、轻量工具、克制趣味。  
避开深黑仪表盘、霓虹、赛博、玻璃拟物堆砌、短视频品牌元素、冷灰科技感。

本机工具：点「开」后按随机间隔自动向上滑动，用来刷短视频。安静、好认、好用。

## 色彩（与 `colors.xml` 一致）

| 资源名 | Hex | 用途 |
|--------|------|------|
| `bg` | `#FFF6EE` | 页面大背景（暖奶油） |
| `bg_elevated` | `#FFFFFF` | 卡片/底栏抬升面 |
| `bg_soft` | `#FFE8D6` | 次级区块、选中浅底 |
| `ink` | `#2A211C` | 主文字 |
| `ink_muted` | `#8A7A70` | 次级文字、说明 |
| `accent` | `#FF8F66` | 主强调（柔珊瑚） |
| `accent_soft` | `#FFE0D2` | 强调浅底、Chip |
| `on_accent` | `#FFF6EE` | 强调色上的字 |
| `success` | `#3CB98A` | 运行中/成功 |
| `danger` | `#E85D4C` | 停止/危险 |
| `warn` | `#C9892C` | 警告 |
| `stroke` | `#F0E2D6` | 分割线、描边 |
| `launcher_bg` | `#FFF0E6` | 启动图标背景 |

## 形状与字体

- 圆角偏大：主按钮在 `themes.xml` 的 `Widget.AutoSwipe.Button` 上是 `22dp`。
- 图标线端为圆角（`strokeLineCap` / `strokeLineJoin` = `round`）。
- 字体用系统无衬线，主按钮为 `sans-serif-medium`。
- 阴影保持轻，不叠大投影卡片。

## 图标

`ic_launcher_fg.xml` 是两条圆润的向上 chevron（双折），叠在一起表示「上滑」。端点圆润、线宽偏胖，没有细线、手机剪影或文字。背景是 `launcher_bg` 纯色。前景色 `#FF8F66`。自适应图标同时用于方形与圆形入口。

## 界面对照

主开关页、运行状态、间隔/预设、权限说明、对话框沿用同一套浅色。结构是暖奶油大背景、圆润主开关（开为珊瑚实心，关为浅底描边）、短句状态文案，次操作用浅底按钮。危险色只用于停止/撤销。

| 组件 | 现有做法 |
|------|----------|
| Primary Button | 底 `@color/accent`，字 `@color/on_accent`，圆角 22dp |
| Secondary | 底 `@color/bg_soft`，字 `@color/ink`（`Widget.AutoSwipe.Button.Secondary`） |
| Card / 表面 | `@color/bg_elevated`，描边 `@color/stroke` |
| 运行中 | `@color/success` |
| 危险停止 | `@color/danger` |

文案保持轻松克制，例如「已开启」「需要无障碍权限才能模拟上滑」。

## 不要做

- 不要按本文再改一版主题、颜色或图标
- 不要改回深色外壳，也不要另做夜间主题
- 不要做成社交、相机或短视频 App 的视觉
- 不要为补齐本笔记里未落地的色值去改 `colors.xml`

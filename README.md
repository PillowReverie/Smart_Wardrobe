# 智能衣橱与 AI 穿搭助手

- 当前版本：2.1.5
- 平台：Android 8.0（API 26）及以上
- 语言：Java
- 工程：`Wardrobe/`

一个基于本地衣橱、定位天气和大模型的 Android 穿搭助手。用户可以拍照或从相册添加衣物，按类别和关键词管理衣橱，获取当前位置与每日天气，并让兼容 OpenAI Chat Completions 的大模型生成今日穿搭或单品搭配。

## 主要功能

- 衣橱管理：添加、编辑、删除、分类筛选、名称/颜色模糊搜索。
- 图片处理：点击照片卡片弹窗选择拍照或相册，自由比例裁剪，支持四角与四边拖拽、双指缩放，压缩后保存到应用私有目录。
- 智能录入：视觉大模型识别衣物名称、类别和颜色。
- 天气定位：`LocationManager` 获取经纬度，和风天气每日预报与逆地理编码。
- AI 穿搭：今日穿搭、单品搭配、三套方案、自定义场景、图文并排展示。
- 异地游玩：大模型解析城市与相对日期，按完整 7 天天气预报生成分类打包清单。
- 旅行衣橱：打包结果按衣物图片逐件加入或移出，支持旅行中仅用携带衣物生成当天穿搭。
- 经典套装：优先复用搭配库中适合当天天气的套装，不适合时再从单品重新组合。
- 性别偏好：女性、男性、不限，首次启动选择，可在设置中修改。
- 搭配库：保存搭配正文、天气快照和关联衣物，展示衣物图片，支持删除和重复保存拦截。
- 设置：天气与大模型配置、浅色/深色/跟随系统、AI 衣物资料补全、数据迁移。
- 稳定性：异步请求、Activity 销毁取消 Call、按钮防连点、断网拦截、缺图占位、跨天缓存清理。

## 技术栈

| 模块 | 实现 |
| --- | --- |
| UI | Material Components 3、Activity、RecyclerView |
| 本地数据 | 原生 SQLite（`SQLiteOpenHelper`） |
| 网络 | Retrofit + Gson + OkHttp |
| 图片 | Glide、系统相机/相册、私有目录 |
| 定位 | `LocationManager` |
| 天气 | 和风天气 Web API |
| 大模型 | OpenAI Chat Completions 兼容接口 |

核心数据流：定位并获取天气 → 读取衣橱和搭配库 → `PromptBuilder` 拼接 Prompt → `LLMService` 异步请求 → `AiOutfitParser` 解析并匹配衣物图片 → 页面展示或写入搭配库。

## 架构

项目采用 Activity + ViewModel + Repository 的分层结构：

```text
Activity（只渲染 UiState）
    ↓ 用户操作
ViewModel（状态分发与页面编排）
    ↓
WardrobeRepository / WeatherRepository / OutfitRepository
    ↓
SQLite / LocationManager / 和风天气 / LLMService
```

- 首页通过唯一的 `MainUiState` 驱动天气、AI、旅行清单和统计信息渲染。
- 单品详情页通过 `ItemDetailUiState` 与 `ItemDetailViewModel` 隔离网络和数据库逻辑。
- Repository 负责数据读取、缓存、Prompt 拼接、网络请求与结果解析。
- Activity 不直接调用 DAO、WeatherManager、PromptBuilder 或 LLMService。

## 项目结构

```text
Project-005-SmartAIWardrobe/
├─ Wardrobe/          Android Studio 工程
├─ outputs/           v2.1.2 Beta / Release 安装包
├─ docs/              使用说明
├─ work/              临时目录（当前为空）
├─ README.md
└─ CHANGELOG.md
```

## 数据库

数据库文件为 `wardrobe.db`，使用三张表：

- `wardrobe_items`：衣物名称、类别、图片文件名、颜色、备注、AI 隐藏描述、旅行衣橱标记、时间戳。
- `outfits`：搭配标题、正文、来源、天气快照、温度、创建时间。
- `outfit_items`：搭配与衣物的多对多关联，保存 `outfit_id`、`item_id` 和排序。

图片只在数据库中保存文件名，读取时通过 `ImageStore` 拼接当前应用私有目录，兼容覆盖安装和换机迁移。

## API 配置

源码不包含真实 API Key。首次运行后进入“设置”填写：

- 和风天气 Host
- 和风天气 Key
- LLM Base URL
- LLM API Key
- LLM Model Name

大模型请求使用 `Authorization: Bearer <Key>` 和标准 `messages` 数组。DeepSeek 官方接口会自动发送 `thinking: {"type":"disabled"}`，避免 `deepseek-flash` 等模型的思考过程耗尽输出额度；其他兼容厂商不会收到该参数。

## 编译运行

环境要求：Android Studio 或 Android SDK、JDK 11 或 Android Studio 自带 JBR。

```powershell
cd D:\Workspace\Projects\Project-005-SmartAIWardrobe\Wardrobe
$env:JAVA_HOME = "D:\AndroidTool\Android studio\jbr"
$env:GRADLE_USER_HOME = "C:\Users\wangkang\.gradle"
.\gradlew.bat assembleDebug --console=plain
```

Android Studio 直接打开：

```text
D:\Workspace\Projects\Project-005-SmartAIWardrobe\Wardrobe
```

运行单元测试并生成 Beta 与 Release 双包：

```powershell
.\gradlew.bat testDebugUnitTest packageDual --console=plain
```

## 安装包

- `outputs/SmartWardrobe-v2.1.5-release.apk`：正式版，包名 `com.example.wardrobe`
- `outputs/SmartWardrobe-v2.1.5-beta.final.apk`：最终测试版，包名 `com.example.wardrobe.beta`

Release SHA-256：

```text
e7ddbca539bad46df943d18464b60fc8d116175e1a9631e7a74367ae57543143
```

Beta SHA-256：

```text
7717f28e9363d7b006b61e3d087cbb584276b2751f218152fea8a31d7611b84b
```

正式版使用固定 Release 签名，可直接覆盖安装旧正式版并保留衣橱、搭配、图片和 API 配置。

```powershell
adb install -r outputs\SmartWardrobe-v2.1.5-release.apk
```

需要对照测试时也可以安装 Beta：

```powershell
adb install -r outputs\SmartWardrobe-v2.1.5-beta.final.apk
```

## 验收要点

- 衣橱为空、未配置 Key 或断网时，点击生成应直接提示，不发起无效请求。
- 请求期间按钮禁用，连续点击不会产生并发请求。
- AI 方案可展示 1～3 套，衣物图片缺失时显示占位图。
- `deepseek-flash` 能正常返回正文，日志不应出现 `finishReason=length`。
- 保存搭配后，`outfit_items` 中应存在对应衣物 ID。
- 覆盖安装 v2.1.5 后，旧版本衣橱、搭配、图片和 API 配置仍然保留。
- 旅行衣橱通过长按衣物或打包卡按钮加入/移出；清空只重置旅行标记。
- 打包清单正文不再重复衣物图片区内容，洗护与证件以单行紧凑展示。
- 点击照片卡片应弹出“拍照 / 从相册选择”底部弹窗。
- 选图后应进入自由比例裁剪页，四角和四边都能拖动；取消裁剪不影响原预览。
- 裁剪初始框应贴合整张图片，双指缩放时图片不应旋转。
- 搭配库卡片可删除；同一方案重复保存时应提示已存在。
- 裁剪页底部不显示“缩放”页签和百分比滚轮，双指捏合可正常缩放。
- 裁剪页标题位于状态栏下方，不与时间、电量重叠。
- 配置 `deepseek-flash` 后，AI 智能识别应自动填充名称、类别和颜色。
- 桌面启动图标应与 1.4.x 一致，不应出现第三方库自带的图标。

## 版本规则

版本号遵循 Semantic Versioning：新增功能升 `MINOR`，兼容性修复升 `PATCH`，不兼容变更升 `MAJOR`。每次更新同时提供 Beta 与 Release 两个安装包，版本号统一在 `Wardrobe/gradle.properties` 中维护。

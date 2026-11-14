# 智能衣橱 v2.2.0

本版本将天气能力升级为双源架构：默认使用免费的 Open-Meteo，填写和风天气
Host/Key 后自动优先使用和风天气。

## 主要更新

- 未配置天气 Key 也可直接获取首页天气和旅行目的地预报。
- 和风天气配置保留原有 SharedPreferences 键名，老用户覆盖升级后继续生效。
- Open-Meteo 使用 WMO 天气代码，已映射为中文天气描述。
- 首页地名使用 Android 原生 Geocoder，解析失败显示“当前位置”。
- 异地游玩支持中文城市名自动转拼音查询，如“咸阳” → `Xianyang`。
- 找不到城市时提示输入拼音或英文名，不再发出无效请求。
- 设置页天气配置改为“和风天气（选填）”，状态区显示当前天气源。
- 切换或清空天气配置会清理旧天气缓存，避免混用两个来源的数据。

## 兼容性

- 包名仍为 `com.example.wardrobe`，可覆盖安装 2.1.x 正式版。
- 衣橱、搭配和图片保存在本地 SQLite/私有目录，升级不会清空。
- 和风天气的 `weather_host` 与 `weather_api_key` 键名未改变。

## 安装包

- 普通使用：`SmartWardrobe-v2.2.0-release.apk`
- 查看 AI 隐藏描述：`SmartWardrobe-v2.2.0-beta.final.apk`

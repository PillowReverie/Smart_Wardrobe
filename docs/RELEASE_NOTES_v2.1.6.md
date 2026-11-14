# 智能衣橱 v2.1.6

这是 MVVM + Repository 重构后的稳定版本。

## 主要更新

- 首页、衣橱与单品详情采用 ViewModel + UiState 驱动渲染。
- 天气、穿搭和旅行逻辑下沉至 Repository，页面更稳定。
- 修复灵感焕新、经典套装与异地游玩之间切换时的内容闪烁。
- 天气缓存持久化，当天 AI 结果按日期、模式、场景和偏好缓存。
- 保留经典套装优先推荐、旅行衣橱、深色模式和衣物搜索。

## 安装

- 普通使用：`SmartWardrobe-v2.1.6-release.apk`
- 查看 AI 隐藏描述：`SmartWardrobe-v2.1.6-beta.final.apk`

正式版 `com.example.wardrobe` 可使用同一签名覆盖升级；Beta 包名独立，可与正式版共存。

## 校验

Release SHA-256：

```text
b805ff52fad3a31e72d44dd56b7a1f54ac141077bf9f44f4b490e7c7d6dc9c23
```

Beta SHA-256：

```text
06906d603cfbc727859206b9e38a595e5722fae713b9ec83ff4f2759a5ea4b76
```

## 说明

应用不包含真实 API Key。首次使用请在设置中填写和风天气 Host/Key 与大模型配置。

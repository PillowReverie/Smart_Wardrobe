# 安全策略

## 支持范围

安全修复面向默认分支的最新正式版本。旧版本可能不再获得修复。

## 报告漏洞

请不要在公开 Issue 中披露以下内容：

- API Key、访问令牌或账号凭据。
- Release 签名文件、证书或密码。
- 可复现的严重漏洞细节。
- 包含真实位置、照片或衣橱数据的日志。

优先使用 GitHub 仓库 Security 页面的私密漏洞报告功能。如果仓库未启用该功能，请通过维护者公开资料中的联系方式私下联系，并只提供必要的最小复现信息。

## 凭据管理

本仓库不应包含任何真实 API Key。以下文件仅限本地保存，已被 `.gitignore` 排除：

```text
Wardrobe/keystore.properties
Wardrobe/signing/*.jks
Wardrobe/signing/*.keystore
Wardrobe/local.properties
```

如果曾经提交过凭据，即使后续删除文件，凭据仍存在于 Git 历史中。应立即在服务商后台吊销并重新生成，而不是只修改最新提交。

## Release 签名

正式版必须始终使用同一签名密钥，才能覆盖升级并保留本地数据。签名密钥和密码应加密备份，不要上传到 GitHub、CI 日志或公开网盘。

CI 只执行单元测试与 Debug 构建，不持有正式签名密钥。正式 APK 应由可信的本地环境构建后手动上传到 GitHub Releases。

## 第三方依赖

项目使用 AndroidX、Material Components、Retrofit、OkHttp、Glide 与 UCrop 等开源依赖。发布前建议关注依赖安全公告并定期更新。

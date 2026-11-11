// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}

val packageVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val packageBetaSequence = providers.gradleProperty("APP_BETA_SEQUENCE")
        .getOrElse("1")

// 每次发布固定产出 Beta 与 Release 两个独立安装包，避免手工复制时漏包。
// 使用标准 Copy 任务而不是在 doLast 中调用项目方法，以兼容 Gradle 配置缓存。
// 路径与重命名规则都在配置阶段转换成普通字符串/File，避免配置缓存序列化脚本闭包。
val publishBetaApk = tasks.register<Copy>("publishBetaApk") {
    dependsOn(":app:assembleBeta")
    from(layout.projectDirectory.file(
            "app/build/outputs/apk/beta/app-beta.apk").asFile)
    into(layout.projectDirectory.dir("../outputs").asFile)
    rename(
        "app-beta\\.apk",
        "SmartWardrobe-v$packageVersionName-beta.$packageBetaSequence.apk"
    )
}

val publishReleaseApk = tasks.register<Copy>("publishReleaseApk") {
    dependsOn(":app:assembleRelease")
    from(layout.projectDirectory.file(
            "app/build/outputs/apk/release/app-release.apk").asFile)
    into(layout.projectDirectory.dir("../outputs").asFile)
    rename(
        "app-release\\.apk",
        "SmartWardrobe-v$packageVersionName-release.apk"
    )
}

tasks.register("packageDual") {
    group = "distribution"
    description = "Test, build and publish both Beta and Release APKs."
    dependsOn(
            ":app:testDebugUnitTest",
            publishBetaApk,
            publishReleaseApk
    )
}

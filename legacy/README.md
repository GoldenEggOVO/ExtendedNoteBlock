# 上游历史代码归档

这里保留上游 Minecraft **1.20.1 / 1.21.1** 的版本专用源码、依赖配置和旧发布脚本，供追溯与移植参考。归档时保留了原文件内容。

| 路径 | 原位置 | 用途 |
| --- | --- | --- |
| `versions/` | 仓库根目录 `versions/` | 两个旧版本的专用源码与资源 |
| `gradle/versions/` | `gradle/versions/` | 旧版依赖配置 |
| `gradle/active-version.properties` | 同名 Gradle 配置 | 旧版版本切换状态 |
| `build_and_release.py` | 仓库根目录 | 上游双版本构建与发布脚本 |

这些文件不参与 Minecraft 26.2 的构建。当前 Gradle 配置没有旧版 `switchTo1201`、`switchTo1211` 等任务；归档发布脚本也不是本分支的可用发布入口。

26.2 的构建步骤见[开发指南](../docs/DEVELOPMENT_zh-cn.md)，实际 CI 定义见[构建工作流](../.github/workflows/build-26.2.yml)。

归档继续遵循项目的 [MIT 许可证](../LICENSE)，保留原作者版权与署名。

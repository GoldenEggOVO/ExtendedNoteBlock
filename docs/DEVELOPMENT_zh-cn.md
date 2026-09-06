# 开发指南 · Minecraft 26.2

[返回首页](../README.md) · [文档中心](README.md) · [English](DEVELOPMENT.md) · [日本語](DEVELOPMENT_ja-jp.md)

当前分支只构建 Minecraft **26.2**，产出 Full Fabric、Paper Client、Paper Server 三个程序版本，以及自动下发的物品 + 声音资源包。Full Fabric 与 Paper Client 不可同时装在客户端。

## 环境与目录

| 项目 | 当前构建基线 |
| --- | --- |
| Java JDK | 25 |
| Gradle Wrapper | 9.5.1 |
| Fabric Loom | 1.17.20 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.159.0+26.2 |
| Paper API | 26.2.build.119-stable |
| 辅助脚本 | Python 3 |

版本配置见 [gradle.properties](../gradle.properties)、[Gradle Wrapper](../gradle/wrapper/gradle-wrapper.properties) 和 [bridge/build.gradle](../bridge/build.gradle)。Paper API 固定为 `26.2.build.119-stable`，确保相同源码使用同一套 API 基线。

| 目录 | 职责 |
| --- | --- |
| `src/main/` | Full Fabric 内容、共享音乐逻辑与资源 |
| `src/client/` | 界面、声音、Paper 客户端伴侣代码 |
| `src/test/` | 现有自动化测试 |
| `bridge/` | 独立 Paper / Purpur 插件工程 |
| `scripts/` | 源码准备和打包脚本 |
| `.github/workflows/` | CI 与发布定义 |
| `docs/` | 安装、架构、开发、待办和版本说明 |
| `legacy/` | 不参与构建的上游 1.20.1 / 1.21.1 历史文件 |

当前 Gradle 不再提供旧版切换任务。历史文件的用途见 [legacy/README.md](../legacy/README.md)。

## 构建前

当前源码准备脚本会修改已跟踪的 Java / 资源文件。需要保持开发目录干净时，可在独立 worktree 中构建；以下示例构建已提交的 `port/26.2`，不会包含原目录的未提交修改：

```bash
git worktree add --detach ../enb-build port/26.2
cd ../enb-build
```

准备脚本是现有发布流程的一部分，不要省略，也不要把脚本生成的全部改动未经检查就提交回开发分支。

## Full Fabric、Paper Client 与资源包

在仓库根目录，使用 JDK 25：

```bash
python3 scripts/prepare_26_2_sources.py
chmod +x gradlew
./gradlew clean test build --stacktrace
python3 scripts/make_paper_bridge_client_jar.py
python3 scripts/make_server_resource_pack.py
```

Paper Client 从 Full 构建输出中按严格白名单提取客户端类，并内置物品模型资源包。不能直接重命名 Full JAR 来代替 Paper Client。

## Paper Server

在仓库根目录依次执行：

```bash
python3 scripts/prepare_paper_custom_model_data.py
python3 scripts/prepare_paper_interactions.py
python3 scripts/prepare_paper_render_sync.py
python3 scripts/prepare_paper_listener_pack.py
./gradlew -p bridge clean build --stacktrace
```

最后一个准备脚本还会调用 `prepare_paper_command_help.py`。直接编译未经准备的插件源码，不能复现当前 Release。

Windows 下可将 `python3` 换成指向 Python 3 的 `python`，将 `./gradlew` 换成 `.\gradlew.bat`，并跳过 `chmod`。

| 输出目录 | 产物 |
| --- | --- |
| `build/libs/` | Full Fabric 运行 JAR 与 sources JAR |
| `build/paper-bridge-client/` | Paper Client JAR |
| `build/server-resource-pack/` | 自动下发的物品材质 + 聆听音色组合 ZIP |
| `bridge/build/libs/` | Paper Server JAR |

组合资源包需要 JDK 25 与 FFmpeg。脚本会核对 GeneralUser GS SoundFont 的固定 SHA-256，渲染并归一化 751 个实际采样，以 OGG quality 4 编码并逐个解码排除过轻输出；源 `.sf2` 不会进入 ZIP。构建还会强制资源包小于 50,000,000 bytes。CI 发布时将最终资源包 URL 与 SHA-1 同时写入 `config.yml` 和仅随 JAR 分发的 `enb-release-pack.properties`，防止旧服务器配置覆盖正式升级，最后统一生成 `SHA256SUMS.txt`。

## 分支与发布

| Ref | 用途 |
| --- | --- |
| `port/26.2` | 主要开发分支 |
| `main` | 仓库展示分支，在确定的检查点同步 |
| `release/26.2` | 发布维护分支，在确定的检查点同步 |
| `v<模组版本>-mc26.2` | 对应正式产物的精确源码提交 |

当前[工作流](../.github/workflows/build-26.2.yml)会在 `port/26.2` / `release/26.2` 的 push、目标为 `main` 的 PR 和手动运行时执行文档质量检查。代码变化与 `release:` 提交运行 Full / Client / Server 完整构建；仅文档变化会跳过耗时构建，同步到 `release/26.2` 时也只运行质量门。只有推送到 `port/26.2`、最新提交信息以 `release:` 开头、两个构建任务都成功，才执行发布任务。

文档和整理使用 `docs:` / `chore:` 提交。分支可以在发布后继续前进；正式 Tag 保持指向产物实际使用的提交。Full / Client / Server Resources 使用 `gradle.properties` 中的 `mod_version`；Paper Server 使用 `bridge/build.gradle` 中的独立版本号。

## 验证范围

CI 验证现有测试、Full 运行内容、Paper Client 的 Registry 安全与资源、插件源码注入和运行资源。2.8.1 起，CI 还会实际启动打包后的 Paper Client；该结果不等于 Purpur 多人游戏测试或真人试听。

游戏验证与功能待办见 [ROADMAP.md](ROADMAP.md)。整理或复用源码时保留 MIT 许可证与原作者署名。

### Paper Client 启动回归测试

构建并运行 Paper Client 打包脚本后，执行 `./gradlew runPaperClientSmoke`。Linux 需要 Xvfb 和可用的 OpenGL 驱动，CI 使用软件渲染。测试使用独立的 `src/paperClientSmoke/` 模块，等待资源加载后检查内置物品包是否启用、原版载体物品选择器、音符盒 GUI、原版 Block/Item Registry、MIDI 0–127 的 SoundEngine pitch，以及原版声音行为。测试模块不会进入正式 JAR。

文档链接、版本号、图片引用与第三方声明检查：`python3 scripts/check_documentation.py`。完整 Python 回归检查：`python3 -m unittest discover -s scripts -p 'test_*.py' -v`。Paper 保存数据测试：`./gradlew -p bridge test`。Release 工作流检查启动成功标记，并从 `docs/releases/<mod_version>.md` 读取当前版本说明。

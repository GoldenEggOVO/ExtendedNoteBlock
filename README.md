# Extended Note Block

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.1-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.2-DBD0B4?style=flat-square)](https://fabricmc.net/)
[![License](https://img.shields.io/github/license/BianFuuuu/ExtendedNoteBlock?style=flat-square)](LICENSE)

![Extended Note Block Banner](./docs/assets/ENB-Banner.png)

Extended Note Block 是一个面向 Minecraft Java Edition 的 Fabric 音乐模组。它扩展了音符盒的音域、力度、延音和空间控制，并提供 NBS、MIDI、音频导入以及多种音乐结构导出方式。

本仓库 fork 自 [Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock)。原作者为 **Atemukesu**，Minecraft 26.1.1 移植与音乐工坊扩展由 **BF_skt** 继续维护。原作者署名和 MIT 许可证均完整保留。

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.1.1 |
| Fabric Loader | 0.19.2 或更高 |
| Fabric API | 0.145.4+26.1.1 或更高 |
| Java | 25 或更高 |

## 主要功能

### 扩展音符盒

- MIDI 0-127 全音域，不再受原版两八度限制。
- 可设置力度、延音、播放延迟、淡入与淡出。
- 支持音量曲线、弯音曲线和基于表达式的声源移动。
- 指挥棒可框选区域并批量编辑扩展音符盒。

### 红石与投影播放

- 全局红石发送器和接收器。
- NBS 投影使用一对一专用接收器，避免激活其他投影。
- 长距离音乐由播放管理器集中播放，不受远端音符约 48 格的声音衰减影响。
- 暂停游戏后会保持音乐时间轴同步。

### NBS 音乐工坊

- 直接读取 `.nbs` 文件。
- 导入 `.mid` 和 `.midi`，保留速度、力度、声像与 GM 乐器信息。
- 拖入 WAV、MP3、OGG、AIFF 或 AU 音频，自动分析音高并转换为 NBS。
- 提供歌曲搜索、试听、速度、移调、力度、延音、音域和自定义音色设置。
- 精细预览投影中的每个方块，支持旋转、缩放和平移。

### 原版音乐导出

- **红石线路**：用拉杆、中继器、红石粉和原版音符盒构成可工作的实体线路。
- **直轨结构**：使用矿车、探测铁轨和动力铁轨按时间触发音符盒。
- **结构格式**：支持 Litematica `.litematic` 和原版结构方块 `.nbt`。
- **数据包**：生成带播放、停止、加入和离开函数的 ZIP 数据包。
- 可配置单双侧和弦分布、时间精度、音乐速度、轨道速度、命令方块、矿车、循环、共享播放、线路方块及 16 种原版音色支撑方块。
- 支持 NBS Tempo Changer、开头留白、MIDI GM 音色映射与大型和弦。

## 安装

1. 安装与 Minecraft 26.1.1 匹配的 [Fabric Loader](https://fabricmc.net/) 和 [Fabric API](https://modrinth.com/mod/fabric-api)。
2. 从本仓库 Releases 下载 JAR。
3. 将 JAR 放入游戏实例的 `mods` 目录。

不要同时放入多个 Extended Note Block 版本，否则 Fabric 会报告重复模组 ID。

## 音乐工坊使用

在按键设置中绑定“打开 NBS 音乐工坊”，或把歌曲放入：

```text
.minecraft/extendednoteblock/songs/
```

无线投影和原版结构默认输出到：

```text
.minecraft/schematics/extendednoteblock/
```

数据包默认输出到：

```text
.minecraft/extendednoteblock/datapacks/
```

将数据包 ZIP 放进目标存档的 `datapacks` 目录后执行：

```mcfunction
/reload
/function extendednoteblock_music:play
/function extendednoteblock_music:stop
```

如果修改了导出设置中的命名空间，请相应替换命令里的 `extendednoteblock_music`。ZIP 内的 `README.txt` 会列出实际命令。

## 从源码构建

Windows：

```powershell
.\gradlew.bat clean test build
```

Linux 或 macOS：

```bash
./gradlew clean test build
```

构建结果位于 `build/libs/`。由于 Windows 下 Java/Gradle 对部分中文路径的参数文件编码存在兼容问题，运行测试时建议把仓库放在纯英文路径。

## 项目来源与作者

- 原项目与原作者：[Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock) - **Atemukesu**
- 26.1.1 移植与扩展维护：**BF_skt**（GitHub: [BianFuuuu](https://github.com/BianFuuuu)）
- 原版详细手册：[atemukesu.github.io/ExtendedNoteBlock](https://atemukesu.github.io/ExtendedNoteBlock/)

## 许可证

本项目继续使用 [MIT License](LICENSE)。原作者的版权声明未被删除；使用、修改或分发时请保留许可证与版权信息。

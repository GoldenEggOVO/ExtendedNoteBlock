# 開発ガイド · Minecraft 26.2

[ホーム（简体中文）](../README.md) · [ドキュメント一覧](README.md) · [English](DEVELOPMENT.md) · [简体中文](DEVELOPMENT_zh-cn.md)

このブランチの対象は Minecraft **26.2** です。JDK **25**、Gradle Wrapper **9.5.1**、Loom **1.17.20**、Fabric Loader **0.19.5**、Fabric API **0.159.0+26.2**、Paper API **26.2.build.119-stable**、Python 3 を使用します。Paper API は再現可能なビルドのため固定されています。

`src/` は Fabric と共有機能、`bridge/` は Paper / Purpur プラグイン、`scripts/` はソース準備とパッケージングを担当します。旧 1.20.1 / 1.21.1 のファイルは [legacy/](../legacy/README.md) に保存され、現在のビルドには含まれません。

## Full Fabric / Paper Client / リソースパック

リポジトリのルートで実行します。準備スクリプトは追跡対象ファイルを書き換えるため、ビルド専用のチェックアウトや worktree を使用してください。

```bash
python3 scripts/prepare_26_2_sources.py
chmod +x gradlew
./gradlew clean test build --stacktrace
python3 scripts/make_paper_bridge_client_jar.py
python3 scripts/make_server_resource_pack.py
```

Paper Client は Full のビルド結果から許可されたクラスのみを抽出します。Full JAR の名前を変更して代用することはできません。

## Paper Server

```bash
python3 scripts/prepare_paper_custom_model_data.py
python3 scripts/prepare_paper_interactions.py
python3 scripts/prepare_paper_render_sync.py
python3 scripts/prepare_paper_listener_pack.py
python3 scripts/prepare_paper_craftengine.py
python3 scripts/prepare_paper_schematic.py
./gradlew -p bridge clean build --stacktrace
```

`prepare_paper_render_sync.py` はコマンドヘルプの準備も行います。Windows では `python3` を Python 3 の `python`、`./gradlew` を `.\gradlew.bat` に置き換え、`chmod` を省略します。

| 出力 | 内容 |
| --- | --- |
| `build/libs/` | Full Fabric と sources JAR |
| `build/paper-bridge-client/` | Paper Client |
| `build/server-resource-pack/` | 自動配信用のアイテム・試聴リソースパック |
| `bridge/build/libs/` | Paper Server |

Server Resources のビルドは 751 個のサンプルをピーク正規化し、Vorbis quality 4 でエンコードして各 OGG を再デコードします。50,000,000 bytes 以上のパックと実質的に無音の出力は拒否されます。Release は URL と SHA-1 を `config.yml` と JAR 内の `enb-release-pack.properties` の両方へ書き込み、古いサーバー設定が公式アップデートを上書きしないようにします。

長期ブランチは `main` のみです。機能・修正は一時ブランチで開発し、`main` にマージして未マージのコミットがないことを確認してから削除します。公開タグは成果物を生成したコミットに固定します。CI は `main` への push、`main` 向け PR、手動実行で動作します。コード変更と `release:` コミットでは完全なビルドを実行し、文書のみの変更では省略します。`main` への push の最新コミットメッセージが `release:` で始まり、ビルドが成功した場合のみ公開します。通常のマージや手動実行はリリースを公開しません。公開前にバージョンとリリースノートを更新してください。

詳細は [English の開発ガイド](DEVELOPMENT.md) と [CI 定義](../.github/workflows/build-26.2.yml) を参照してください。CI の成功は実際のゲーム内検証を意味しません。

`python3 scripts/check_documentation.py` はローカルリンク、見出し、画像参照、現在のバージョン、Release 記事索引、第三者表記を確認します。Paper Client のパッケージ作成後、`./gradlew runPaperClientSmoke` で実際の Fabric クライアント起動、リソース読み込み、GUI、音程を検証できます。Linux では Xvfb が必要です。テスト用 Mod はリリース JAR に含まれません。Purpur マルチプレイと実際の音は別途確認が必要です。

## 2.13.0 构建补充

Paper Server 0.14.0 使用公开 Maven 的 CraftEngine core/bukkit 26.8.2 API（运行时不内嵌），必须在原有 Paper 准备步骤后依次运行 `prepare_paper_craftengine.py`、`prepare_paper_schematic.py`。生成源码只用于构建；权威集成源在 `craftengine/integration/` 与 `scripts/templates/`。

客户端编译兼容 Litematica 0.28.8 / MaLiLib 0.29.6，运行时可选。发布检查包含无 Litematica 与有 Litematica 两次 Paper Client 启动。CraftEngine 安装资源由 `scripts/make_craftengine_pack.py --resource-pack <server-resources.zip> --output <output.zip> --version 2.13.0` 生成；其中不含 CraftEngine 插件二进制或服务器私有配置。

# 開発ガイド · Minecraft 26.2

[ホーム（简体中文）](../README.md) · [ドキュメント一覧](README.md) · [English](DEVELOPMENT.md) · [简体中文](DEVELOPMENT_zh-cn.md)

このブランチの対象は Minecraft **26.2** です。JDK **25**、Gradle Wrapper **9.5.1**、Loom **1.17.20**、Fabric Loader **0.19.5**、Fabric API **0.159.0+26.2**、Python 3 を使用します。

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

開発ブランチは `port/26.2` です。`main` と `release/26.2` は確認済みのチェックポイントで同期し、公開済みのタグは成果物を作成したコミットに固定します。現在の CI は `port/26.2` への push で最新コミットメッセージが `release:` から始まる場合に、ビルド成功後のリリース処理を実行します。

詳細は [English の開発ガイド](DEVELOPMENT.md) と [CI 定義](../.github/workflows/build-26.2.yml) を参照してください。CI の成功は実際のゲーム内検証を意味しません。

Paper Client のパッケージ作成後、`./gradlew runPaperClientSmoke` で実際の Fabric クライアント起動、リソース読み込み、GUI、音程を検証できます。Linux では Xvfb が必要です。テスト用 Mod はリリース JAR に含まれません。Purpur マルチプレイと実際の音は別途確認が必要です。

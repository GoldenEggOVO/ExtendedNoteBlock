# Extended Note Block · Minecraft 26.2

[简体中文](../README.md) · **日本語** · [ドキュメント一覧](README.md) · [開発ガイド](DEVELOPMENT_ja-jp.md)

![Extended Note Block Banner](assets/ENB-Banner.png)

Extended Note Block は、MIDI 0–127 に対応する拡張音符ブロック、指揮棒、ワイヤレスレッドストーン、NBS 音楽ワークショップを Minecraft に追加します。NBS / MIDI / 一般的な音声ファイルの読み込み、試聴、音楽構造の書き出しにも対応します。

現在のリリースは **Full Fabric / Paper Client 2.12.0**、**Paper Server 0.12.0** です。Minecraft **26.2** と Java **25** を対象としています。

[2.12.0 をダウンロード](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.12.0-mc26.2) · [機能紹介](FEATURES.md) · [インストールガイド（中文）](INSTALLATION.md)

## エディションの選択

| 用途 | 導入するもの |
| --- | --- |
| シングルプレイ | Full Fabric + Fabric API |
| Fabric サーバー | サーバーとクライアントに Full Fabric + Fabric API |
| Paper / Purpur サーバー | サーバーに Paper Server プラグイン |
| Paper のバニラクライアント | Mod 不要。サーバーリソースパックを読み込む |
| Paper の編集用クライアント | Paper Client + Fabric API |

> [!IMPORTANT]
> **Full Fabric と Paper Client を同じクライアントに同時導入しないでください。**

## Paper / Purpur

Paper Server は Note Block や Concrete などのバニラブロックをキャリアとして使用します。

- Mod なしのプレイヤーは、サーバーリソースパックで ENB の音楽とアイテム外観を利用できます。
- ワールド内のブロックはバニラの外観を保ち、偽ブロックや Display Entity は使用しません。
- Paper Client を導入したプレイヤーは、編集 GUI、128 種類の音色、位置ごとの完全なブロックモデル、高度な音声制御を利用できます。
- 貼り付けた Paper Projection は、NBS Workshop の **Restore ENB** から元の `.litematic` メタデータを復元できます。

デフォルトキー **N** で NBS Workshop を開きます。Paper の OP は `/enb give all` でテスト用アイテムを取得できます。

## ドキュメント

- [ドキュメント一覧](README.md)
- [機能紹介とスクリーンショット](FEATURES.md)
- [インストール・コマンド・トラブルシューティング（中文）](INSTALLATION.md)
- [Paper アーキテクチャ（中文）](ARCHITECTURE.md)
- [開発ガイド](DEVELOPMENT_ja-jp.md)
- [2.12.0 リリースノート（中文）](releases/2.12.0.md)

## Credits / License

Original project: [Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock). The 26.1.1 port and Workshop expansion were developed by [BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock). Minecraft 26.2 and Paper Bridge are maintained by **GoldenEggOVO**.

This project remains available under the [MIT License](../LICENSE) with the original copyright notices preserved.

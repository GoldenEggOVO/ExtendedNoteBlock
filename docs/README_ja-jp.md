# Extended Note Block · Minecraft 26.2

[简体中文](../README.md) · **日本語** · [開発ガイド](DEVELOPMENT_ja-jp.md)

![Extended Note Block Banner](assets/ENB-Banner.png)

Extended Note Block は、拡張音符ブロック、指揮棒、ワイヤレスレッドストーン、NBS / MIDI / 音声ファイルの読み込みと音楽構造の書き出しを提供します。

現在のリリースは Full Fabric / Paper Client **2.10.0**、Paper Server **0.10.0** です。[ダウンロード](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.10.0-mc26.2)

## エディションの選択

**Full Fabric と Paper Client を同じクライアントに同時導入しないでください。**

| エディション | 用途 | 導入先 |
| --- | --- | --- |
| Full Fabric | シングルプレイ / 同じ Mod を導入した Fabric サーバー | `mods/` |
| Paper Client | ENB Bridge を導入した Paper / Purpur への接続 | Fabric クライアントの `mods/` |
| Paper Server | Paper / Purpur サーバープラグイン | サーバーの `plugins/` |
| Visuals | 任意のアイテム用リソースパック | `resourcepacks/` |
| Server Resources | バニラクライアント向けの表示・試聴用パック | 接続時に自動配信 |

対象は Minecraft **26.2**、Java **25**。Fabric 側の構築基準は Loader **0.19.5** と Fabric API **0.159.0+26.2** です。Fabric サーバーで遊ぶ場合はサーバーにも Full Fabric と Fabric API を導入します。

Paper Client は Visuals と同じリソースを内蔵しています。2.10.0 では Mod を導入しないプレイヤーも、接続時に約 18 MiB のサーバーリソースパックを受け入れるだけで MIDI 0–127 の ENB 音楽を聞けます。配置済み ENB ブロックの座標別モデル表示には引き続き Paper Client が必要です。

## 使用方法と現在の制限

- デフォルトの **N** キーで NBS Workshop を開きます。
- Paper では OP が `/enb give all` で ENB アイテムを取得できます。
- 2.8.1 は Paper Client 起動時の `IllegalClassLoadError` を修正します。Paper Server 0.8.2 は GUI 保存時の権限・距離・パケット長を検証します。
- 2.8.x には GM 楽器選択と 128 鍵ピアノを備えた Paper 編集画面、低音域の pitch clamp 修正、バニラキャリアを使う Paper Litematic 出力が含まれます。
- 2.9.0 では貼り付け後に N → Restore ENB から元の ENB Litematic を読み込み、赤い送信機の座標と回転・反転を指定してパラメーターを復元できます。Paper Server 0.9.0 と OP / import 権限が必要です。
- 2.10.0 では 32 種類の代表音色と 11 の音域アンカー、47 の打楽器音を含むパックを自動配信します。Paper Client は従来どおり完全な音色と編集機能を使用します。
- CI は成功していますが、低音域などの実際の Purpur ゲーム内検証は引き続き必要です。

詳細は[インストールガイド（中文）](INSTALLATION.md)、[2.10.0 変更内容（中文）](releases/2.10.0.md)、[開発ガイド](DEVELOPMENT_ja-jp.md)を参照してください。

## 原作者とライセンス

原作者は [Atemukesu](https://github.com/atemukesu/ExtendedNoteBlock)、26.1.1 移植と Workshop 拡張は [BF_skt](https://github.com/BianFuuuu/ExtendedNoteBlock)、26.2 / Paper Bridge の保守は **GoldenEggOVO** です。[MIT License](../LICENSE) と元の著作権表記を維持しています。

# Anti-ReelShorts

Instagram のリールと YouTube の Shorts を、**公式アプリの画面から消す** LSPosed モジュールです。

見えないように戻すのではなく、下のタブ・フィードの棚・検索結果からその項目自体を取り除き、残りの UI を詰めます。通常の動画、投稿、ストーリーズはそのまま使えます。

## 何をするか

- **下タブ:** 「Shorts」「リール」だけを消し、残りのタブを並べ直す
- **フィード、検索、登録チャンネル:** 棚やカードの高さを 0 にして、下の投稿を詰める
- **リンク:** YouTube の `/shorts/<id>` は通常の再生 URL で開く。Instagram の `/reel/` は開かない
- アプリごとにオン／オフを切り替えられる

YouTube と Instagram のプロセスの中で View を消しています。画面の判定はこの端末の中だけで、データはどこにも送信しません（インターネット権限もありません）。

## 必要なもの

- Magisk と Zygisk
- [LSPosed](https://github.com/LSPosed/LSPosed)（Zygisk 版）
- 公式の YouTube と Instagram。このモジュールは APK を改変したり同梱したりしません

## インストール

1. このアプリをインストールする（GitHub の **Releases**、または Actions の「Build APK」→ Artifacts の `AntiReelShorts.apk`）
2. LSPosed で「Anti ReelShorts」を有効にする
3. スコープに **YouTube** と **Instagram** を入れる
4. 両方のアプリを強制停止してから開き直す

スイッチを変えたあとも、対象のアプリを開き直してください。

ビルドごとに署名が変わるため、上書きインストールできない場合は一度アンインストールしてから入れ直してください。入れ直したあとは LSPosed の有効化が外れていないか確認してください。

## うまく消えない面が出たら

Instagram／YouTube のアップデートで内部の画面 ID が変わると、その面だけ残ることがあります。`app/src/main/java/com/antireelshorts/Targets.kt` の ID と表示名のリストを更新してください。クラス名は難読化されるため使っていません。

## 実機で確認すること

このリポジトリの自動テストは URL の変換と「どの View を消すか」の判定だけです。見た目は端末で確認します。

- 下タブから項目が消え、残りのタブが隙間なく並ぶ
- ホーム、検索、登録チャンネルに棚やカードが残らず、下の投稿が詰まる
- ストーリーズ、通常動画、通常投稿は操作できる
- 共有リンクで縦型プレイヤーが開かない。YouTube は通常の再生画面になる
- スイッチを切ってアプリを開き直すと、公式アプリの UI が元に戻る

## ビルド

`main` 以外のブランチも含め、push すると GitHub Actions が APK をビルドします。手元でビルドとテストをする場合は Android SDK を入れて:

```sh
./gradlew testDebugUnitTest assembleDebug
```

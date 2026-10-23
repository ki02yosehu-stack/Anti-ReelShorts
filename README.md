# Anti-ReelShorts

Instagram のリールと YouTube の Shorts を、**公式アプリを使ったまま**見られなくする Android アプリです。

## 何をするか

- **リール／Shorts の画面が開いたら自動で「戻る」**：おすすめ・リンク・DM など、どこから開いても閉じます。
- **下のタブボタンにフタをする**：「リール」「Shorts」タブの上に 🚫 を重ねて、押せないようにします。
- アプリごとにオン／オフを切り替えられます。

Android の「ユーザー補助（アクセシビリティ）サービス」で画面の構成を読み取り、端末の中だけで判定しています。データはどこにも送信しません（インターネット権限もありません）。

## インストール

1. GitHub の **Releases**（または Actions の「Build APK」→ Artifacts）から `AntiReelShorts.apk` をスマホでダウンロード
2. 開いてインストール（「提供元不明のアプリ」の許可を求められたら許可）
3. アプリを開いて「ユーザー補助の設定を開く」→「Anti ReelShorts」をオン

### スイッチがグレーで押せないとき（Android 13 以降）

設定 → アプリ → Anti ReelShorts → 右上の「⋮」→「制限付き設定を許可」をタップしてから、もう一度オンにしてください。

### 更新するとき

ビルドごとに署名が変わるため、上書きインストールできない場合は一度アンインストールしてから入れ直してください。

## うまく動かなくなったら

Instagram／YouTube のアップデートで内部の画面 ID が変わると検知できなくなることがあります。
その場合は `app/src/main/java/com/antireelshorts/Targets.kt` の ID リストを更新してください。

## ビルド

`main` 以外のブランチも含め、push すると GitHub Actions が APK をビルドします。手元でビルドする場合は Android SDK を入れて:

```sh
./gradlew assembleDebug
```

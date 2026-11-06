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

## Web版の「箱」アプリ（webbox）

ユーザー補助を使わない、もう一つの方式です。Instagram／YouTube の **Web版** を、タブ切り替えの1つのアプリで開きます。
中身は Firefox のエンジン（GeckoView）で、組み込みの拡張機能がリール／Shorts を消します。

- YouTube：Shorts タブ・Shorts の棚を非表示。Shorts のリンクは普通の動画プレーヤーで再生
- Instagram：リールのタブ・プロフィールのリールタブを非表示、リール画面はブロック（DM から開いたリールは見られる）
- 他のアプリで Instagram／YouTube のリンクを開くとき、このアプリを選べます

`AntiReelShortsWeb.apk` を Releases からインストールしてください（約 100MB、arm64 端末のみ）。

### 何かが消えなくなったら

`webbox/rules/remote.css`（main ブランチ）に CSS を追加すると、アプリが起動時と 6 時間ごとに読み込みます。入れ直しは不要です。

## うまく動かなくなったら

Instagram／YouTube のアップデートで内部の画面 ID が変わると検知できなくなることがあります。
その場合は `app/src/main/java/com/antireelshorts/Targets.kt` の ID リストを更新してください。

## ビルド

`main` 以外のブランチも含め、push すると GitHub Actions が APK をビルドします。手元でビルドする場合は Android SDK を入れて:

```sh
./gradlew assembleDebug
```

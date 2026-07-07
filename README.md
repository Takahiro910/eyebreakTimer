# EyeBreak — 20-20-20 リマインダー (Wear OS)

Pixel Watch 3(Wear OS 5)単体で動作する、眼精疲労予防「20-20-20ルール」のリマインダーアプリ。

有効時間帯・有効曜日の間、毎時 **00 / 20 / 40 分**に手首を振動させ(長め2回=「6m先を見ろ」)、
その **30秒後**に終了合図(短め1回=「画面に戻ってよし」)を振動させる。

- スタンドアロンWatchアプリ(スマホ側コンパニオンなし)
- 常駐サービス・ポーリングなし。`AlarmManager.setExactAndAllowWhileIdle()` によるワンショット連鎖のみで、待機時の追加電池消費は実質ゼロ
- ストア公開なし。debug APK をワイヤレスADBでサイドロードして使う

## 構成

```
app/
 ├─ MainActivity.kt              // Compose設定画面のホスト + 起動時のアラーム登録
 ├─ ui/SettingsScreen.kt         // ON/OFF・時間帯(30分刻み)・曜日・次回時刻の表示
 ├─ core/Scheduler.kt            // 次回発火時刻の計算(純粋関数・ユニットテスト対象)
 ├─ core/Settings.kt             // 設定データクラス(純粋)
 ├─ core/AlarmScheduler.kt       // AlarmManager登録(副作用側)
 ├─ core/AlarmReceiver.kt        // 振動実行 + 終了アラーム登録 + 次回再登録
 ├─ core/BootReceiver.kt         // 再起動・更新・時刻/TZ変更でアラーム再登録
 ├─ core/Reschedule.kt           // 設定読出→再登録→Tile更新の共通処理
 ├─ core/Prefs.kt                // DataStore (Preferences) ラッパー
 ├─ core/Vibration.kt            // 開始/終了バイブパターン
 └─ tile/EyeBreakTileService.kt  // Tile(ON/OFFトグル + 次回時刻表示)
```

## ビルド手順

### 必要環境

- JDK 17 以上
- Android SDK(Platform 34)。`ANDROID_HOME` 環境変数を設定するか、
  プロジェクト直下に `local.properties` を作成して `sdk.dir=/path/to/android-sdk` を記載する
- Android Studio は不要(CLIのみでビルド可能)

SDKをCLIだけで用意する場合(例):

```bash
# コマンドラインツールを https://developer.android.com/studio#command-line-tools-only から取得して展開後
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### ビルド

```bash
./gradlew :app:assembleDebug
# => app/build/outputs/apk/debug/app-debug.apk
```

### ユニットテスト

```bash
./gradlew test
```

`Scheduler.nextTrigger()`(次回発火時刻の計算)のテストが実行される。

## サイドロード手順(ワイヤレスADB)

1. Watch側: 「設定 → システム → 情報」でビルド番号を7回タップして開発者向けオプションを有効化
2. 「設定 → 開発者向けオプション → ワイヤレスデバッグ」をON(WatchとPCは同じWi-Fiに接続)
3. 「新しいデバイスとペア設定」をタップし、表示されたIP:ポートとペアリングコードでPCから:

   ```bash
   adb pair <IP>:<ペアリング用ポート>   # 例: adb pair 192.168.1.20:40001
   # ペアリングコードを入力
   adb connect <IP>:<接続用ポート>      # ワイヤレスデバッグ画面に表示されるポート(例: 192.168.1.20:5555)
   ```

4. インストール:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

5. **初回はWatch上でアプリを一度起動する**(起動時にアラームが登録される)。
   以降は `adb install -r` による更新でも `MY_PACKAGE_REPLACED` 受信で自動的に再登録される。

## 動作確認

### アラーム登録の確認

```bash
adb shell dumpsys alarm | grep -i eyebreak
```

`RTC_WAKEUP` で次回スロット時刻のアラームが1件登録されていればOK。
(発火直後の約30秒間は終了バイブ用のアラームも同時に見える)

### 発火を待たずにテストする

debugビルドでは設定画面の最下部に「**今すぐ発火 (テスト)**」ボタンがある。
タップすると開始バイブ(長め2回)→30秒後に終了バイブ(短め1回)が実行され、次回アラームも再登録される。

### Tile

文字盤を左右スワイプ → 「タイルの追加」から EyeBreak を追加。

- 次回発火時刻(OFF時は「停止中」)を表示
- ボタンでマスターON/OFFをその場でトグル(アプリを開く必要なし)

## 設定

| 項目 | デフォルト | 備考 |
|---|---|---|
| マスターON/OFF | ON | Tileからも切替可 |
| 有効時間帯 | 9:00〜18:00 | 30分刻み。終了時刻は排他的境界(18:00スロットは発火しない。最終は17:40) |
| 有効曜日 | 月〜金 | 7曜日を個別にON/OFF |

設定はDataStore (Preferences) に永続化され、再起動後も保持される。

## 既知の制約

- **発火精度**: `setExactAndAllowWhileIdle()` の仕様上、Doze中は数秒程度遅れることがある(目標±10秒以内)。
  また同APIの制約により、Doze中の発火は概ね9分に1回までにスロットリングされるが、
  本アプリのスロット間隔は20分のため通常は影響しない
- **時計変更**: 時刻・タイムゾーン変更時は自動で再登録されるが、変更の瞬間をまたぐ1回はずれる可能性がある
- **DND連動なし**(v2候補)。会議中などはマスタートグルかTileで手動OFFにする運用
- **バッテリーセーバー**: 極端な省電力モードではOSがアラームを遅延させる場合がある
- 通知・画面点灯は行わない(振動のみ)。統計・達成率トラッキングなし

## ライセンス / 配布

自分専用。ストア公開なし。

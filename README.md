# Chatbot Client (Android)

テキスト生成サーバ（**text-generation-webui 互換 API**）とやり取りする、シンプル＆拡張しやすい Android クライアントです。
RecyclerView + ListAdapter 構成で、メッセージバブル／再生成／コピー＆共有／「最新へ」FAB など、会話アプリの基本を実装しています。
個人学習用のため、動作保証などは一切行っておりません。

---


## ✨ 主な機能

* **チャット送受信（/v1/chat/completions 互換）**
* **-1 ボタン**：直近の *ユーザー＋AI* 2件を削除し、**ユーザーの最後の入力を編集欄へ復元**
* **Regenerate**：最後の **AI 応答を再生成**（直前のユーザー発言で再リクエスト）
* **コピー & 共有（長押しメニュー）**：各メッセージを長押し → コピー／共有／削除（任意）
* **最新へ FAB**：スクロールが上にあるときだけ表示、タップで末尾へ
* **ListAdapter + DiffUtil**：差分だけ更新し、スクロールや描画が滑らか
---

## 🧰 要件

* Android Studio (2024-11)
* 最低 SDK（目安）：`minSdk 24+`
* 依存：

  * `com.google.android.material:material`（Snackbar, FAB などで使用）

---

## 🔧 セットアップ

### 1) `local.properties` に API 情報を追記

```properties
API_BASE_URL=http://your-server:port/v1/chat/completions
TEXTGEN_BEARER=Bearer YOUR_TOKEN  # ない場合は空でも可（サーバ設定に依存）
```

### 2) `app/build.gradle` に BuildConfig を渡す

```gradle
def props = new Properties()
def lp = rootProject.file("local.properties")
if (lp.exists()) props.load(new FileInputStream(lp))

def API_BASE_URL   = props.getProperty("API_BASE_URL", "")
def TEXTGEN_BEARER = props.getProperty("TEXTGEN_BEARER", "")

android {
  defaultConfig {
    buildConfigField "String", "API_BASE_URL", "\"${API_BASE_URL}\""
    buildConfigField "String", "TEXTGEN_BEARER", "\"${TEXTGEN_BEARER}\""
  }
}
```

### 3) `AndroidManifest.xml` の権限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<!-- HTTP の場合は必要 -->
<application
    android:usesCleartextTraffic="true"
    ... >
```

> 入力時のレイアウト崩れを防ぐには（任意）：

```xml
<activity
    android:name=".MainActivity"
    android:windowSoftInputMode="stateHidden|adjustResize"
    ... />
```

---

## ▶️ 実行方法

1. 上のセットアップを行う
2. 実機 or エミュレータで **Run**
3. API サーバ（text-generation-webui 互換）が起動していることを確認

---

## 🖱️ 画面と操作

* **送信**：入力 → 「送信」ボタン
* **-1**：直近の *ユーザー＋AI* を削除し、ユーザー文を入力欄へ復元
* **Regenerate**：最後の **AI 応答だけ** を削除し、同じユーザー文で再生成
* **クリア**：履歴を全消去
* **最新へ**（FAB）：スクロールが上にあるとき表示／タップで末尾へ
* **長押し（各メッセージ）**：**コピー**／**共有**（／削除は任意）

---

## 🏗️ 実装のポイント

### ListAdapter + DiffUtil

* `Message` データモデル（`id`, `role`, `content`, `timestamp`）
* `MessageAdapter : ListAdapter<Message, ...>` で差分更新
* `submitList(messages.toList())` で **新しい List** を渡すのがコツ

### スクロール制御

* `AdapterDataObserver.onItemRangeInserted` で

  * **末尾に追加** かつ **ユーザーが最下部にいた** → 自動スクロール
  * それ以外 → **FAB を表示**
* `scrollToBottom()` は `recyclerView.post { smoothScrollToPosition(last) }` で空振り防止

### 下段ボタンのチェーン（均等幅）

* ConstraintLayout で **width=0dp + Horizontal\_weight=1**
* `Clear | -1 | Regenerate` を 1 本のチェーンに

---

## 🔌 API（text-generation-webui 互換）

**Endpoint**（例）
`POST $API_BASE_URL` → `/v1/chat/completions`

**Request（最小例）**

```json
{
  "messages": [
    { "role": "user", "content": "Hello" }
  ],
  "mode": "chat-instruct",
  "character": "Example",
  "instruction_template": "Command-R"
}
```

**Response（利用パス）**

```json
{
  "choices": [
    {
      "message": { "content": "こんにちは！" }
    }
  ]
}
```

本アプリは `choices[0].message.content` を取り出して表示します。
Bearer 認証が必要なら `Authorization: <TEXTGEN_BEARER>` を付与します。

> HTTP の場合、`usesCleartextTraffic="true"` を有効化してください。HTTPS 推奨。

---

## 📁 主なファイル構成（例）

```
app/src/main/java/com/example/chatbot/
  ├─ MainActivity.kt            // 画面制御・ネットワーク呼び出し
  ├─ Message.kt                 // data class / Role enum
  ├─ MessageAdapter.kt          // ListAdapter + 長押しメニュー
  ├─ MessageActionListener.kt   // コピー/共有/削除/再生成のコールバック（任意）

app/src/main/res/layout/
  ├─ activity_main.xml
  ├─ item_message_user.xml
  ├─ item_message_assistant.xml

app/src/main/res/menu/
  └─ message_item_menu.xml      // 長押しメニュー（コピー/共有/削除/再生成）

app/src/main/AndroidManifest.xml
```



## 🔑 ライセンス

```
MIT License
Copyright (c) 2025 ...
```

---

## 📣 クレジット

* Server: text-generation-webui 互換エンドポイント
* UI: Material Components for Android



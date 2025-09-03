package com.example.chatbot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var button: Button
    private lateinit var regenButton: Button
    private lateinit var minusOneButton: Button
    private lateinit var clearButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var idSeq = 1L
    private lateinit var fabToBottom: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    private var isUserAtBottom: Boolean = true

    private data class RemovalSnapshot(val messages: List<Message>, val positions: List<Int>)

    private var lastRemoval: RemovalSnapshot? = null

    private fun showUndoSnackbar(label: String, onUndo: () -> Unit) {
        val root = findViewById<View>(android.R.id.content)
        com.google.android.material.snackbar.Snackbar
            .make(root, label, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setAction("元に戻す") { onUndo() }
            .show()
    }

    private fun nextId() = idSeq++   // 安定ID生成

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editText = findViewById<EditText>(R.id.inputText)
        button = findViewById(R.id.button)
        clearButton = findViewById(R.id.clearButton)
        regenButton = findViewById(R.id.regenButton)
        minusOneButton = findViewById(R.id.minusOneButton)
        recyclerView = findViewById(R.id.recyclerView)

        messageAdapter = MessageAdapter(object : MessageActionListener {
            override fun onCopy(message: Message, position: Int) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("message", message.content))
                Toast.makeText(this@MainActivity, "コピーしました", Toast.LENGTH_SHORT).show()
            }

            override fun onShare(message: Message, position: Int) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message.content)
                }
                startActivity(Intent.createChooser(intent, "共有"))
            }

            override fun onDelete(message: Message, position: Int) {
                deleteMessageWithUndo(position) // 1-4 で作った関数を再利用
            }

            override fun onRegenerate(message: Message, position: Int) {
                regenerateAssistantAt(position)
            }
        })
        recyclerView.adapter = messageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        fabToBottom = findViewById(R.id.fabToBottom)

        // 初期表示(必要なら)
        addMessage(Message(nextId(), Role.SYSTEM, "チャットを開始しました。"))

        clearButton.setOnClickListener {
            messages.clear()
            render()
        }

        minusOneButton.setOnClickListener {
            removeLastPairAndRestoreInput(editText)
        }

        regenButton.setOnClickListener {
            regenerateLastAssistant(editText)
        }

        button.setOnClickListener {
            val inputText = editText.text.toString().trim()
            if (inputText.isEmpty()) return@setOnClickListener

            button.isEnabled = false
            editText.text.clear()

            // 送信：ユーザー→（後で）アシスタント
            addMessage(Message(nextId(), Role.USER, inputText))

            lifecycleScope.launch {
                val response = sendToChatbotServer(inputText)
                withContext(Dispatchers.Main) {
                    addMessage(Message(nextId(), Role.ASSISTANT, response))
                    scrollToBottom()
                    button.isEnabled = true
                }
            }
        }
        // RecyclerView のレイアウトマネージャ
        val lm = LinearLayoutManager(this)
        recyclerView.layoutManager = lm
        recyclerView.adapter = messageAdapter

// スクロール監視：一番下にいるかどうかを更新
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                isUserAtBottom = isAtBottom(lm)
                updateFabVisibility()
            }
        })

// データ挿入時の挙動（ListAdapter でも通常 OK）
        messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                // 直前まで最下部にいたなら自動スクロール、そうでなければ FAB を出して知らせる
                if (isUserAtBottom) {
                    scrollToBottom()
                } else {
                    showFab()
                }
            }
        })

// FAB タップで末尾へ
        fabToBottom.setOnClickListener {
            scrollToBottom()
            isUserAtBottom = true
            updateFabVisibility()
        }
    }

    // ---- ヘルパー群 ----

    private fun render() {
        messageAdapter.submitList(messages.toList())   // ★新インスタンスで渡す
    }

    private fun addMessage(m: Message) {
        messages.add(m)
        render()
        scrollToBottom()
    }

    /** -1: 直近のユーザー＆アシスタントを削除し、ユーザー文を入力に戻す */
    private fun removeLastPairAndRestoreInput(edit: EditText) {
        val lastAssistant = messages.indexOfLast { it.role == Role.ASSISTANT }
        if (lastAssistant == -1) return
        val lastUser = messages.subList(0, lastAssistant).indexOfLast { it.role == Role.USER }
        if (lastUser == -1) return

        val userText = messages[lastUser].content

        // スナップショット保存（削除する2件と元の位置）
        val removed = listOf(messages[lastUser], messages[lastAssistant])
        val positions = listOf(lastUser, lastAssistant)
        lastRemoval = RemovalSnapshot(removed, positions)

        // 実削除（アシスタント→ユーザーの順に削除注意）
        messages.removeAt(lastAssistant)
        messages.removeAt(lastUser)
        render()

        // 入力に復元
        edit.setText(userText)
        edit.setSelection(userText.length)

        showUndoSnackbar("直前の2メッセージを削除しました") {
            // 位置を保って復元（lastUser < lastAssistant なので順に挿入）
            lastRemoval?.let { snap ->
                messages.add(snap.positions[0], snap.messages[0])
                messages.add(snap.positions[1], snap.messages[1])
                render()
                scrollToBottom()
                lastRemoval = null
            }
        }
    }

    private fun deleteMessageWithUndo(position: Int) {
        if (position !in messages.indices) return
        val removed = messages[position]
        lastRemoval = RemovalSnapshot(listOf(removed), listOf(position))
        messages.removeAt(position)
        render()

        showUndoSnackbar("メッセージを削除しました") {
            lastRemoval?.let { snap ->
                messages.add(snap.positions[0], snap.messages[0])
                render()
                lastRemoval = null
            }
        }
    }

    /** Regenerate: 直近のアシスタントだけ削除→直前のユーザーで再生成 */
    private fun regenerateLastAssistant(edit: EditText) {
        val lastAssistant = messages.indexOfLast { it.role == Role.ASSISTANT }
        if (lastAssistant == -1) return
        val lastUser = messages.subList(0, lastAssistant).indexOfLast { it.role == Role.USER }
        if (lastUser == -1) return
        val userText = messages[lastUser].content

        // 直近AI削除→プレースホルダ→API→置換
        messages.removeAt(lastAssistant)
        val placeholder = Message(nextId(), Role.ASSISTANT, "（再生成中…）")
        messages.add(placeholder)
        render()
        scrollToBottom()

        // ボタン無効化
        val disableAll = { button.isEnabled = false; regenButton.isEnabled = false; minusOneButton.isEnabled = false; clearButton.isEnabled = false }
        val enableAll = { button.isEnabled = true; regenButton.isEnabled = true; minusOneButton.isEnabled = true; clearButton.isEnabled = true }
        disableAll()

        lifecycleScope.launch {
            val response = sendToChatbotServer(userText)
            withContext(Dispatchers.Main) {
                // プレースホルダを本物で置換（末尾想定）
                val idx = messages.indexOfLast { it.id == placeholder.id }
                if (idx != -1) {
                    messages[idx] = Message(nextId(), Role.ASSISTANT, response)
                    render()
                    scrollToBottom()
                }
                enableAll()
            }
        }
    }

    private fun regenerateAssistantAt(position: Int) {
        // 安全チェック
        if (position !in messages.indices) return
        val target = messages[position]
        if (target.role != Role.ASSISTANT) {
            Toast.makeText(this, "アシスタントのメッセージを選んでください", Toast.LENGTH_SHORT).show()
            return
        }

        // 直前のユーザー発言を探す
        val lastUserIndex = messages.subList(0, position).indexOfLast { it.role == Role.USER }
        if (lastUserIndex == -1) {
            Toast.makeText(this, "直前のユーザーメッセージが見つかりません", Toast.LENGTH_SHORT).show()
            return
        }
        val userText = messages[lastUserIndex].content

        // その位置のアシスタントをプレースホルダに置換
        val placeholder = Message(nextId(), Role.ASSISTANT, "（再生成中…）")
        messages[position] = placeholder
        render()

        // （任意）全体ボタンを一時無効化。必要なければ外してOK
        setUiEnabled(false)

        lifecycleScope.launch {
            val response = sendToChatbotServer(userText) // 既存のAPIをそのまま利用
            withContext(Dispatchers.Main) {
                // まだプレースホルダが同じ位置に居るなら置換
                val currentIndex = messages.indexOfFirst { it.id == placeholder.id }
                if (currentIndex != -1) {
                    messages[currentIndex] = Message(nextId(), Role.ASSISTANT, response)
                    render()
                    // 必要なら、そのメッセージ位置までスクロール
                    recyclerView.post { recyclerView.smoothScrollToPosition(currentIndex) }
                }
                setUiEnabled(true)
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        // 画面の主要ボタンをまとめて制御（必要に応じて増減）
        button.isEnabled = enabled
        regenButton.isEnabled = enabled
        minusOneButton.isEnabled = enabled
        clearButton.isEnabled = enabled
    }

    private fun isAtBottom(lm: LinearLayoutManager): Boolean {
        val lastVisible = lm.findLastVisibleItemPosition()
        return lastVisible >= (messageAdapter.itemCount - 1)
    }

    private fun scrollToBottom() {
        val lastIndex = (recyclerView.adapter?.itemCount ?: 0) - 1
        if (lastIndex >= 0) {
            // submitList の適用やレイアウト完了後に走らせる
            recyclerView.post {
                recyclerView.smoothScrollToPosition(lastIndex)
            }
        }
    }

    private fun showFab() {
        if (!fabToBottom.isShown) fabToBottom.show()
    }

    private fun hideFab() {
        if (fabToBottom.isShown) fabToBottom.hide()
    }

    private fun updateFabVisibility() {
        if (isUserAtBottom || messageAdapter.itemCount == 0) hideFab() else showFab()
    }

    private suspend fun sendToChatbotServer(inputText: String): String {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null //
            try {
                val url = URL(BuildConfig.API_BASE_URL)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", BuildConfig.TEXTGEN_BEARER)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val requestBody = JSONObject().apply {
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", inputText)
                        })
                    })
                    put("mode", "chat-instruct")
                    put("character", "Example")
                    put("instruction_template", "Command-R")
                }.toString()

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }

                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = reader.readText()
                    val jsonResponse = JSONObject(response)
                    // メッセージ部分を抽出
                    val messageContent = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    Log.d("Chatbot", "Response: $messageContent")
                    messageContent // メッセージ部分を返す
                }
            } catch (e: IOException) {
                Log.e("Chatbot", "Network Error: ${e.message}", e)
                "ネットワークエラーが発生しました"
            } catch (e: JSONException) {
                Log.e("Chatbot", "JSON Parse Error: ${e.message}", e)
                "JSONのパースエラーが発生しました"
            } finally {
                connection?.disconnect()
            }
        }
    }
}
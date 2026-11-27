package com.blackcore.ai

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ChatMessage(val id: String = UUID.randomUUID().toString(), val role: String, val content: String)
data class AIConfig(
    val endpoint: String = "https://api.openai.com/v1/chat/completions",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = "You are BlackCore AI, a helpful, clear, and capable assistant."
)
data class ChatState(
    val messages: List<ChatMessage> = emptyList(), val input: String = "", val loading: Boolean = false,
    val error: String? = null, val config: AIConfig = AIConfig(), val configured: Boolean = false, val model: String = ""
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("blackcore_settings", Context.MODE_PRIVATE)
    private val dao = BlackCoreDatabase.get(app).chatDao()
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private fun readConfig() = AIConfig(
        prefs.getString("endpoint", AIConfig().endpoint) ?: AIConfig().endpoint,
        prefs.getString("key", "") ?: "", prefs.getString("model", "") ?: "",
        prefs.getString("system", AIConfig().systemPrompt) ?: AIConfig().systemPrompt
    )
    private val config = readConfig()
    private val _state = MutableStateFlow(ChatState(config=config, configured=config.apiKey.isNotBlank() && config.model.isNotBlank(), model=config.model))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init { viewModelScope.launch { dao.observeMessages().collect { rows ->
        if (!_state.value.loading) _state.value = _state.value.copy(messages=rows.map { ChatMessage(it.id,it.role,it.content) })
    } } }
    fun setInput(v:String) { _state.value=_state.value.copy(input=v,error=null) }
    fun saveConfig(c:AIConfig) {
        prefs.edit().putString("endpoint",c.endpoint).putString("key",c.apiKey).putString("model",c.model).putString("system",c.systemPrompt).apply()
        _state.value=_state.value.copy(config=c,configured=c.apiKey.isNotBlank() && c.model.isNotBlank(),model=c.model,error=null)
    }
    fun newChat() { viewModelScope.launch { dao.clear(); _state.value=_state.value.copy(messages=emptyList(),input="",error=null) } }
    fun send() {
        val s=_state.value; val prompt=s.input.trim()
        if(prompt.isEmpty() || s.loading) return
        if(!s.configured) { _state.value=s.copy(error="Masukkan API key dan model melalui Settings terlebih dahulu."); return }
        val user=ChatMessage(role="user",content=prompt); val history=s.messages+user
        _state.value=s.copy(messages=history,input="",loading=true,error=null)
        viewModelScope.launch {
            dao.insert(StoredMessage(user.id,user.role,user.content,System.currentTimeMillis()))
            val aid=UUID.randomUUID().toString()
            try {
                val response=withContext(Dispatchers.IO) { stream(s.config,history) { partial ->
                    _state.value=_state.value.copy(messages=_state.value.messages.filterNot{it.id==aid}+ChatMessage(aid,"assistant",partial))
                } }
                if(response.isNotBlank()) dao.insert(StoredMessage(aid,"assistant",response,System.currentTimeMillis()))
                _state.value=_state.value.copy(loading=false)
            } catch(e:Exception) {
                _state.value=_state.value.copy(loading=false,error=e.message ?: "Request error",messages=_state.value.messages.filterNot{it.id==aid})
            }
        }
    }
    private fun stream(c:AIConfig, history:List<ChatMessage>, onChunk:(String)->Unit):String {
        val arr=JSONArray().put(JSONObject().put("role","system").put("content",c.systemPrompt))
        history.forEach { arr.put(JSONObject().put("role",it.role).put("content",it.content)) }
        val body=JSONObject().put("model",c.model).put("messages",arr).put("stream",true).toString()
        val req=Request.Builder().url(c.endpoint).post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization","Bearer ${c.apiKey}").header("Accept","text/event-stream").build()
        http.newCall(req).execute().use { res ->
            if(!res.isSuccessful) { val msg=res.body?.string().orEmpty(); throw IllegalStateException("HTTP ${res.code}: ${msg.take(400)}") }
            val source=res.body?.source() ?: error("Empty response stream")
            val text=StringBuilder()
            while(!source.exhausted()) {
                val line=source.readUtf8Line() ?: break
                if(!line.startsWith("data:")) continue
                val data=line.removePrefix("data:").trim(); if(data=="[DONE]") break
                val delta=try { JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty() } catch(_:Exception){""}
                if(delta.isNotEmpty()){text.append(delta);onChunk(text.toString())}
            }
            return text.toString()
        }
    }
}

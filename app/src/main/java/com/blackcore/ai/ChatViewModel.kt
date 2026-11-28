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
import java.io.IOException
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
    val error: String? = null, val config: AIConfig = AIConfig(), val configured: Boolean = false,
    val model: String = "", val threads: List<ChatThread> = emptyList(), val activeThreadId: String = ""
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("blackcore_settings", Context.MODE_PRIVATE)
    private val dao = BlackCoreDatabase.get(app).chatDao()
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    @Volatile private var activeCall: Call? = null
    private var activeJob: Job? = null
    @Volatile private var generationCounter = 0L
    @Volatile private var stoppedGeneration: Long? = null
    private var messageCollection: Job? = null

    private fun readConfig() = AIConfig(
        prefs.getString("endpoint", AIConfig().endpoint) ?: AIConfig().endpoint,
        prefs.getString("key", "") ?: "", prefs.getString("model", "") ?: "",
        prefs.getString("system", AIConfig().systemPrompt) ?: AIConfig().systemPrompt
    )
    private val config = readConfig()
    private val _state = MutableStateFlow(ChatState(config=config, configured=config.apiKey.isNotBlank() && config.model.isNotBlank(), model=config.model))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeThreads().collect { list ->
                _state.value = _state.value.copy(threads=list)
                if (_state.value.activeThreadId.isBlank()) {
                    if (list.isNotEmpty()) openThread(list.first().id)
                    else createChat()
                }
            }
        }
    }
    private fun watchMessages(threadId: String) {
        messageCollection?.cancel()
        messageCollection = viewModelScope.launch {
            dao.observeMessages(threadId).collect { rows ->
                if (_state.value.activeThreadId == threadId && !_state.value.loading)
                    _state.value = _state.value.copy(messages=rows.map { ChatMessage(it.id,it.role,it.content) })
            }
        }
    }
    fun openThread(id:String) {
        if(_state.value.loading) stopGeneration()
        _state.value=_state.value.copy(activeThreadId=id,messages=emptyList(),input="",error=null)
        watchMessages(id)
    }
    fun createChat() {
        viewModelScope.launch {
            val now=System.currentTimeMillis(); val id=UUID.randomUUID().toString()
            dao.insertThread(ChatThread(id,"Percakapan baru",now,now))
            openThread(id)
        }
    }
    fun renameChat(id:String,title:String) { viewModelScope.launch { dao.renameThread(id,title.trim().ifBlank{"Percakapan"},System.currentTimeMillis()) } }
    fun deleteChat(id:String) {
        viewModelScope.launch {
            val wasActive = _state.value.activeThreadId == id
            if(wasActive) {
                val now=System.currentTimeMillis()
                val replacement=ChatThread(UUID.randomUUID().toString(),"Percakapan baru",now,now)
                dao.insertThread(replacement)
                openThread(replacement.id)
            }
            dao.deleteMessages(id)
            dao.deleteThread(id)
        }
    }
    fun setInput(v:String) { _state.value=_state.value.copy(input=v,error=null) }
    fun saveConfig(c:AIConfig) {
        prefs.edit().putString("endpoint",c.endpoint).putString("key",c.apiKey).putString("model",c.model).putString("system",c.systemPrompt).apply()
        _state.value=_state.value.copy(config=c,configured=c.apiKey.isNotBlank() && c.model.isNotBlank(),model=c.model,error=null)
    }
    fun newChat() = createChat()
    fun stopGeneration() {
        stoppedGeneration = generationCounter
        activeCall?.cancel()
        _state.value=_state.value.copy(loading=false)
    }
    fun send() {
        val s=_state.value; val prompt=s.input.trim()
        if(prompt.isEmpty() || s.loading) return
        if(!s.configured) { _state.value=s.copy(error="Masukkan API key dan model melalui Settings terlebih dahulu."); return }
        if(s.activeThreadId.isBlank()) { createChat(); return }
        val generation = ++generationCounter
        stoppedGeneration = null
        val threadId=s.activeThreadId
        val user=ChatMessage(role="user",content=prompt); val history=s.messages+user
        _state.value=s.copy(messages=history,input="",loading=true,error=null)
        activeJob=viewModelScope.launch {
            dao.insert(StoredMessage(user.id,threadId,user.role,user.content,System.currentTimeMillis()))
            if(dao.messageCount(threadId)==1) dao.renameThread(threadId,prompt.take(40),System.currentTimeMillis())
            dao.touchThread(threadId,System.currentTimeMillis())
            val aid=UUID.randomUUID().toString(); var partial=""
            if(generation in stoppedGenerations) { stoppedGenerations = stoppedGenerations - generation; if(_state.value.activeThreadId==threadId) _state.value=_state.value.copy(loading=false); return@launch }
            try {
                val response=withContext(Dispatchers.IO) { stream(s.config,history,generation) { text ->
                    partial=text
                    if(_state.value.activeThreadId==threadId)
                        _state.value=_state.value.copy(messages=_state.value.messages.filterNot{it.id==aid}+ChatMessage(aid,"assistant",text))
                } }
                if(response.isNotBlank()) dao.insert(StoredMessage(aid,threadId,"assistant",response,System.currentTimeMillis()))
                if(_state.value.activeThreadId==threadId && generation == generationCounter) _state.value=_state.value.copy(loading=false)
            } catch(e:Exception) {
                if(generation in stoppedGenerations) {
                    if(partial.isNotBlank()) dao.insert(StoredMessage(aid,threadId,"assistant",partial,System.currentTimeMillis()))
                    if(_state.value.activeThreadId==threadId && generation == generationCounter) _state.value=_state.value.copy(loading=false)
                } else {
                    if(_state.value.activeThreadId==threadId && generation == generationCounter) _state.value=_state.value.copy(loading=false,error=e.message ?: "Request error",messages=_state.value.messages.filterNot{it.id==aid})
                }
            } finally { stoppedGenerations = stoppedGenerations - generation; if(generation == generationCounter) activeCall=null }
        }
    }
    private fun stream(c:AIConfig,history:List<ChatMessage>,generation:Long,onChunk:(String)->Unit):String {
        val arr=JSONArray().put(JSONObject().put("role","system").put("content",c.systemPrompt))
        history.forEach { arr.put(JSONObject().put("role",it.role).put("content",it.content)) }
        val body=JSONObject().put("model",c.model).put("messages",arr).put("stream",true).toString()
        val req=Request.Builder().url(c.endpoint).post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization","Bearer ${c.apiKey}").header("Accept","text/event-stream").build()
        val call=http.newCall(req)
        if(generation == generationCounter) activeCall=call
        if(generation in stoppedGenerations) call.cancel()
        call.execute().use { res ->
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
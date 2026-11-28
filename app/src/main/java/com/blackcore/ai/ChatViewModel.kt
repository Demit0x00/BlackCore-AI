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
import java.util.concurrent.ConcurrentHashMap

data class ChatMessage(val id: String = UUID.randomUUID().toString(), val role: String, val content: String)\nprivate class ProviderHttpException(val statusCode: Int, message: String) : IOException(message)
data class AIConfig(
    val endpoint: String = "https://api.openai.com/v1/chat/completions",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = "You are BlackCore AI, a helpful, clear, and capable assistant.",
    val provider: String = "OpenAI"
)
data class ChatState(
    val messages: List<ChatMessage> = emptyList(), val input: String = "", val loading: Boolean = false,
    val error: String? = null, val config: AIConfig = AIConfig(), val configured: Boolean = false,
    val model: String = "", val threads: List<ChatThread> = emptyList(), val activeThreadId: String = ""
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("blackcore_settings", Context.MODE_PRIVATE)
    private val dao = BlackCoreDatabase.get(app).chatDao()
    // Fail fast on connection stalls, but allow a reasonable first-token wait and bounded overall request time.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    @Volatile private var activeCall: Call? = null
    private var activeJob: Job? = null
    @Volatile private var generationCounter = 0L
    private val stoppedGenerations = ConcurrentHashMap.newKeySet<Long>()
    private var messageCollection: Job? = null

    private fun readConfig(): AIConfig {
        val provider = prefs.getString("provider", "OpenAI") ?: "OpenAI"
        val savedModel = prefs.getString("model", "") ?: ""
        val endpoint = prefs.getString("endpoint", AIConfig().endpoint) ?: AIConfig().endpoint
        // Google restricted Gemini 2.5 Flash for new API users; migrate old saved settings.
        val model = if (provider == "Google Gemini" && savedModel == "gemini-2.5-flash") "gemini-3.8-flash" else savedModel
        if (model != savedModel) prefs.edit().putString("model", model).apply()
        return AIConfig(
            endpoint, prefs.getString("key", "") ?: "", model,
            prefs.getString("system", AIConfig().systemPrompt) ?: AIConfig().systemPrompt,
            provider
        )
    }
    private val config = readConfig()
    private val _state = MutableStateFlow(ChatState(config=config, configured=config.apiKey.isNotBlank() && config.model.isNotBlank(), model=if(config.apiKey.isNotBlank()) config.provider + " · " + config.model else ""))
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
    fun saveConfig(input:AIConfig) {
        val provider = input.provider
        val (endpoint, model) = providerDefaults(provider)
        val c = input.copy(endpoint=endpoint, model=model)
        prefs.edit().putString("endpoint",c.endpoint).putString("key",c.apiKey).putString("model",c.model).putString("system",c.systemPrompt).putString("provider",provider).apply()
        _state.value=_state.value.copy(config=c,configured=c.apiKey.isNotBlank(),model=provider + " · " + model,error=null)
    }
    private fun providerDefaults(provider:String):Pair<String,String> = when(provider) {
        "Google Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" to "gemini-3.8-flash"
        "Anthropic Claude" -> "https://api.anthropic.com/v1/messages" to "claude-haiku-4-5"
        "Groq" -> "https://api.groq.com/openai/v1/chat/completions" to "openai/gpt-oss-120b"
        "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions" to "google/gemini-2.5-flash"
        "DeepSeek" -> "https://api.deepseek.com/chat/completions" to "deepseek-v4-flash"
        "Mistral" -> "https://api.mistral.ai/openai/v1/chat/completions" to "mistral-large-latest"
        "xAI Grok" -> "https://api.x.ai/v1/chat/completions" to "grok-4"
        "Together AI" -> "https://api.together.xyz/v1/chat/completions" to "meta-llama/Llama-3.3-70B-Instruct-Turbo"
        "Cerebras" -> "https://api.cerebras.ai/v1/chat/completions" to "openai/gpt-oss-120b"
        "Fireworks AI" -> "https://api.fireworks.ai/inference/v1/chat/completions" to "accounts/fireworks/models/glm-5p3"
        "Qwen (Alibaba)" -> "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions" to "qwen3.5-plus"
        "Moonshot Kimi" -> "https://api.moonshot.ai/v1/chat/completions" to "kimi-k3.1-pro"
        "Z.ai GLM" -> "https://api.z.ai/api/paas/v4/chat/completions" to "glm-5.3"
        "MiniMax" -> "https://api.minimax.io/v1/chat/completions" to "MiniMax-M3.1-Flash"
        else -> "https://api.openai.com/v1/chat/completions" to "gpt-4.1-mini"
    }
    fun newChat() = createChat()
    fun stopGeneration() {
        stoppedGenerations.add(generationCounter)
        activeCall?.cancel()
        _state.value=_state.value.copy(loading=false)
    }
    fun send() {
        val s=_state.value; val prompt=s.input.trim()
        if(prompt.isEmpty() || s.loading) return
        if(!s.configured) { _state.value=s.copy(error="Masukkan API key dan pilih penyedia AI melalui Settings terlebih dahulu."); return }
        if(s.activeThreadId.isBlank()) { createChat(); return }
        val generation = ++generationCounter
        val threadId=s.activeThreadId
        val user=ChatMessage(role="user",content=prompt); val history=s.messages+user
        _state.value=s.copy(messages=history,input="",loading=true,error=null)
        activeJob=viewModelScope.launch {
            dao.insert(StoredMessage(user.id,threadId,user.role,user.content,System.currentTimeMillis()))
            if(dao.messageCount(threadId)==1) dao.renameThread(threadId,prompt.take(40),System.currentTimeMillis())
            dao.touchThread(threadId,System.currentTimeMillis())
            val aid=UUID.randomUUID().toString(); var partial=""
            if(stoppedGenerations.contains(generation)) { stoppedGenerations.remove(generation); if(_state.value.activeThreadId==threadId) _state.value=_state.value.copy(loading=false); return@launch }
            try {
                val response=withContext(Dispatchers.IO) {
                    try {
                        stream(s.config,history,generation) { text ->
                            partial=text
                            if(_state.value.activeThreadId==threadId && generation == generationCounter)
                                _state.value=_state.value.copy(messages=_state.value.messages.filterNot{it.id==aid}+ChatMessage(aid,"assistant",text))
                        }
                    } catch (first: Exception) {
                        val fallback = fallbackModelFor(s.config, first)
                        if (fallback == null || stoppedGenerations.contains(generation)) throw first
                        val retryConfig = s.config.copy(model=fallback)
                        // Save the recovered model so subsequent chats do not repeat the failing request.
                        prefs.edit().putString("model", fallback).apply()
                        if (_state.value.activeThreadId == threadId && generation == generationCounter)
                            _state.value = _state.value.copy(config=retryConfig, model=retryConfig.provider + " · " + fallback, error=null,
                                messages=_state.value.messages.filterNot { it.id == aid })
                        partial = ""
                        stream(retryConfig,history,generation) { text ->
                            partial=text
                            if(_state.value.activeThreadId==threadId && generation == generationCounter)
                                _state.value=_state.value.copy(messages=_state.value.messages.filterNot{it.id==aid}+ChatMessage(aid,"assistant",text))
                        }
                    }
                }
                if(response.isNotBlank()) dao.insert(StoredMessage(aid,threadId,"assistant",response,System.currentTimeMillis()))
                if(_state.value.activeThreadId==threadId && generation == generationCounter) _state.value=_state.value.copy(loading=false)
            } catch(e:Exception) {
                if(stoppedGenerations.contains(generation)) {
                    if(partial.isNotBlank()) dao.insert(StoredMessage(aid,threadId,"assistant",partial,System.currentTimeMillis()))
                    if(_state.value.activeThreadId==threadId && generation == generationCounter) _state.value=_state.value.copy(loading=false)
                } else {
                    if(_state.value.activeThreadId==threadId && generation == generationCounter) _state.value=_state.value.copy(loading=false,error=friendlyException(e),messages=_state.value.messages.filterNot{it.id==aid})
                }
            } finally { stoppedGenerations.remove(generation); if(generation == generationCounter) activeCall=null }
        }
    }
    private fun fallbackModelFor(config: AIConfig, error: Exception): String? {
        val message = error.message.orEmpty().lowercase()
        val looksLikeModelError = message.contains("model") && (
            message.contains("not found") || message.contains("not available") ||
            message.contains("no longer available") || message.contains("unknown") ||
            message.contains("unsupported") || message.contains("not supported") ||
            message.contains("not_found") || message.contains("does not exist")
        )
        if (!looksLikeModelError) return null
        val fallback = when (config.provider) {
            "Google Gemini" -> "gemini-3.8-flash"
            "OpenRouter" -> "google/gemini-3.8-flash"
            else -> providerDefaults(config.provider).second
        }
        return fallback.takeIf { it.isNotBlank() && it != config.model }
    }

    private fun friendlyHttpError(code: Int, body: String): String {
        val root = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val json = root.optJSONObject("error") ?: root
        val rawMessage = json.optString("message", json.optString("error_description", root.optString("message", ""))).trim()
        val errorCode = json.optString("code", root.optString("code", "")).lowercase()
        val errorType = json.optString("type", root.optString("type", "")).lowercase()
        val combined = (rawMessage + " " + errorCode + " " + errorType).lowercase()
        return when {
            code == 401 || combined.contains("invalid_api_key") || combined.contains("incorrect api key") ||
                combined.contains("unauthorized") || errorType.contains("authentication") ->
                "API KEY TIDAK VALID\nKey ditolak oleh provider. Buka Pengaturan, pastikan provider sesuai, lalu masukkan API key baru yang benar."
            code == 403 -> "AKSES DITOLAK\nProvider menolak akses. Periksa izin API, billing, dan akses model pada dashboard provider."
            code == 429 && (combined.contains("quota") || combined.contains("billing") || combined.contains("insufficient")) ->
                "KUOTA API HABIS\nSaldo atau batas penggunaan API tidak mencukupi. Periksa billing dan limit provider."
            code == 429 -> "RATE LIMIT\nTerlalu banyak permintaan. Tunggu sebentar lalu coba kembali."
            code == 400 -> "REQUEST TIDAK VALID\nPeriksa model dan pengaturan provider. " + rawMessage.take(160)
            code == 404 -> "MODEL / ENDPOINT TIDAK DITEMUKAN\nPeriksa provider dan model yang dipilih."
            code == 408 -> "REQUEST TIMEOUT\nProvider terlalu lama merespons. Coba lagi atau gunakan model lebih cepat."
            code in 500..599 -> "SERVER PROVIDER BERMASALAH (HTTP $code)\nCoba lagi beberapa saat."
            rawMessage.isNotBlank() -> "REQUEST GAGAL (HTTP $code)\n" + rawMessage.take(180)
            else -> "REQUEST GAGAL (HTTP $code)\nPeriksa konfigurasi dan coba lagi."
        }
    }

    private fun friendlyException(e: Exception): String {
        val raw = e.message.orEmpty().lowercase()
        return when {
            e is java.net.UnknownHostException -> "Tidak dapat terhubung ke server. Periksa koneksi internet atau alamat endpoint."
            e is java.net.SocketTimeoutException || raw.contains("timeout") -> "Respons terlalu lama atau koneksi tidak stabil. BlackCore kini memberi waktu lebih panjang; coba ulangi atau gunakan model yang lebih cepat."
            raw.contains("invalid_api_key") || raw.contains("incorrect api key") || raw.contains("401") ->
                "API KEY TIDAK VALID\nKey ditolak provider. Buka Pengaturan, periksa provider, lalu masukkan API key yang benar."
            raw.contains("quota") || raw.contains("billing") || raw.contains("insufficient") ->
                "KUOTA API HABIS\nPeriksa saldo, billing, atau batas penggunaan di dashboard provider."
            raw.contains("rate limit") || raw.contains("429") -> "RATE LIMIT\nTerlalu banyak permintaan. Tunggu sebentar lalu coba kembali."
            e is IOException -> "Gangguan jaringan: ${e.message?.take(140) ?: "koneksi gagal"}. Periksa internet lalu coba kembali."
            else -> e.message?.take(220) ?: "Terjadi kesalahan. Coba kembali."
        }
    }

    private fun streamAnthropic(c:AIConfig,history:List<ChatMessage>,generation:Long,onChunk:(String)->Unit):String {
        val messages=JSONArray()
        history.takeLast(24).forEach { messages.put(JSONObject().put("role",it.role).put("content",it.content.take(30000))) }
        val body=JSONObject().put("model",c.model).put("max_tokens",4096).put("system",c.systemPrompt.take(12000)).put("messages",messages).put("stream",true).toString()
        val req=Request.Builder().url(c.endpoint).post(body.toRequestBody("application/json".toMediaType()))
            .header("x-api-key",c.apiKey).header("anthropic-version","2023-06-01").header("Accept","text/event-stream").build()
        val call=http.newCall(req)
        if(generation == generationCounter) activeCall=call
        if(stoppedGenerations.contains(generation)) call.cancel()
        call.execute().use { res ->
            if(!res.isSuccessful) { val msg=res.body?.string().orEmpty(); throw ProviderHttpException(res.code, friendlyHttpError(res.code, msg)) }
            val source=res.body?.source() ?: error("Empty response stream")
            val text=StringBuilder()
            while(!source.exhausted()) {
                val line=source.readUtf8Line() ?: break
                if(!line.startsWith("data:")) continue
                val data=line.removePrefix("data:").trim()
                val delta=try { JSONObject(data).optJSONObject("delta")?.optString("text").orEmpty() } catch(_:Exception){""}
                if(delta.isNotEmpty()){text.append(delta);onChunk(text.toString())}
            }
            return text.toString()
        }
    }
    private fun stream(c:AIConfig,history:List<ChatMessage>,generation:Long,onChunk:(String)->Unit):String {
        // Retry one transient network/server failure and discard partial text to prevent duplicate tokens.
        var attempt = 0
        while (true) {
            try {
                return streamOnce(c, history, generation, onChunk)
            } catch (e: Exception) {
                val message = e.message.orEmpty()
                val httpTransient = e is ProviderHttpException && (e.statusCode == 408 || e.statusCode in 500..599 || (e.statusCode == 429 && !message.contains("KUOTA API HABIS")))
                val transient = e is SocketTimeoutException || (e is IOException && e !is ProviderHttpException) || httpTransient
                if (attempt >= 1 || !transient || stoppedGenerations.contains(generation)) throw e
                attempt++
                onChunk("")
                try { Thread.sleep(1500L) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
    }
    private fun streamOnce(c:AIConfig,history:List<ChatMessage>,generation:Long,onChunk:(String)->Unit):String {
        if(c.provider == "Anthropic Claude") return streamAnthropic(c,history,generation,onChunk)
        val arr=JSONArray().put(JSONObject().put("role","system").put("content",c.systemPrompt))
        history.forEach { arr.put(JSONObject().put("role",it.role).put("content",it.content)) }
        val body=JSONObject().put("model",c.model).put("messages",arr).put("stream",true).toString()
        val req=Request.Builder().url(c.endpoint).post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization","Bearer ${c.apiKey}").header("Accept","text/event-stream").build()
        val call=http.newCall(req)
        if(generation == generationCounter) activeCall=call
        if(stoppedGenerations.contains(generation)) call.cancel()
        call.execute().use { res ->
            if(!res.isSuccessful) { val msg=res.body?.string().orEmpty(); throw IllegalStateException(friendlyHttpError(res.code, msg)) }
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
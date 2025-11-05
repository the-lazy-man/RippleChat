package com.pos.core.viewmodel // Assume a package name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.example.ripplechat.BuildConfig
import kotlin.random.Random

// Placeholder data class used in the ViewModel's state flow
data class AiMessage(val text: String, val isUser: Boolean)

//// Placeholder for BuildConfig properties, which would normally be generated
//object BuildConfig {
//    const val OPENAI_API_KEY = "YOUR_OPENAI_KEY"
//    const val GEMINI_API_KEY = "YOUR_GEMINI_KEY"
//    const val HF_API_KEY = "YOUR_HUGGINGFACE_KEY"
//}
//

@HiltViewModel
class AiViewModel @Inject constructor() : ViewModel() {

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val client = OkHttpClient()

    enum class Provider { OPENAI, GEMINI, HUGGINGFACE }
    private val allProviders = Provider.entries.toTypedArray()

    var currentProvider: Provider = Provider.OPENAI // default

    fun sendMessage(userText: String) {
        // 2. RANDOMIZATION LOGIC
        val selectedProvider = allProviders[Random.nextInt(allProviders.size)]
        println("Selected LLM: $selectedProvider") // Optional: Log to check which one was selected

        // 3. Add user message to the list immediately
        _messages.value = _messages.value + AiMessage(userText, true)

        // 4. Launch API call in ViewModel scope
        viewModelScope.launch {
            val reply = when (selectedProvider) { // Use the randomly selected provider
                Provider.OPENAI -> chatWithOpenAi(userText)
                Provider.GEMINI -> chatWithGemini(userText)
                Provider.HUGGINGFACE -> chatWithHuggingFace(userText)
            }
            // 5. Add model reply to the list
            _messages.value = _messages.value + AiMessage(reply, false)
        }
    }

    private suspend fun chatWithOpenAi(userText: String): String = withContext(Dispatchers.IO) {
        val body = """
            {
              "model": "gpt-3.5-turbo",
              "messages": [{"role": "user", "content": "$userText"}]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
//            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        // OkHttp call is synchronous and is now safely run on the Dispatchers.IO thread
        client.newCall(request).execute().use { res ->
            val json = JSONObject(res.body?.string() ?: "{}")
            return@withContext json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "No reply") ?: "No reply"
        }
    }

    private suspend fun chatWithGemini(userText: String): String = withContext(Dispatchers.IO) {
        val body = """
            {
              "contents": [{
                "parts": [{"text": "$userText"}]
              }]
            }
        """.trimIndent()

        val request = Request.Builder()
//            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { res ->
            val json = JSONObject(res.body?.string() ?: "{}")
            return@withContext json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "No reply") ?: "No reply"
        }
    }

    private suspend fun chatWithHuggingFace(userText: String): String = withContext(Dispatchers.IO) {
        val body = """{"inputs": "$userText"}"""

        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/facebook/blenderbot-400M-distill")
//            .addHeader("Authorization", "Bearer ${BuildConfig.HF_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { res ->
            val json = JSONArray(res.body?.string() ?: "[]")
            return@withContext json.optJSONObject(0)?.optString("generated_text", "No reply") ?: "No reply"
        }
    }
}

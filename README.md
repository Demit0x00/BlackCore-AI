# BlackCore AI

Native Android AI chat built with Kotlin and Jetpack Compose.

## Features

### Stage 1
- Dark Compose chat UI with starter prompts
- Provider-aware API key settings with automatic model presets (OpenAI, Google Gemini, Anthropic Claude, Groq, OpenRouter, DeepSeek, Mistral, xAI Grok, Together AI, Cerebras, Fireworks AI, Qwen, Moonshot Kimi, Z.ai GLM, MiniMax)
- Beginner-friendly provider selection and key-prefix detection; model IDs and API endpoints are configured automatically
- Optional system prompt customization
- Streaming responses using Server-Sent Events (SSE)
- Local persistence using Room

### Stage 2
- Multi-conversation history drawer
- Create, switch, rename, and delete conversations
- Room schema migration to preserve existing messages in a legacy conversation
- Markdown rendering (headings, lists, links, tables, emphasis, inline code)
- Fenced code blocks with language label, basic token coloring, text selection, and copy button
- Stop-generation button; partial assistant output is retained when stopping

## Build
Open in Android Studio (JDK 17, Android SDK 35), sync Gradle, and run on Android 8.0+.

GitHub Actions workflow `.github/workflows/android-build.yml` runs a debug APK assembly on pushes and pull requests to `main`.

## Setup
In the app Settings, select the AI provider (key prefixes automatically recognize Gemini, Groq, and OpenRouter), then paste the provider API key. BlackCore AI selects a preset model and endpoint automatically. Most providers use OpenAI-compatible Chat Completions with SSE streaming. Anthropic Claude uses its native Messages streaming API. Provider key-prefix detection recognizes Gemini, Claude, Groq, OpenRouter, xAI, and Fireworks; for other keys, select the provider manually. Model presets are defaults and can change as providers retire models.

## Security
This starter stores the API key in SharedPreferences. This is not production-grade secret storage. Never commit a real API key. For public distribution, use an authenticated backend proxy with server-side credentials.

## Status
Stage 2 source is committed. The CI build is configured but has not yet completed successfully in this session. Device testing and validation against the selected API provider are still required.

# BlackCore AI

Native Android AI chat built with Kotlin and Jetpack Compose.

## Features

### Stage 1
- Dark Compose chat UI with starter prompts
- OpenAI-compatible chat completions endpoint settings
- API key, model identifier, and system prompt configuration
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
In the app Settings, configure:
- Chat completions URL (default: `https://api.openai.com/v1/chat/completions`)
- API key
- Model identifier
- System prompt (optional)

The endpoint must support the OpenAI Chat Completions format and SSE streaming.

## Security
This starter stores the API key in SharedPreferences. This is not production-grade secret storage. Never commit a real API key. For public distribution, use an authenticated backend proxy with server-side credentials.

## Status
Stage 2 source is committed. The CI build is configured but has not yet completed successfully in this session. Device testing and validation against the selected API provider are still required.

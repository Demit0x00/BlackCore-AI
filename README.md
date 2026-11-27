# BlackCore AI

A native Android AI chat starter built with Kotlin and Jetpack Compose.

## Implemented
- Dark Compose chat interface and prompt suggestions
- OpenAI-compatible Chat Completions endpoint settings
- Configurable API key, model ID, and system prompt
- Streaming responses using Server-Sent Events (SSE)
- Local message persistence using Room
- New chat action to clear the current conversation

## Build
Open in Android Studio (JDK 17, Android SDK 35), sync Gradle, and run the app on Android 8.0+.

## Setup
Open Settings in the app and enter:
- Endpoint URL (default: `https://api.openai.com/v1/chat/completions`)
- API key
- Model identifier
- Optional system prompt

The provider must support the OpenAI Chat Completions request format and streaming SSE.

## Security notice
This starter stores the API key in local SharedPreferences. It is not production-grade secret storage. Never commit real API keys. For a public release, use a secure backend proxy with server-side credentials.

## Project status
Initial source is committed. It has not yet been compiled or tested on a device; please run a Gradle build and validate provider compatibility before release.

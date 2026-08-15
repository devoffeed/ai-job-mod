# AI Bots

AI-powered NPCs and chat bot for Minecraft **1.20.1 (Forge)** and **1.21.4 (NeoForge)**.

Talk to NPCs in the world, or use `/ask <message>` to chat with an AI assistant backed by
**OpenAI**, **Google Gemini**, or **OpenRouter**. Requests are fully asynchronous — the game
thread is never blocked.

## Features

- **AI NPCs** — spawn a peaceful humanoid NPC, right-click to start a conversation.
- **Chat bot** — `/ask <message>` asks your configured AI provider.
- **Per-NPC personas** — while talking to an NPC, `/ask` messages route to that NPC with its
  personality prompt.
- **In-game GUI** — press `M` to open a chat panel; a Settings panel picks the provider and
  enters the API key/model without touching config files.
- **3 providers** — OpenAI, Google Gemini, OpenRouter (single config file).
- **Local proxy mode** — the `Local` provider points at `http://localhost:8844` (a tiny Go
  proxy that forwards to an AI API). Works on **any** server, even vanilla ones where the
  mod's server side is absent: when the server does not support the mod's channels, the
  client talks to the proxy directly. The API key stays in the proxy, never in the game.
- **No external libraries** — plain `java.net.http` + bundled Gson, small jar.
- **Apache 2.0 licensed**.

## Requirements

| Edition | Loader | Minecraft | Java |
|---------|--------|-----------|------|
| `aibots-forge-1.0.0.jar` | Forge 47.2.0+ | 1.20.1 | 17 |
| `aibots-neoforge-1.0.0.jar` | NeoForge 21.1.248+ | 1.21.4 | 21 |

## Installation

1. Drop the matching jar into your `mods/` folder.
2. Launch the game once — it creates `config/aibots.json`.
3. Open `config/aibots.json` and set your provider + API key.
4. Run `/aibots reload` in-game (or restart) to apply.

## Configuration (`config/aibots.json`)

```jsonc
{
  "provider": "openrouter",          // "openai" | "gemini" | "openrouter"
  "openaiApiKey": "",                // sk-...
  "openaiModel": "gpt-4o-mini",
  "openaiBaseUrl": "https://api.openai.com/v1",
  "geminiApiKey": "",                // AIza...
  "geminiModel": "gemini-2.0-flash",
  "openrouterApiKey": "",            // sk-or-...
  "openrouterModel": "openai/gpt-4o-mini",
  "systemPrompt": "You are a friendly assistant living inside Minecraft...",
  "timeoutSeconds": 60,
  "maxTokens": 512
}
```

Only the fields of the selected `provider` are used.

## Commands

| Command | Description |
|---------|-------------|
| `/ask <message>` | Ask the AI. If you're talking to an NPC, it answers as that NPC. |
| `/aibots stop` | Stop the current conversation. |
| `/aibots reload` | Reload `config/aibots.json` (op-permission). |

## In-game GUI

Press `M` to open the chat panel (toggle — press again to close). The **Settings** button
opens a panel where you can pick the provider, paste an API key, and set a model override.
Closing the panel from Settings auto-saves the entered values. On servers without the mod,
select the **Local** provider and run `proxy-go/aibots-proxy.exe` (needs `proxy-go/main.json`
with your Gemini key, see `main.json.example`).

## Spawning NPCs

An NPC entity (`aibots:ai_bot`) is registered. Spawn it with e.g.:

```
/summon aibots:ai_bot ~ ~ ~ {CustomName:'"Bob"'}
```

Right-click it to start a conversation, then use `/ask`.

## Building from source

```
# Forge 1.20.1 (needs JDK 17)
cd forge
gradlew.bat build

# NeoForge 1.21.4 (needs JDK 21)
cd neoforge
gradlew.bat build
```

Jars land in `forge/build/libs/` and `neoforge/build/libs/`.

## Local proxy (Go)

```
cd proxy-go
go build -o aibots-proxy.exe .
# copy main.json.example to main.json, paste your Gemini key
aibots-proxy.exe
```

`POST http://localhost:8844/generate` with `{"system": "...", "prompt": "..."}` returns
`{"reply": "..."}`. The proxy runs 3 worker goroutines.

> **Security disclaimer:** `proxy-go/main.json` holds your real Gemini API key. It is
> git-ignored and must **never** be committed or pushed to any repository. Only commit the
> placeholder file `proxy-go/main.json.example`. If you ever expose a key (paste it in a chat,
> log, or commit), **revoke it immediately** at https://console.cloud.google.com/apis/credentials
> and generate a new one. The key lives only in the local proxy, never inside Minecraft or the
> mod source.

## License

Licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE).

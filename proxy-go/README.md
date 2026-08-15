# AI Bots local proxy (Go)

Sits on `localhost:8844`, forwards chat requests to the Google Gemini API.
The API key lives here only — the Minecraft mod never sees it.

## Build

```
go build -o aibots-proxy.exe .
```

## Run

Put your API key in `main.json` (next to the exe), then:

```
aibots-proxy.exe
```

Optional env vars override `main.json`:

| Env              | JSON field | Default                                             |
|------------------|------------|-----------------------------------------------------|
| `GEMINI_API_KEY` | `apiKey`   | required                                            |
| `GEMINI_MODEL`   | `model`    | `gemini-2.0-flash`                                  |
| `GEMINI_API_URL` | `apiUrl`   | `https://generativelanguage.googleapis.com/v1beta`  |
| `PROXY_ADDR`     | `addr`     | `localhost:8844`                                    |

`main.json` example:

```json
{
  "apiKey": "AIzaSy...",
  "model": "gemini-2.0-flash",
  "apiUrl": "https://generativelanguage.googleapis.com/v1beta",
  "addr": "localhost:8844"
}
```

The proxy runs 3 worker goroutines to process asks concurrently.

## Contract (used by the mod)

```
POST /generate
{"system": "...", "prompt": "..."}

200 {"reply": "..."}
```

`GET /health` returns `{"ok":true}`.

package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

// config mirrors what the mod sends: {"system": "...", "prompt": "..."}
type askRequest struct {
	System string `json:"system"`
	Prompt string `json:"prompt"`
}

type askReply struct {
	Reply string `json:"reply"`
}

// geminiRequest is the wire format for generateContent.
type geminiRequest struct {
	SystemInstruction *content `json:"system_instruction,omitempty"`
	Contents          []content `json:"contents"`
	GenerationConfig  generationConfig `json:"generationConfig,omitempty"`
}

type content struct {
	Parts []part `json:"parts"`
}

type part struct {
	Text string `json:"text"`
}

type generationConfig struct {
	MaxOutputTokens int     `json:"maxOutputTokens,omitempty"`
	Temperature     float64 `json:"temperature,omitempty"`
}

type geminiResponse struct {
	Candidates []struct {
		Content struct {
			Parts []part `json:"parts"`
		} `json:"content"`
	} `json:"candidates"`
}

var (
	apiKey     string
	model      = "gemini-2.0-flash"
	apiURL     = "https://generativelanguage.googleapis.com/v1beta"
	addr       = "localhost:8844"
	httpClient = &http.Client{Timeout: 90 * time.Second}
	workerCount = 3
)

// fileConfig holds settings loaded from main.json (optional). Env vars override.
type fileConfig struct {
	APIKey string `json:"apiKey"`
	Model  string `json:"model"`
	APIURL string `json:"apiUrl"`
	Addr   string `json:"addr"`
}

type askJob struct {
	ctx    context.Context
	system string
	prompt string
	reply  chan string
	err    chan error
}

func loadConfig() {
	data, err := os.ReadFile("main.json")
	if err != nil {
		if !errors.Is(err, os.ErrNotExist) {
			log.Printf("warning: cannot read main.json: %v", err)
		}
	} else {
		var cfg fileConfig
		if err := json.Unmarshal(data, &cfg); err != nil {
			log.Fatalf("main.json is not valid JSON: %v", err)
		}
		if cfg.APIKey != "" {
			apiKey = cfg.APIKey
		}
		if cfg.Model != "" {
			model = cfg.Model
		}
		if cfg.APIURL != "" {
			apiURL = cfg.APIURL
		}
		if cfg.Addr != "" {
			addr = cfg.Addr
		}
	}
	if v := os.Getenv("GEMINI_API_KEY"); v != "" {
		apiKey = v
	}
	if v := os.Getenv("GEMINI_MODEL"); v != "" {
		model = v
	}
	if v := os.Getenv("GEMINI_API_URL"); v != "" {
		apiURL = v
	}
	if v := os.Getenv("PROXY_ADDR"); v != "" {
		addr = v
	}
}

func main() {
	loadConfig()
	if apiKey == "" {
		log.Fatal("no API key: set GEMINI_API_KEY or put \"apiKey\" in main.json")
	}

	// 3 worker goroutines process asks off the HTTP goroutines.
	jobs := make(chan *askJob, 16)
	for i := 0; i < workerCount; i++ {
		go worker(i+1, jobs)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/generate", handleGenerate(jobs))
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"ok":true}`)
	})

	log.Printf("proxy listening on %s (model=%s, workers=%d)", addr, model, workerCount)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatal(err)
	}
}

func worker(id int, jobs <-chan *askJob) {
	for job := range jobs {
		reply, err := askGemini(job.ctx, job.system, job.prompt)
		job.reply <- reply
		job.err <- err
	}
}

func handleGenerate(jobs chan<- *askJob) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			writeError(w, http.StatusMethodNotAllowed, "use POST")
			return
		}

		var req askRequest
		if err := json.NewDecoder(io.LimitReader(r.Body, 1<<20)).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "bad JSON: "+err.Error())
			return
		}
		if req.Prompt == "" {
			writeError(w, http.StatusBadRequest, "prompt is empty")
			return
		}

		job := &askJob{
			ctx:    r.Context(),
			system: req.System,
			prompt: req.Prompt,
			reply:  make(chan string, 1),
			err:    make(chan error, 1),
		}
		jobs <- job

		reply := <-job.reply
		err := <-job.err
		if err != nil {
			log.Printf("gemini error: %v", err)
			writeError(w, http.StatusBadGateway, err.Error())
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(askReply{Reply: reply})
	}
}

func askGemini(ctx context.Context, system, prompt string) (string, error) {
	body, err := json.Marshal(geminiRequest{
		SystemInstruction: maybeContent(system),
		Contents: []content{{
			Parts: []part{{Text: prompt}},
		}},
		GenerationConfig: generationConfig{
			MaxOutputTokens: 512,
			Temperature:     0.7,
		},
	})
	if err != nil {
		return "", err
	}

	base := strings.TrimRight(apiURL, "/")
	url := fmt.Sprintf(
		"%s/models/%s:generateContent?key=%s",
		base, model, apiKey)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := httpClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("gemini status %d: %s", resp.StatusCode, truncate(string(respBody), 300))
	}

	var parsed geminiResponse
	if err := json.Unmarshal(respBody, &parsed); err != nil {
		return "", fmt.Errorf("bad gemini JSON: %w", err)
	}
	if len(parsed.Candidates) == 0 || len(parsed.Candidates[0].Content.Parts) == 0 {
		return "", errors.New("gemini returned no candidates")
	}

	var sb bytes.Buffer
	for _, p := range parsed.Candidates[0].Content.Parts {
		sb.WriteString(p.Text)
	}
	return sb.String(), nil
}

func maybeContent(system string) *content {
	if system == "" {
		return nil
	}
	return &content{Parts: []part{{Text: system}}}
}

func writeError(w http.ResponseWriter, status int, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"error": msg})
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "..."
}

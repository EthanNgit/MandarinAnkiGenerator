package tts

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"os"
	"strings"
	"time"
	"tts/src/storage"

	"golang.org/x/oauth2/google"
)

type GoogleTTSProvider struct {
	languageCode string
	maleVoice    string
	femaleVoice  string
	ttsConfig    TTSConfig
	blobDatabase storage.BlobDatabase
	accessToken  string
}

func NewGoogleTTSProvider(languageCode, maleVoice, femaleVoice string, config TTSConfig) (*GoogleTTSProvider, error) {
	blobDB, err := storage.ConnectToAzurite()
	if err != nil {
		return nil, fmt.Errorf("failed to connect to blob storage: %w", err)
	}

	return &GoogleTTSProvider{
		languageCode: languageCode,
		maleVoice:    maleVoice,
		femaleVoice:  femaleVoice,
		ttsConfig:    config,
		blobDatabase: blobDB,
	}, nil
}

func (g *GoogleTTSProvider) Process(words []Word, gender string) ([]string, error) {
	if g.accessToken == "" {
		token, err := g.getAccessToken()
		if err != nil {
			return nil, err
		}
		g.accessToken = token
	}

	var voice string
	switch strings.ToLower(gender) {
	case "male":
		voice = g.maleVoice
	case "female":
		voice = g.femaleVoice
	case "any":
		rand.Seed(time.Now().UnixNano())
		flip := rand.Intn(2)

		if flip == 0 {
			voice = g.maleVoice
		} else {
			voice = g.femaleVoice
		}
	default:
		return nil, fmt.Errorf("invalid gender provided: %s", gender)
	}

	audio, err := g.synthesizeSpeech(words, voice, g.accessToken)
	if err != nil {
		if strings.Contains(err.Error(), "401") || strings.Contains(err.Error(), "403") {
			token, tokenErr := g.getAccessToken()
			if tokenErr != nil {
				return nil, tokenErr
			}
			g.accessToken = token

			audio, err = g.synthesizeSpeech(words, voice, g.accessToken)
			if err != nil {
				return nil, fmt.Errorf("failed after token refresh: %w", err)
			}
		} else {
			return nil, fmt.Errorf("synthesize error: %w", err)
		}
	}

	chunks, err := splitOnSilence(g.ttsConfig, audio)
	if err != nil {
		return nil, fmt.Errorf("split error: %w", err)
	}

	if len(chunks) != len(words) {
		fmt.Printf("Warning: %d chunks for %d words\n", len(chunks), len(words))
	}

	var urls []string
	for i, chunk := range chunks {
		fileName := fmt.Sprintf("tts/%d.wav", words[i].Id)
		url, err := g.blobDatabase.InsertTTSAudio(fileName, chunk.Data)
		if err != nil {
			return nil, fmt.Errorf("upload failed %d: %w", i, err)
		}
		urls = append(urls, url)
	}

	return urls, nil
}

func (g *GoogleTTSProvider) synthesizeSpeech(words []Word, voice, accessToken string) ([]byte, error) {
	var ssmlParts []string
	for _, word := range words {
		ssmlParts = append(ssmlParts, fmt.Sprintf(`<phoneme alphabet="pinyin" ph="%s">%s</phoneme><break time="%dms"/>`,
			word.Pronunciation, word.Text, g.ttsConfig.BreakDurationMs))
	}
	ssmlText := "<speak>" + strings.Join(ssmlParts, "") + "</speak>"
	ssmlText = strings.Replace(ssmlText, fmt.Sprintf(`<break time="%dms"/></speak>`,
		g.ttsConfig.BreakDurationMs), "</speak>", 1)

	requestBody := map[string]interface{}{
		"input": map[string]string{"ssml": ssmlText},
		"voice": map[string]string{
			"languageCode": g.languageCode,
			"name":         voice,
		},
		"audioConfig": map[string]string{"audioEncoding": "LINEAR16"},
	}

	jsonBody, err := json.Marshal(requestBody)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequest("POST", "https://texttospeech.googleapis.com/v1/text:synthesize", bytes.NewBuffer(jsonBody))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+accessToken)

	resp, err := (&http.Client{}).Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("API error %d: %s", resp.StatusCode, body)
	}

	var responseJson map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&responseJson); err != nil {
		return nil, err
	}

	audioContent, ok := responseJson["audioContent"].(string)
	if !ok {
		return nil, fmt.Errorf("invalid response")
	}

	return base64.StdEncoding.DecodeString(audioContent)
}

func (g *GoogleTTSProvider) getAccessToken() (string, error) {
	data, err := os.ReadFile("/config/google_service.json")
	if err != nil {
		return "", fmt.Errorf("service account read error: %w", err)
	}

	conf, err := google.JWTConfigFromJSON(data, "https://www.googleapis.com/auth/cloud-platform")
	if err != nil {
		return "", fmt.Errorf("JWT config error: %w", err)
	}

	token, err := conf.TokenSource(context.Background()).Token()
	if err != nil {
		return "", fmt.Errorf("token error: %w", err)
	}

	return token.AccessToken, nil
}

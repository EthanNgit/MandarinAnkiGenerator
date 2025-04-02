package tts

import (
	"bytes"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"
	"tts/src/storage"
)

type AzureTTSProvider struct {
	languageCode string
	maleVoice    string
	femaleVoice  string
	ttsConfig    TTSConfig
	blobDatabase storage.BlobDatabase
	azureKey     string
	azureRegion  string
}

func NewAzureTTSProvider(languageCode, maleVoice, femaleVoice string, config TTSConfig) (*AzureTTSProvider, error) {
	blobDB, err := storage.ConnectToAzurite()
	if err != nil {
		return nil, fmt.Errorf("failed to connect to blob storage: %w", err)
	}

	azureKey := os.Getenv("AZURE_API_KEY")
	if azureKey == "" {
		return nil, fmt.Errorf("invalid azure api key")
	}

	azureRegion := os.Getenv("AZURE_REGION")
	if azureRegion == "" {
		return nil, fmt.Errorf("invalid azure region")
	}

	return &AzureTTSProvider{
		languageCode: languageCode,
		maleVoice:    maleVoice,
		femaleVoice:  femaleVoice,
		ttsConfig:    config,
		blobDatabase: blobDB,
		azureKey:     azureKey,
		azureRegion:  azureRegion,
	}, nil
}

func (a *AzureTTSProvider) Process(words []Word, gender string, sentence bool) ([]string, error) {
	var voice string
	switch strings.ToLower(gender) {
	case "male":
		voice = a.maleVoice
	case "female":
		voice = a.femaleVoice
	case "any":
		rand.Seed(time.Now().UnixNano())
		flip := rand.Intn(2)

		if flip == 0 {
			voice = a.maleVoice
		} else {
			voice = a.femaleVoice
		}
	default:
		return nil, fmt.Errorf("invalid gender provided: %s", gender)
	}

	// Synthesize speech using Azure REST API.
	audio, err := a.synthesizeSpeech(words, voice)
	if err != nil {
		return nil, fmt.Errorf("failed to synthesize speech: %w", err)
	}

	// Split audio into chunks. This reuses the splitting logic (e.g. splitOnSilence) shared with Google.
	chunks, err := splitOnSilence(a.ttsConfig, audio)
	if err != nil {
		return nil, fmt.Errorf("failed to split audio: %w", err)
	}

	if len(chunks) != len(words) {
		fmt.Printf("Warning: %d chunks for %d words\n", len(chunks), len(words))
	}

	// Upload each audio chunk to blob storage.
	var urls []string
	for i, chunk := range chunks {
		path := ""
		if sentence {
			path = "tts/sentence/"
		} else {
			path = "tts/word/"
		}
		fileName := fmt.Sprintf("%s%d.wav", path, words[i].Id)
		url, err := a.blobDatabase.InsertTTSAudio(fileName, chunk.Data)
		if err != nil {
			return nil, fmt.Errorf("failed to upload chunk %d: %w", i, err)
		}
		urls = append(urls, url)
	}

	return urls, nil
}

// synthesizeSpeech builds an SSML payload, calls the Azure TTS REST API, and returns the raw audio bytes.
func (a *AzureTTSProvider) synthesizeSpeech(words []Word, voice string) ([]byte, error) {
	ssml := fmt.Sprintf(`<speak xmlns='http://www.w3.org/2001/10/synthesis' xmlns:mstts='http://www.w3.org/2001/mstts' xmlns:emo='http://www.w3.org/2009/10/emotionml' version='1.0' xml:lang='%s'>
		<voice name='%s'>
			<lang xml:lang="%s"><prosody rate='-20.00%%' pitch='default' contour="">`, a.languageCode, voice, a.languageCode)

	for _, word := range words {
		if word.Pronunciation != "" {
			ssml += fmt.Sprintf(`<phoneme alphabet='sapi' ph='%s'>%s</phoneme><break time='%dms'/>`,
				addSpaceBeforeNumbers(word.Pronunciation), word.Text, a.ttsConfig.BreakDurationMs)
		} else {
			ssml += fmt.Sprintf("%s<break time='%dms'/>", word.Text, a.ttsConfig.BreakDurationMs)
		}
	}

	ssml += `</prosody></lang></voice></speak>`

	fmt.Println("Generated SSML:") // Add this line
	fmt.Println(ssml)              // Add this line

	url := fmt.Sprintf("https://%s.tts.speech.microsoft.com/cognitiveservices/v1", a.azureRegion)

	req, err := http.NewRequest("POST", url, bytes.NewBufferString(ssml))
	if err != nil {
		return nil, err
	}

	req.Header.Set("Content-Type", "application/ssml+xml")
	req.Header.Set("Ocp-Apim-Subscription-Key", a.azureKey)
	req.Header.Set("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm")
	req.Header.Set("User-Agent", "tts")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("azure TTS API returned status: %d, body: %s", resp.StatusCode, string(bodyBytes))
	}

	audioData, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	return audioData, nil
}

func addSpaceBeforeNumbers(input string) string {
	re := regexp.MustCompile(`(\D)(\d)`)
	result := re.ReplaceAllString(input, `$1 $2`)

	re2 := regexp.MustCompile(`(\d)([a-zA-Z])`)
	result = re2.ReplaceAllString(result, `$1 $2`)

	return result
}

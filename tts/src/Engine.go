package main

import (
	"fmt"
	"os"
	"tts/src/tts"
)

type Engine struct {
	ttsProvider tts.TTSProvider
	ttsConfig   tts.TTSConfig
}

func NewEngine(provider string, config *tts.TTSConfig) (*Engine, error) {
	if config == nil {
		config = &tts.TTSConfig{
			BreakDurationMs: 500,
			SilenceThreshDB: -40.0,
			MinSilenceLen:   350,
			KeepSilence:     200,
			SeekStep:        5,
		}
	}

	configuration := *config
	var ttsProvider tts.TTSProvider
	var err error

	switch provider {
	case "google":
		male := os.Getenv("GOOGLE_MALE_VOICE")
		female := os.Getenv("GOOGLE_FEMALE_VOICE")

		if male == "" {
			male = "cmn-CN-Wavenet-C"
		}
		if female == "" {
			female = "cmn-CN-Wavenet-A"
		}

		ttsProvider, err = tts.NewGoogleTTSProvider("cmn-Hans-CN", male, female, configuration)
		if err != nil {
			return nil, fmt.Errorf("failed to initialize google tts %v", err)
		}
	case "azure":
		male := os.Getenv("AZURE_MALE_VOICE")
		female := os.Getenv("AZURE_FEMALE_VOICE")

		if male == "" {
			male = "zh-CN-YunxiaoMultilingualNeural"
		}
		if female == "" {
			female = "zh-CN-XiaochenMultilingualNeural"
		}

		ttsProvider, err = tts.NewAzureTTSProvider("zh-CN", male, female, configuration)
		if err != nil {
			return nil, fmt.Errorf("failed to initialize azure tts %v", err)
		}
	default:
		return nil, fmt.Errorf("invalid tts provider")
	}
	e := &Engine{
		ttsProvider: ttsProvider,
		ttsConfig:   configuration,
	}

	return e, nil
}

func (e *Engine) BatchProcessWords(words []tts.Word, gender string) ([]string, error) {
	return e.ttsProvider.Process(words, gender)
}

func (e *Engine) Close() {

}

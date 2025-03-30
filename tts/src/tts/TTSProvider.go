package tts

// Word represents a word with its pronunciation
type Word struct {
	Id            int    `json:"context_id"`
	Text          string `json:"text"`
	Pronunciation string `json:"pronunciation"`
}

// TTSConfig holds configuration for TTS
type TTSConfig struct {
	BreakDurationMs int
	SilenceThreshDB float64
	MinSilenceLen   int
	KeepSilence     int
	SeekStep        int
}

type TTSProvider interface {
	Process(words []Word, gender string) ([]string, error)
}

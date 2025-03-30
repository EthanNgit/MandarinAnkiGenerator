package tts

import (
	"bytes"
	"encoding/binary"
	"math"
)

type AudioSegment struct {
	Data       []byte
	SampleRate int
	Channels   int
}

func splitOnSilence(config TTSConfig, audioData []byte) ([]AudioSegment, error) {
	sampleRate := 24000
	channels := 1

	minSilenceLen := config.MinSilenceLen

	silenceThresh := int16(math.Pow(10, config.SilenceThreshDB/20) * 32768)
	minSilenceSamples := minSilenceLen * sampleRate / 1000
	keepSilenceSamples := config.KeepSilence * sampleRate / 1000
	seekStepSamples := config.SeekStep

	samples := make([]int16, len(audioData)/2)
	if err := binary.Read(bytes.NewReader(audioData), binary.LittleEndian, &samples); err != nil {
		return nil, err
	}

	silentRegions := []struct{ start, end int }{}
	inSilence := false
	silenceStart := 0

	for i := 0; i < len(samples); i += channels * seekStepSamples {
		var sum int64
		for ch := 0; ch < channels; ch++ {
			if i+ch < len(samples) {
				sum += int64(math.Abs(float64(samples[i+ch])))
			}
		}
		avgAmplitude := int16(sum / int64(channels))

		if avgAmplitude <= silenceThresh {
			if !inSilence {
				inSilence = true
				silenceStart = i
			}
		} else if inSilence {
			if silenceLen := i - silenceStart; silenceLen >= minSilenceSamples*channels {
				silentRegions = append(silentRegions, struct{ start, end int }{silenceStart, i})
			}
			inSilence = false
		}
	}

	if inSilence && (len(samples)-silenceStart) >= minSilenceSamples*channels {
		silentRegions = append(silentRegions, struct{ start, end int }{silenceStart, len(samples)})
	}

	if len(silentRegions) == 0 {
		wavData := addWAVHeader(audioData, sampleRate, channels)
		return []AudioSegment{{Data: wavData, SampleRate: sampleRate, Channels: channels}}, nil
	}

	var chunks []AudioSegment

	if silentRegions[0].start > 0 {
		end := silentRegions[0].start + keepSilenceSamples*channels
		if end > len(samples) {
			end = len(samples)
		}
		chunk := samples[:end]
		wavData := createWAV(chunk, sampleRate, channels)
		chunks = append(chunks, AudioSegment{Data: wavData, SampleRate: sampleRate, Channels: channels})
	}

	for i := 0; i < len(silentRegions)-1; i++ {
		start := silentRegions[i].end - keepSilenceSamples*channels
		if start < 0 {
			start = 0
		}
		end := silentRegions[i+1].start + keepSilenceSamples*channels
		if end > len(samples) {
			end = len(samples)
		}
		chunk := samples[start:end]
		wavData := createWAV(chunk, sampleRate, channels)
		chunks = append(chunks, AudioSegment{Data: wavData, SampleRate: sampleRate, Channels: channels})
	}

	if lastEnd := silentRegions[len(silentRegions)-1].end; lastEnd < len(samples) {
		start := lastEnd - keepSilenceSamples*channels
		if start < 0 {
			start = 0
		}
		chunk := samples[start:]
		wavData := createWAV(chunk, sampleRate, channels)
		chunks = append(chunks, AudioSegment{Data: wavData, SampleRate: sampleRate, Channels: channels})
	}

	return chunks, nil
}

func createWAV(samples []int16, sampleRate, channels int) []byte {
	var pcmBuf bytes.Buffer
	binary.Write(&pcmBuf, binary.LittleEndian, samples)
	return addWAVHeader(pcmBuf.Bytes(), sampleRate, channels)
}

func addWAVHeader(pcmData []byte, sampleRate, channels int) []byte {
	header := bytes.NewBuffer(nil)
	header.Write([]byte("RIFF"))
	binary.Write(header, binary.LittleEndian, uint32(36+len(pcmData))) // ChunkSize
	header.Write([]byte("WAVEfmt "))
	binary.Write(header, binary.LittleEndian, uint32(16)) // Subchunk1Size
	binary.Write(header, binary.LittleEndian, uint16(1))  // AudioFormat
	binary.Write(header, binary.LittleEndian, uint16(channels))
	binary.Write(header, binary.LittleEndian, uint32(sampleRate))
	binary.Write(header, binary.LittleEndian, uint32(sampleRate*channels*2)) // ByteRate
	binary.Write(header, binary.LittleEndian, uint16(channels*2))            // BlockAlign
	binary.Write(header, binary.LittleEndian, uint16(16))                    // BitsPerSample
	header.Write([]byte("data"))
	binary.Write(header, binary.LittleEndian, uint32(len(pcmData))) // Subchunk2Size

	return append(header.Bytes(), pcmData...)
}

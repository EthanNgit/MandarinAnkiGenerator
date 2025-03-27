import json
import platform
import random
import os
from typing import List, Dict

import azure.cognitiveservices.speech as speechsdk
from google.cloud import texttospeech
from pydub import AudioSegment
from pydub.silence import split_on_silence

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel


# Determine the FFmpeg binary based on environment and OS
FFMPEG_PATH = os.getenv("FFMPEG_PATH")
if not FFMPEG_PATH:
    if platform.system() == "Windows":
        FFMPEG_PATH = os.path.join(os.getcwd(), "ffmpeg.exe")
    else:
        FFMPEG_PATH = "ffmpeg"

# Set the converter for pydub to use the correct FFmpeg binary
AudioSegment.converter = FFMPEG_PATH

# Configuration Constants
class Config:
    # TTS Provider Settings
    class TTS:
        PROVIDER = "azure"  # "google" or "azure"
        OUTPUT_FOLDER = "outputs"

        # Google Cloud Settings
        GOOGLE = {
            "VOICE": "cmn-CN-Wavenet-A",
            "CREDENTIALS_PATH": "google_service.json",
            "LANGUAGE_CODE": "cmn-CN"
        }

        # Azure Settings
        AZURE = {
            "KEY": os.getenv("AZURE_API_KEY"),
            "REGION": "eastus",
            "VOICE": "zh-CN-Xiaochen:DragonHDLatestNeural"
        }

    # Audio Processing Settings
    class Audio:
        BREAK_DURATION_MS = 500  # Pause between words
        MIN_SILENCE_LEN_MS = 350  # Silence detection threshold
        SILENCE_THRESH_DBFS = -40  # dBFS for silence detection
        SAFETY_BUFFER_MS = 50  # Overlap protection
        FADE_OUT_MS = 50  # Fade out duration
        NORMALIZATION = True

        # FFmpeg Configuration
        FFMPEG_PATH = FFMPEG_PATH

    # Word Selection Settings
    class Words:
        MANDARIN_WORDS = [
            "你好", "再见", "谢谢", "请", "不客气",
            "一", "二", "三", "四", "五",
            "六", "七", "八", "九", "十",
            "红色", "蓝色", "绿色", "黄色", "黑色",
            "星期一", "星期二", "星期三", "星期四", "星期五"
        ]
        SAMPLE_SIZE = 5  # Number of words to randomly select


def google_tts(words: List[str]) -> tuple:
    """Generate Google Cloud TTS audio with SSML breaks"""
    client = texttospeech.TextToSpeechClient()

    ssml_parts = [f'<prosody rate="0.95">{word}</prosody><break time="{Config.Audio.BREAK_DURATION_MS}ms"/>'
                  for word in words]
    ssml_text = f"<speak>{''.join(ssml_parts)}</speak>".replace(
        f"<break time=\"{Config.Audio.BREAK_DURATION_MS}ms\"/></speak>", "</speak>")

    response = client.synthesize_speech(
        input=texttospeech.SynthesisInput(ssml=ssml_text),
        voice=texttospeech.VoiceSelectionParams(
            language_code=Config.TTS.GOOGLE["LANGUAGE_CODE"],
            name=Config.TTS.GOOGLE["VOICE"]
        ),
        audio_config=texttospeech.AudioConfig(
            audio_encoding=texttospeech.AudioEncoding.LINEAR16
        )
    )
    return response.audio_content, None


def process_google(audio_data: bytes, words: List[str]) -> List[AudioSegment]:
    """Process Google TTS output with silence detection"""
    with open("temp.wav", "wb") as f:
        f.write(audio_data)

    audio = AudioSegment.from_wav("temp.wav")
    if Config.Audio.NORMALIZATION:
        audio = audio.normalize()

    chunks = split_on_silence(
        audio,
        min_silence_len=300 if "Wavenet" in Config.TTS.GOOGLE["VOICE"] else 350,
        silence_thresh=Config.Audio.SILENCE_THRESH_DBFS,
        keep_silence=200,
        seek_step=5
    )

    # Fallback to time-based splitting if silence detection fails
    if len(chunks) != len(words):
        chunk_length = len(audio) // len(words)
        chunks = [audio[i * chunk_length:(i + 1) * chunk_length] for i in range(len(words))]

    return chunks


def azure_tts(words: List[str]) -> tuple:
    """Generate audio with optimized breaks and boundary tracking"""
    speech_config = speechsdk.SpeechConfig(
        subscription=Config.TTS.AZURE["KEY"],
        region=Config.TTS.AZURE["REGION"]
    )
    speech_config.set_speech_synthesis_output_format(
        speechsdk.SpeechSynthesisOutputFormat.Riff16Khz16BitMonoPcm
    )

    word_boundaries = []

    def boundary_cb(evt: speechsdk.SessionEventArgs):
        offset_ms = evt.audio_offset // 10000  # 100ns to ms
        duration_ms = int(evt.duration.total_seconds() * 1000)
        word_boundaries.append({
            'start': offset_ms,
            'end': offset_ms + duration_ms,
            'text': evt.text.strip()
        })

    # SSML with dual splitting strategy
    ssml = f"""<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' 
              xmlns:mstts='http://www.w3.org/2001/mstts' xml:lang='zh-CN'>
              <voice name='{Config.TTS.AZURE["VOICE"]}'>
              <prosody rate='0.85'>"""

    for word in words:
        ssml += f"{word}<break time='{Config.Audio.BREAK_DURATION_MS}ms'/>"
    ssml += "</prosody></voice></speak>"

    synthesizer = speechsdk.SpeechSynthesizer(speech_config=speech_config, audio_config=None)
    synthesizer.synthesis_word_boundary.connect(boundary_cb)

    result = synthesizer.speak_ssml_async(ssml).get()

    print(f"Boundaries captured: {len(word_boundaries)}")
    return result.audio_data, word_boundaries


def process_azure(audio_data: bytes, word_boundaries: List[Dict], words: List[str]) -> List[AudioSegment]:
    """Hybrid processing with priority: Boundaries > Silence > Time"""
    with open("temp.wav", "wb") as f:
        f.write(audio_data)

    audio = AudioSegment.from_wav("temp.wav")

    # 1. Try word boundaries first
    if len(word_boundaries) == len(words):
        print("Using word boundaries")
        return process_with_boundaries(audio, word_boundaries, words)

    # 2. Attempt silence detection
    silence_chunks = split_on_silence(
        audio,
        min_silence_len=Config.Audio.MIN_SILENCE_LEN_MS,
        silence_thresh=Config.Audio.SILENCE_THRESH_DBFS,
        keep_silence=200,
        seek_step=5
    )

    if len(silence_chunks) == len(words):
        print("Using silence detection")
        return [c.fade_out(Config.Audio.FADE_OUT_MS).normalize() for c in silence_chunks]

    # 3. Fallback to time-based with buffers
    print("Using time-based with overlap protection")
    return split_with_safety(audio, words)


def process_with_boundaries(audio: AudioSegment, boundaries: List[Dict], words: List[str]) -> List[AudioSegment]:
    """Boundary-based processing with validation"""
    chunks = []
    for i in range(len(words)):
        current = boundaries[i]
        next_start = boundaries[i + 1]['start'] if i < len(words) - 1 else len(audio)

        # Calculate safe range
        safe_start = max(0, current['start'] - Config.Audio.SAFETY_BUFFER_MS)
        safe_end = min(current['end'] + Config.Audio.BREAK_DURATION_MS, next_start)
        safe_end = min(safe_end, len(audio))

        chunk = audio[safe_start:safe_end]
        chunk = chunk.fade_out(Config.Audio.FADE_OUT_MS)

        if Config.Audio.NORMALIZATION:
            chunk = chunk.normalize()

        # Validate text alignment
        if boundaries[i]['text'] != words[i]:
            print(f"⚠️ Boundary mismatch: {boundaries[i]['text']} vs {words[i]}")

        chunks.append(chunk)
    return chunks


def split_with_safety(audio: AudioSegment, words: List[str]) -> List[AudioSegment]:
    """Time-based with intelligent buffers"""
    chunk_len = len(audio) // len(words)
    return [
        audio[
        max(0, i * chunk_len - Config.Audio.SAFETY_BUFFER_MS):
        min(len(audio), (i + 1) * chunk_len + Config.Audio.SAFETY_BUFFER_MS)
        ]
        .fade_out(Config.Audio.FADE_OUT_MS)
        .normalize() if Config.Audio.NORMALIZATION else
        audio[
        max(0, i * chunk_len - Config.Audio.SAFETY_BUFFER_MS):
        min(len(audio), (i + 1) * chunk_len + Config.Audio.SAFETY_BUFFER_MS)
        ]
        .fade_out(Config.Audio.FADE_OUT_MS)
        for i in range(len(words))
    ]


class WordsRequest(BaseModel):
    words: List[str]


app = FastAPI(title="TTS Processing API")

@app.post("/v1/process")
def process_words(request: WordsRequest):
    # Set FFmpeg path
    AudioSegment.converter = Config.Audio.FFMPEG_PATH

    # Prepare output directory
    os.makedirs(Config.TTS.OUTPUT_FOLDER, exist_ok=True)

    # Select words
    words = request.words

    if not words:
        raise HTTPException(status_code=400, detail="Words can not be empty.")

    # Generate audio
    try:
        if Config.TTS.PROVIDER == "google":
            # Set Google credentials
            os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = Config.TTS.GOOGLE["CREDENTIALS_PATH"]

            audio_data, _ = google_tts(words)
            chunks = process_google(audio_data, words)
        elif Config.TTS.PROVIDER == "azure":
            audio_data, word_boundaries = azure_tts(words)
            chunks = process_azure(audio_data, word_boundaries, words)
        else:
            raise HTTPException(status_code=500, detail="Invalid TTS provider")

        # Export results
        output_files = []
        for i, chunk in enumerate(chunks):
            filename = os.path.join(Config.TTS.OUTPUT_FOLDER, f"word_{i + 1}_{words[i]}.wav")
            chunk.export(filename, format="wav", parameters=["-ac", "1"])
            output_files.append(filename)

        return {"message": f"Processed {len(chunks)} words.", "output_files": output_files}

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"An error occurred: {e}")
    finally:
        # Clean up temporary file
        if os.path.exists("temp.wav"):
            os.remove("temp.wav")

@app.get("/v1/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8081)
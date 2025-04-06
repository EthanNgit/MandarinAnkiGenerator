# 🧠 Mandarin Anki Deck Generator

Create personalized Mandarin ➝ English Anki decks on *any* topic — powered by the latest AI models, seamless text-to-speech, and flexible pinyin support.

---

## 🚀 What Is This?

This project lets you easily generate high-quality Anki decks with Mandarin vocabulary and English translations, with support for:

- 🎯 *Your* topics and prompts
- 💬 AI-powered content generation (currently supports OpenAI, DeepSeek, and Gemini)
- 🔊 Audio pronunciation (currently supports Google TTS or Azure TTS)
- 🈶 Customizable pinyin formats (numbered, marked, and zhuyin)

---

## 🧩 How It Works

The system is built with **Docker Compose** and made up of 3 main microservices working together:

### 1. 🖥️ The Server (Deck Engine)

This is the brain of the system. It connects everything together:

- Talks to the AI model to generate vocabulary and sentences
- Calls the pinyin converter to get your preferred pinyin format
- Integrates TTS audio into the final Anki cards
- Outputs a `.apkg` file ready to import into Anki

### 2. 🔊 TTS Service

Add native-like pronunciation to your flashcards!

- Supports the use of **Google Text-to-Speech** or **Azure TTS**
- Generates clean `.wav` files for each vocabulary item efficiently by slicing batch api requests
- Automatically links audio to the Anki card

### 3. 🀄 Pinyin Converter

Different learners prefer different pinyin styles. This service makes that easy:

- Convert Mandarin to pinyin in multiple formats (e.g., numbered tones, tone marks, no tones, zhuyin)
- Helps learners hear, see, and internalize pronunciation more effectively

---

## 🛠️ Todo

1. Add client interface
2. Add more deck customization (themes)
3. Add grammar structure based cards and support
4. Tagging words for searching
5. Sharing decks
6. Expanding decks
7. Importing and improving decks
8. Explore ai tts options for better pronounciation

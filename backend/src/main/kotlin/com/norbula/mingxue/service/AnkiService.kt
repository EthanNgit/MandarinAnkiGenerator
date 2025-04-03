package com.norbula.mingxue.service

import com.norbula.mingxue.exceptions.DeckDoesNotExistException
import com.norbula.mingxue.exceptions.UserDoesNotExist
import com.norbula.mingxue.models.UserDeck
import com.norbula.mingxue.models.enums.PinyinType
import com.norbula.mingxue.repository.UserDeckRepository
import com.norbula.mingxue.repository.UserDeckWordRepository
import com.norbula.mingxue.repository.UserRepository
import com.norbula.mingxue.repository.WordTranslationRepository
import com.norbula.mingxue.service.ai.voice.NorbulaVoiceGenerator
import com.norbula.mingxue.service.pinyin.NorbulaPinyinConverter
import jakarta.json.JsonObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import shaded_package.net.minidev.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.sql.DriverManager
import java.util.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class AnkiService(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val deckRepository: UserDeckRepository,
    @Autowired private val userDeckWordRepository: UserDeckWordRepository,
    @Autowired private val wordTranslationRepository: WordTranslationRepository,
    @Autowired private val pinyinConverter: NorbulaPinyinConverter,
    @Autowired private val speechGenerator: NorbulaVoiceGenerator
)  {
    // TODO: add export options: includeSimplified, includeTraditional, pinyin, colors maybe?, custom css and html?, ai voice?
    fun createAnkiSQLiteDatabase(userToken: String, sqliteFile: File, deckName: String, pinyinType: PinyinType): Pair<File, MutableMap<String, ByteArray>> {
        val user = userRepository.findByAuthToken(userToken).orElseThrow { UserDoesNotExist() }
        val deck = deckRepository.findByUserAndName(user, deckName).orElseThrow { DeckDoesNotExistException() }

        Class.forName("org.sqlite.JDBC")
        val connection = DriverManager.getConnection("jdbc:sqlite:${sqliteFile.absolutePath}")

        // Drop tables if they exist (to avoid caching issues during development)
        connection.createStatement().use { stmt ->
            stmt.execute("DROP TABLE IF EXISTS col")
            stmt.execute("DROP TABLE IF EXISTS notes")
            stmt.execute("DROP TABLE IF EXISTS cards")
            stmt.execute("DROP TABLE IF EXISTS graves")
            stmt.execute("DROP TABLE IF EXISTS revlog")
        }

        // Create tables
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
            CREATE TABLE col (
                id              INTEGER PRIMARY KEY,
                crt             INTEGER,
                mod             INTEGER,
                scm             INTEGER,
                ver             INTEGER,
                dty             INTEGER,
                usn             INTEGER,
                ls              INTEGER,
                conf            TEXT,
                models          TEXT,
                decks           TEXT,
                dconf           TEXT,
                tags            TEXT
            );
            """.trimIndent()
            )

            stmt.execute(
                """
            CREATE TABLE notes (
                id      INTEGER PRIMARY KEY,
                guid    TEXT,
                mid     INTEGER,
                mod     INTEGER,
                usn     INTEGER,
                tags    TEXT,
                flds    TEXT,
                sfld    TEXT,
                csum    INTEGER,
                flags   INTEGER,
                data    TEXT
            );
            """.trimIndent()
            )

            stmt.execute(
                """
            CREATE TABLE cards (
                id      INTEGER PRIMARY KEY,
                nid     INTEGER,
                did     INTEGER,
                ord     INTEGER,
                mod     INTEGER,
                usn     INTEGER,
                type    INTEGER,
                queue   INTEGER,
                due     INTEGER,
                ivl     INTEGER,
                factor  INTEGER,
                reps    INTEGER,
                lapses  INTEGER,
                left    INTEGER,
                odue    INTEGER,
                odid    INTEGER,
                flags   INTEGER,
                data    TEXT
            );
            """.trimIndent()
            )

            stmt.execute(
                """
            CREATE TABLE graves (
                usn             INTEGER,
                oid             INTEGER,
                type            INTEGER
            );
            """.trimIndent()
            )

            stmt.execute(
                """
            CREATE TABLE revlog (
                id      INTEGER PRIMARY KEY,
                cid     INTEGER,
                usn     INTEGER,
                ease    INTEGER,
                ivl     INTEGER,
                lastIvl INTEGER,
                factor  INTEGER,
                time    INTEGER,
                type    INTEGER
            );
            """.trimIndent()
            )
        }

        val now = System.currentTimeMillis() / 1000

        println("THIS IS CALLED")

        // Model configuration - Modified to include audio field
        val modelsJson = """
        {
            "100000": {
                "id": 100000,
                "name": "Norbula Model",
                "did": 200000,
                "sortf": 0,
                "mod": $now,
                "usn": -1,
                "type": 0,
                "flds": [
                    {"name": "Simplified", "ord": 0, "sticky": false, "rtl": false, "font": "Arial", "size": 20},
                    {"name": "Traditional", "ord": 1, "sticky": false, "rtl": false, "font": "Arial", "size": 20},
                    {"name": "Pinyin", "ord": 2, "sticky": false, "rtl": false, "font": "Arial", "size": 16},
                    {"name": "Translation", "ord": 3, "sticky": false, "rtl": false, "font": "Arial", "size": 16},
                    {"name": "PartOfSpeech", "ord": 4, "sticky": false, "rtl": false, "font": "Arial", "size": 16},
                    {"name": "SimpleSentence", "ord": 5, "sticky": false, "rtl": false, "font": "Arial", "size": 18},
                    {"name": "TraditionalSentence", "ord": 6, "sticky": false, "rtl": false, "font": "Arial", "size": 18},
                    {"name": "SentencePinyin", "ord": 7, "sticky": false, "rtl": false, "font": "Arial", "size": 16},
                    {"name": "SentenceTranslation", "ord": 8, "sticky": false, "rtl": false, "font": "Arial", "size": 16},
                    {"name": "Audio", "ord": 9, "sticky": false, "rtl": false, "font": "Arial", "size": 16},
                    {"name": "AudioSentence", "ord": 10, "sticky": false, "rtl": false, "font": "Arial", "size": 16}
                ],         
                "tmpls": [
                        {
                            "name": "Recognition",
                            "qfmt": "<div data-version=\"2\" class=\"chinese-card\"><div class=\"word-header\"><div class=\"word-container\"><span class=\"hanzi hover-trigger\">{{Simplified}}{{#Traditional}} / {{Traditional}}{{/Traditional}}</span><div class=\"pinyin-popup\">{{Pinyin}}</div></div></div><hr class=\"divider\"><div class=\"sentence-section\"><div class=\"sentence-container\"><p class=\"sentence hover-trigger\">{{SimpleSentence}}{{#TraditionalSentence}}<br>{{TraditionalSentence}}{{/TraditionalSentence}}</p><div class=\"pinyin-popup\">{{SentencePinyin}}</div></div></div></div>",
                            "afmt": "<div data-version=\"2\" class=\"chinese-card\"><div class=\"word-header\"><div class=\"hanzi\">{{Simplified}}{{#Traditional}} / {{Traditional}}{{/Traditional}}</div><div class=\"pinyin\">{{Pinyin}}</div><div class=\"translation-block\"><span class=\"translation\">{{Translation}}</span><span class=\"part-of-speech\">{{PartOfSpeech}}</span></div></div><hr class=\"divider\"><div class=\"sentence-section\"><p class=\"sentence\">{{SimpleSentence}}{{#TraditionalSentence}}<br>{{TraditionalSentence}}{{/TraditionalSentence}}</p><div class=\"pinyin\">{{SentencePinyin}}</div><div class=\"sentence-translation\">{{SentenceTranslation}}</div></div><div class=\"audio-controls\">{{Audio}} {{AudioSentence}}</div></div>",
                            "ord": 0
                        }
                    ],
                "css": ".card { font-family: \"Inter\", \"SF Pro Display\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; font-size: 18px; text-align: center; color: #334155; background-color: #ffffff; border-radius: 12px; padding: 24px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); max-width: 600px; margin: 0 auto; line-height: 1.5; } .word-header { margin-bottom: 16px; } .word-container { position: relative; display: inline-block; cursor: help; } .hanzi { font-size: 34px; font-weight: 600; color: #1e40af; letter-spacing: 1px; transition: color 0.2s ease; text-shadow: 0 1px 2px rgba(0, 0, 0, 0.05); } .hover-trigger:hover { color: #3b82f6; } .pinyin-popup { display: none; font-size: 16px; color: #64748b; background-color: #f8fafc; padding: 8px 16px; border-radius: 6px; margin-top: 8px; border: 1px solid #e2e8f0; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05); position: absolute; left: 50%; transform: translateX(-50%); z-index: 10; min-width: 120px; white-space: nowrap; } .hover-trigger:hover + .pinyin-popup, .sentence-container:hover .pinyin-popup { display: block; animation: fadeIn 0.2s ease-in; } .pinyin { font-size: 18px; color: #64748b; margin-top: 8px; font-weight: 500; } .translation-block { margin: 20px 0; padding: 16px; background-color: #f0f9ff; border-radius: 8px; border-left: 4px solid #0ea5e9; text-align: left; } .translation { font-size: 18px; color: #0369a1; font-weight: 500; display: inline-block; } .part-of-speech { font-size: 14px; color: #94a3b8; margin-left: 8px; font-style: italic; display: inline-block; } .divider { border: 0; height: 1px; background: linear-gradient(to right, transparent, #cbd5e1, transparent); margin: 24px 0; } .sentence-section { background-color: #f8fafc; border-radius: 8px; padding: 20px; margin-top: 16px; } .sentence-container { position: relative; display: block; cursor: help; margin-bottom: 12px; } .sentence { font-size: 18px; line-height: 1.6; margin: 12px 0; color: #334155; padding: 8px; text-align: left; transition: color 0.2s ease; } .sentence-translation { font-size: 16px; color: #64748b; font-style: italic; margin-top: 12px; text-align: left; line-height: 1.5; background-color: rgba(226, 232, 240, 0.5); padding: 10px; border-radius: 6px; } .audio-controls { margin: 10px 0; } .audio-controls audio { display: block; width: 100%; max-width: 300px; margin: 0 auto; } @keyframes fadeIn { from { opacity: 0; transform: translateY(-5px) translateX(-50%); } to { opacity: 1; transform: translateY(0) translateX(-50%); } } .nightMode .card { background-color: #1e293b; color: #e2e8f0; } .nightMode .hanzi { color: #60a5fa; } .nightMode .pinyin-popup, .nightMode .sentence-section { background-color: #334155; border-color: #475569; color: #cbd5e1; } .nightMode .translation-block { background-color: #0f172a; border-left-color: #3b82f6; } .nightMode .translation { color: #60a5fa; } .nightMode .part-of-speech, .nightMode .pinyin { color: #94a3b8; } .nightMode .sentence { color: #e2e8f0; } .nightMode .sentence-translation { color: #cbd5e1; background-color: rgba(51, 65, 85, 0.5); } .nightMode .divider { background: linear-gradient(to right, transparent, #475569, transparent); }"
            }
        }
        """.trimIndent()

        // Deck configuration
        val decksJson = """
        {
            "200000": {
                "id": 200000,
                "name": "${deck.name.replace("\"", "\\\"")}",
                "desc": "",
                "dyn": 0,
                "conf": 1,
                "extendRev": 0,
                "usn": -1,
                "mod": $now,
                "collapsed": false,
                "newToday": [0, 0],
                "revToday": [0, 0],
                "lrnToday": [0, 0],
                "timeToday": [0, 0],
                "browserCollapsed": false,
                "queue": -1
            }
        }
        """.trimIndent()

        val dconfJson = """
        {
            "1": {
                "id": 1,
                "name": "Default",
                "mod": $now,
                "usn": -1,
                "autoplay": true,
                "timer": 0,
                "replayq": true,
                "new": {
                    "bury": false,
                    "delays": [1, 10],
                    "initialFactor": 2500,
                    "ints": [1, 4, 0],
                    "order": 1,
                    "perDay": 20
                },
                "rev": {
                    "bury": false,
                    "ease4": 1.3,
                    "fuzz": 0.05,
                    "ivlFct": 1,
                    "maxIvl": 36500,
                    "perDay": 200
                },
                "lapse": {
                    "delays": [10],
                    "leechAction": 1,
                    "leechFails": 8,
                    "minInt": 1,
                    "mult": 0
                },
                "maxTaken": 60
            }
        }
        """.trimIndent()

        // Insert collection configuration
        val insertCol = connection.prepareStatement(
            "INSERT INTO col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags) " +
                    "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        with(insertCol) {
            setLong(1, now)  // crt
            setLong(2, now)  // mod
            setLong(3, now)  // scm
            setInt(4, 11)    // ver
            setInt(5, 0)     // dty
            setInt(6, -1)    // usn (changed from 0 to -1)
            setLong(7, now)  // ls
            setString(8, "{}")  // conf
            setString(9, modelsJson)
            setString(10, decksJson)
            setString(11, dconfJson)  // Changed from "{}" to actual config
            setString(12, "{}")
            executeUpdate()
        }

        // Prepare statements
        val insertNote = connection.prepareStatement(
            "INSERT INTO notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )

        val insertCard = connection.prepareStatement(
            "INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )

        // Process words
        val deckWords = userDeckWordRepository.findByDeck(deck)
        // Extract the numbered pinyin from words and sentences.
        val wordPinyinList = deckWords.map { it.wordContext.pinyin }
        val sentencePinyinList = deckWords.map { it.wordContext.usageSentence.pinyin }

        // Batch-convert the pinyin based on the pinyinType.
        val convertedWordPinyin: List<String> = when(pinyinType) {
            PinyinType.marked  -> pinyinConverter.toMarked(wordPinyinList)
            PinyinType.zhuyin  -> pinyinConverter.toZhuyin(wordPinyinList)
            PinyinType.none    -> List(wordPinyinList.size) { "" }  // leave blank when pinyinType is none
            else               -> wordPinyinList  // default numbered, no conversion needed
        }

        val convertedSentencePinyin: List<String> = when(pinyinType) {
            PinyinType.marked  -> pinyinConverter.toMarked(sentencePinyinList)
            PinyinType.zhuyin  -> pinyinConverter.toZhuyin(sentencePinyinList)
            PinyinType.none    -> List(sentencePinyinList.size) { "" }
            else               -> sentencePinyinList
        }

        // Create a map to store media references for the APKG file
        val mediaMap = mutableMapOf<String, ByteArray>()
        val mediaIndex = mutableMapOf<Int, String>()

        // Now process each deck word and insert the note.
        deckWords.forEachIndexed { index, word ->
            val wordTranslation = wordTranslationRepository.findByContext(word.wordContext)
                .orElseThrow { Error("Invalid word") }

            // Generate IDs
            val noteId = System.currentTimeMillis() + index
            val cardId = noteId + 1

// Generate word audio file name and reference
            val audioFileName = "word_${word.wordContext.id}.mp3"
            val audioRef = "[sound:$audioFileName]"

            // Fetch word audio using the old API signature
            val audioBytes = speechGenerator.getTTSFile(word.wordContext.id, false)

            if (audioBytes != null) {
                mediaMap[audioFileName] = audioBytes
                mediaIndex[index] = audioFileName
                println("Audio for word ${word.wordContext.id} retrieved, size: ${audioBytes.size}")
            } else {
                println("Audio for word ${word.wordContext.id} is null!")
            }

            // Generate sentence audio file name and reference
            val sentenceAudioFileName = "sentence_${word.wordContext.id}.mp3"
            val sentenceAudioRef = "[sound:$sentenceAudioFileName]"

            // Fetch sentence audio using the new API signature (second parameter true)
            val sentenceAudioBytes = speechGenerator.getTTSFile(word.wordContext.id, true)

            if (sentenceAudioBytes != null) {
                mediaMap[sentenceAudioFileName] = sentenceAudioBytes
                println("Sentence audio for word ${word.wordContext.id} retrieved, size: ${sentenceAudioBytes.size}")
            } else {
                println("Sentence audio for word ${word.wordContext.id} is null!")
            }

            val simplifiedWord = word.wordContext.word.simplifiedWord
            val traditionalWord = if (simplifiedWord == word.wordContext.word.traditionalWord) ""
            else word.wordContext.word.traditionalWord

            val simpleSentence = word.wordContext.usageSentence.simplifiedSentence
            val traditionalSentence = if (simpleSentence == word.wordContext.usageSentence.traditionalSentence) ""
            else word.wordContext.usageSentence.traditionalSentence

            // Build the fields to be stored. Notice that for the pinyin fields we use the converted results.
            val fields = listOf(
                simplifiedWord,
                traditionalWord,
                convertedWordPinyin[index],
                wordTranslation.translation,
                word.wordContext.partOfSpeech,
                simpleSentence,
                traditionalSentence,
                convertedSentencePinyin[index],
                word.wordContext.usageSentence.translation ?: "",
                audioBytes?.let { audioRef } ?: "",  // Include audio reference if available
                sentenceAudioBytes?.let { sentenceAudioRef } ?: ""
            )

            // Insert Note (using your prepared statement 'insertNote').
            with(insertNote) {
                setLong(1, noteId)
                setString(2, UUID.randomUUID().toString())
                setLong(3, 100000)
                setLong(4, now)
                setInt(5, 0)
                setString(6, word.wordContext.frequency.toString())
                setString(7, fields.joinToString("\u001F"))
                setString(8, fields[0].toString())

                // Calculate and set CRC using the simplified word.
                val crc = CRC32().apply { update(fields[0].toString().toByteArray()) }
                setLong(9, crc.value)
                setInt(10, 0)
                setString(11, "")
                executeUpdate()
            }

            // Insert Card
            with(insertCard) {
                setLong(1, cardId)
                setLong(2, noteId)
                setLong(3, 200000)  // deck ID
                setInt(4, 0)        // ord
                setLong(5, now)     // mod
                setInt(6, 0)        // usn
                setInt(7, 0)        // type
                setInt(8, 0)        // queue
                setInt(9, 0)        // due
                setInt(10, 0)       // ivl
                setInt(11, 0)       // factor
                setInt(12, 0)       // reps
                setInt(13, 0)       // lapses
                setInt(14, 0)       // left
                setInt(15, 0)       // odue
                setInt(16, 0)       // odid
                setInt(17, 0)       // flags
                setString(18, "")   // data
                executeUpdate()
            }
        }

        connection.close()

        // Return the media map for the APKG creation
        return Pair(sqliteFile, mediaMap)
    }

    // Create an APKG file (zip archive) that contains the collection.anki2 file.
    fun createApkgFile(databaseInfo: Pair<File, Map<String, ByteArray>>, deckName: String): File {
        val (sqliteFile, mediaMap) = databaseInfo
        val apkgFile = File(sqliteFile.parentFile, "$deckName.apkg")

        ZipOutputStream(FileOutputStream(apkgFile)).use { zipOut ->
            // Add collection.anki2
            FileInputStream(sqliteFile).use { fis ->
                zipOut.putNextEntry(ZipEntry("collection.anki2"))
                fis.copyTo(zipOut)
                zipOut.closeEntry()
            }

            // Create media mapping
            val mediaJson = mediaMap.entries.mapIndexed { index, entry ->
                "\"$index\": \"${entry.key}\""
            }.joinToString(",", "{", "}")

            // Add media mapping file
            zipOut.putNextEntry(ZipEntry("media"))
            zipOut.write(mediaJson.toByteArray())
            zipOut.closeEntry()

            // Add each media file
            mediaMap.entries.forEachIndexed { index, entry ->
                zipOut.putNextEntry(ZipEntry("$index"))
                ByteArrayInputStream(entry.value).copyTo(zipOut)
                zipOut.closeEntry()
            }

            println("Created APKG with ${mediaMap.size} audio files")
        }
        return apkgFile
    }

    // Main function to create Anki deck with audio
    fun createAnkiDeckWithAudio(userToken: String, outputDir: File, deckName: String, pinyinType: PinyinType): File {
        val sqliteFile = File(outputDir, "collection.anki2")
        val databaseInfo = createAnkiSQLiteDatabase(userToken, sqliteFile, deckName, pinyinType)
        return createApkgFile(databaseInfo, deckName)
    }
}
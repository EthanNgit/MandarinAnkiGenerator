from typing import List, Dict

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

import re
from pyzhuyin import pinyin_to_zhuyin as p_t_z


tone_map = {
    'a': ['ā', 'á', 'ǎ', 'à'],
    'o': ['ō', 'ó', 'ǒ', 'ò'],
    'e': ['ē', 'é', 'ě', 'è'],
    'i': ['ī', 'í', 'ǐ', 'ì'],
    'u': ['ū', 'ú', 'ǔ', 'ù'],
    'ü': ['ǖ', 'ǘ', 'ǚ', 'ǜ']
}


def standardize_pinyin(pinyin):
    try:
        pinyin_str = str(pinyin)
        processed = re.sub(r'([a-zA-Z]+)\s+([1-4])', r'\1\2', pinyin_str)
        syllables = re.findall(r'[a-zA-Z]+[1-4]|[a-zA-Z]+|\d+', processed)
        return ' '.join(syllables)
    except TypeError:
        return ""
    

def add_tone_mark(char, tone):
    tone_index = tone - 1
    return tone_map.get(char, [char] * 4)[tone_index]


def process_syllable(syllable):
    if not syllable:
        return ''
    # Check if the syllable ends with a tone number
    if not syllable[-1].isdigit():
        return syllable.lower()
    tone = int(syllable[-1])
    if tone < 1 or tone > 4:
        return syllable[:-1].lower()
    letters = syllable[:-1].lower().replace('v', 'ü')
    # Check for a, o, e
    for vowel in ['a', 'o', 'e']:
        if vowel in letters:
            index = letters.find(vowel)
            accented_vowel = add_tone_mark(vowel, tone)
            new_letters = letters[:index] + accented_vowel + letters[index+1:]
            return new_letters
    # Check for iu or ui
    if 'iu' in letters:
        iu_pos = letters.find('iu')
        u_pos = iu_pos + 1
        accented_u = add_tone_mark('u', tone)
        new_letters = letters[:u_pos] + accented_u + letters[u_pos+1:]
        return new_letters
    elif 'ui' in letters:
        ui_pos = letters.find('ui')
        i_pos = ui_pos + 1
        accented_i = add_tone_mark('i', tone)
        new_letters = letters[:i_pos] + accented_i + letters[i_pos+1:]
        return new_letters
    # Check for i, u, ü
    for vowel in ['i', 'u', 'ü']:
        if vowel in letters:
            index = letters.find(vowel)
            accented_vowel = add_tone_mark(vowel, tone)
            new_letters = letters[:index] + accented_vowel + letters[index+1:]
            return new_letters
    # If no vowels found, return letters as is
    return letters


def pinyin_to_marked(pinyin):
    standardized = standardize_pinyin(pinyin)
    if isinstance(standardized, list):
        syllables = standardized
    else:
        syllables = standardized.split()
    processed = []
    for syl in syllables:
        processed_syl = process_syllable(syl)
        processed.append(processed_syl)
    combined = ''.join(processed)
    if combined:
        combined = combined[0].upper() + combined[1:]
    return combined


def pinyin_to_zhuyin(pinyin):
    try:
        a = standardize_pinyin(pinyin)
        a = a.split(" ")
        return "".join([p_t_z(x) for x in a])
    except ValueError:
        return ""


class ConvertRequest(BaseModel):
    pinyin: List[str]

app = FastAPI(title="Pinyin Converting API")

@app.post("/api/v1/convert/mark")
def process_words(request: ConvertRequest):
    res = [pinyin_to_marked(x) for x in request.pinyin]

    return {"result": res}


@app.post("/api/v1/convert/zhuyin")
def process_words(request: ConvertRequest):
    res = [pinyin_to_zhuyin(x) for x in request.pinyin]

    return {"result": res}


@app.get("/api/v1/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8082)
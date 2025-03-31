from typing import List

from fastapi import FastAPI
from pydantic import BaseModel

import re
from pyzhuyin import pinyin_to_zhuyin as p_t_z


accent_map = {
    'a': {'1': 'ā', '2': 'á', '3': 'ǎ', '4': 'à', '5': 'a'},
    'e': {'1': 'ē', '2': 'é', '3': 'ě', '4': 'è', '5': 'e'},
    'i': {'1': 'ī', '2': 'í', '3': 'ǐ', '4': 'ì', '5': 'i'},
    'o': {'1': 'ō', '2': 'ó', '3': 'ǒ', '4': 'ò', '5': 'o'},
    'u': {'1': 'ū', '2': 'ú', '3': 'ǔ', '4': 'ù', '5': 'u'},
    'v': {'1': 'ǖ', '2': 'ǘ', '3': 'ǚ', '4': 'ǜ', '5': 'ü'},
    'ü': {'1': 'ǖ', '2': 'ǘ', '3': 'ǚ', '4': 'ǜ', '5': 'ü'}
}


def standardize_pinyin(text: str) -> str:
    text = re.sub(r'([a-zA-Züv]+)\s+(\d)', r'\1\2', text)

    tokens = text.split()

    if not tokens:
        return ""

    merged_tokens = []
    current = tokens[0]

    for token in tokens[1:]:
        if not current[-1].isdigit() and token and token[0].isdigit():
            current += token
        else:
            merged_tokens.append(current)
            current = token
    merged_tokens.append(current)

    return " ".join(merged_tokens)


def convert_syllable(syllable: str, tone: str) -> str:
    s_lower = syllable.lower()
    index = -1
    if 'a' in s_lower:
        index = s_lower.index('a')
    elif 'e' in s_lower:
        index = s_lower.index('e')
    elif "ou" in s_lower:
        index = s_lower.index('o')
    else:
        for i in range(len(s_lower)-1, -1, -1):
            if s_lower[i] in "aeiouüv":
                index = i
                break
    if index == -1:
        return syllable
    vowel = s_lower[index]
    accented = accent_map.get(vowel, {}).get(tone, vowel)
    result = syllable[:index] + accented + syllable[index+1:]
    return result


def convert_token(token: str) -> str:
    syllables = re.findall(r'([a-zA-Züv]+)(\d)', token)
    if not syllables:
        return token
    converted = ""
    for syl, tone in syllables:
        converted += convert_syllable(syl, tone)
    return converted


def pinyin_to_marked(pinyin: str) -> str:
    std = standardize_pinyin(pinyin)
    delimiter = " " if " " in std else ""
    tokens = std.split()
    converted_tokens = [convert_token(token) for token in tokens]
    if delimiter:
        converted_tokens = [t.capitalize() for t in converted_tokens]
        return delimiter.join(converted_tokens)
    else:
        result = "".join(converted_tokens)
        return result.capitalize()


def standardize_pinyin_for_zhuyin(pinyin):
    try:
        pinyin_str = str(pinyin.replace('ü', "v"))
        processed = re.sub(r'([a-zA-Z]+)\s+([1-4])', r'\1\2', pinyin_str)
        processed = re.sub(r'([1-4])(?=[a-zA-Z])', r'\1 ', processed)
        syllables = re.findall(r'[a-zA-Z]+[1-4]|[a-zA-Z]+|\d+', processed)
        return ' '.join(syllables)
    except TypeError:
        return ""


def pinyin_to_zhuyin(pinyin):
    try:
        a = standardize_pinyin_for_zhuyin(pinyin)
        a = a.split(" ")
        return "".join([p_t_z(x) for x in a])
    except ValueError:
        return ""


class ConvertRequest(BaseModel):
    pinyin: List[str]

app = FastAPI(title="Pinyin Converting API")

@app.post("/api/v1/convert/mark")
def process_pinyin_to_mark(request: ConvertRequest):
    res = [pinyin_to_marked(x) for x in request.pinyin]
    print(res)

    return {"result": res}


@app.post("/api/v1/convert/zhuyin")
def process_pinyin_to_zhuyin(request: ConvertRequest):
    res = [pinyin_to_zhuyin(x) for x in request.pinyin]
    print(res)
    return {"result": res}


@app.get("/api/v1/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8082)
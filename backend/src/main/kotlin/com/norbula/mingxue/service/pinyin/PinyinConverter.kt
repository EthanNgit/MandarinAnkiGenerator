package com.norbula.mingxue.service.pinyin

interface PinyinConverter {
    fun toMarked(pinyin: List<String>): String
    fun toZhuyin(pinyin: List<String>): String
}
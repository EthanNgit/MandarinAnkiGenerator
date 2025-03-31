package com.norbula.mingxue.service.pinyin

interface PinyinConverter {
    fun toMarked(pinyin: List<String>): List<String>
    fun toZhuyin(pinyin: List<String>): List<String>
}
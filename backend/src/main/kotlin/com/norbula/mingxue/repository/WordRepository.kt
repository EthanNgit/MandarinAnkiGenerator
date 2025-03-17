package com.norbula.mingxue.repository

import com.norbula.mingxue.modules.models.Word
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface WordRepository: CrudRepository<Word, Int> {
}
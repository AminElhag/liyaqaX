package com.liyaqa.common.exception

import org.springframework.http.HttpStatus

class ArenaException(
    val status: HttpStatus,
    val errorType: String,
    override val message: String,
    val errorCode: String? = null,
) : RuntimeException(message)

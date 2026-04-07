package com.liyaqa

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
class LiyaqaApplication

fun main(args: Array<String>) {
    runApplication<LiyaqaApplication>(*args)
}

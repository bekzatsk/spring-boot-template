package kz.innlab.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AuthStarterApplication

fun main(args: Array<String>) {
    runApplication<AuthStarterApplication>(*args)
}

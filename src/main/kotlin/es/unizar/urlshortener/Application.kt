package es.unizar.urlshortener

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
class Application

fun main(args: Array<String>) {
    @Suppress("SpreadOperator")
    runApplication<Application>(*args)
}

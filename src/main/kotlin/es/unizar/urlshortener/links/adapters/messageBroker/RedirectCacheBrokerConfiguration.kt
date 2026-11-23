package es.unizar.urlshortener.links.adapters.messageBroker

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("broker")
@Configuration
class RedirectCacheBrokerConfiguration {
    @Bean
    fun redirectCacheInvalidationDeclarables(): Declarables {
        val queue = Queue("links.cache-invalidation.queue", true)
        val exchange = TopicExchange("links.cache-invalidation", true, false)
        val binding = BindingBuilder.bind(queue).to(exchange).with("cache.invalidate")

        // Agrupamos las declaraciones para evitar conflictos con los beans de la feature 0004
        return Declarables(queue, exchange, binding)
    }
}

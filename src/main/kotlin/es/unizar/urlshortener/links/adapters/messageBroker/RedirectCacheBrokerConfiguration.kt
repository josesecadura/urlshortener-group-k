package es.unizar.urlshortener.links.adapters.messageBroker

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("broker")
@Configuration
class RedirectCacheBrokerConfiguration(
    @Value("\${urlshortener.redirect-cache.broker.queue}")
    private val queueName: String,
    @Value("\${urlshortener.redirect-cache.broker.exchange}")
    private val exchangeName: String,
    @Value("\${urlshortener.redirect-cache.broker.routing-key}")
    private val routingKey: String,
) {
    @Bean
    fun redirectCacheInvalidationDeclarables(): Declarables {
        val queue = Queue(queueName, true)
        val exchange = TopicExchange(exchangeName, true, false)
        val binding = BindingBuilder.bind(queue).to(exchange).with(routingKey)

        // Agrupamos las declaraciones para evitar conflictos con los beans de la feature 0004
        return Declarables(queue, exchange, binding)
    }
}

package es.unizar.urlshortener.links.scheduling.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface JpaShedlockRepository : JpaRepository<ShedlockEntity, String>

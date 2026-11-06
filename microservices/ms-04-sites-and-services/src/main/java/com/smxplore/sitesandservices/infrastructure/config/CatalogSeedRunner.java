package com.smxplore.sitesandservices.infrastructure.config;

import com.smxplore.sitesandservices.application.catalog.CatalogSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Ejecuta el catalogo semilla al arrancar, solo cuando {@code app.catalog.seed-enabled=true}.
 *
 * <p>Por defecto esta apagado: produccion no crea datos sola. En el stack local se enciende con
 * {@code CATALOG_SEED_ENABLED=true} para que una instalacion nueva tenga las categorias base.
 * Si Mongo no responde, se registra el aviso y el servicio arranca igual: el catalogo semilla no
 * es un requisito de disponibilidad.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.catalog", name = "seed-enabled", havingValue = "true")
public class CatalogSeedRunner implements ApplicationRunner {

    private final CatalogSeedService catalogSeedService;

    @Override
    public void run(ApplicationArguments args) {
        catalogSeedService.seed()
                .doOnNext(created -> log.info("catalogo semilla: {} categorias creadas", created))
                .onErrorResume(error -> {
                    log.warn("catalogo semilla omitido: {}", error.getMessage());
                    return Mono.empty();
                })
                .block();
    }
}

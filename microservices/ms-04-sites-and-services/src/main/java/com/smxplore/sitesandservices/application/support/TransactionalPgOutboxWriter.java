package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import com.smxplore.sitesandservices.domain.ports.out.ReservationOutboxPort;
import com.smxplore.sitesandservices.domain.ports.out.ReservationTransactionPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

/**
 * Variante PostgreSQL de {@link TransactionalOutboxWriter}: guarda el agregado y encola su evento
 * en el outbox dentro de la misma transaccion R2DBC.
 *
 * <p>Se usa para los agregados que viven en PostgreSQL (hoy, zonas y reservas). El nombre
 * "Reservation*" de los puertos es historico: la tabla {@code reservation_outbox} es el outbox
 * PostgreSQL de todo el servicio, y {@code ReservationOutboxRelay} publica cada fila al topic
 * igual a su {@code event_type}, sin importar el agregado que la origino.
 */
@RequiredArgsConstructor
public class TransactionalPgOutboxWriter {

    private final ReservationTransactionPort transaction;
    private final ReservationOutboxPort outbox;

    public <T> Mono<T> save(
            Mono<T> saveOperation,
            BiFunction<T, String, DomainEvent> eventFactory,
            String correlationId
    ) {
        return transaction.execute(
                saveOperation.flatMap(saved -> outbox.append(eventFactory.apply(saved, correlationId))
                        .thenReturn(saved))
        );
    }
}

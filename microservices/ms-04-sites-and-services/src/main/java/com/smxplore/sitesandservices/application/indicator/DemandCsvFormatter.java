package com.smxplore.sitesandservices.application.indicator;

import com.smxplore.sitesandservices.domain.model.indicator.DemandIndicators;
import com.smxplore.sitesandservices.domain.model.indicator.ServiceDemand;
import com.smxplore.sitesandservices.domain.model.indicator.SiteDemand;
import com.smxplore.sitesandservices.domain.model.indicator.ZoneDemand;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Reporte plano de los indicadores para descarga. No pretende ser un formato
 * financiero: es el mismo dato de la API, apto para abrir en una hoja de cálculo.
 */
public final class DemandCsvFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DemandCsvFormatter() {
    }

    public static String format(DemandIndicators indicators) {
        ZoneId zone = ZoneId.of(indicators.timeZone());
        StringBuilder csv = new StringBuilder();
        csv.append("Indicadores de demanda SM-Xplore\n");
        csv.append("Desde,").append(DATE_TIME.format(indicators.from().atZone(zone))).append('\n');
        csv.append("Hasta,").append(DATE_TIME.format(indicators.to().atZone(zone))).append('\n');
        csv.append("Alcance,").append(value(indicators.scope())).append('\n');
        csv.append("Zona horaria,").append(value(indicators.timeZone())).append('\n');
        csv.append('\n');

        csv.append("Resumen\n");
        csv.append("Reservas,Personas,Ingresos,Canceladas,Completadas,Turistas unicos,")
                .append("Tasa de cancelacion,Promedio de personas por reserva\n");
        csv.append(indicators.summary().reservations()).append(',')
                .append(indicators.summary().people()).append(',')
                .append(money(indicators.summary().revenue())).append(',')
                .append(indicators.summary().cancelledReservations()).append(',')
                .append(indicators.summary().completedReservations()).append(',')
                .append(indicators.summary().uniqueTourists()).append(',')
                .append(percent(indicators.summary().cancellationRate())).append(',')
                .append(decimal(indicators.summary().averagePeoplePerReservation()))
                .append('\n');

        csv.append('\n').append("Comparacion con el periodo anterior\n");
        csv.append("Metrica,Actual,Anterior,Variacion %\n");
        csv.append("Reservas,")
                .append(indicators.comparison().current().reservations()).append(',')
                .append(indicators.comparison().previous().reservations()).append(',')
                .append(decimal(indicators.comparison().reservationsChange())).append('\n');
        csv.append("Personas,")
                .append(indicators.comparison().current().people()).append(',')
                .append(indicators.comparison().previous().people()).append(',')
                .append(decimal(indicators.comparison().peopleChange())).append('\n');
        csv.append("Ingresos,")
                .append(money(indicators.comparison().current().revenue())).append(',')
                .append(money(indicators.comparison().previous().revenue())).append(',')
                .append(decimal(indicators.comparison().revenueChange())).append('\n');

        csv.append('\n').append("Zonas\n");
        csv.append("Zona,Estado,Reservas,Personas,Ingresos,Participacion,Capacidad,Ocupacion\n");
        for (ZoneDemand zoneDemand : indicators.zones()) {
            csv.append(value(zoneDemand.zoneName())).append(',')
                    .append(value(zoneDemand.zoneStatus())).append(',')
                    .append(zoneDemand.reservations()).append(',')
                    .append(zoneDemand.people()).append(',')
                    .append(money(zoneDemand.revenue())).append(',')
                    .append(percent(zoneDemand.share())).append(',')
                    .append(zoneDemand.capacityPeople()).append(',')
                    .append(percent(zoneDemand.occupancyRate())).append('\n');
        }

        csv.append('\n').append("Sitios\n");
        csv.append("Sitio,Zona,Reservas,Personas,Ingresos,Participacion,Capacidad,Ocupacion\n");
        for (SiteDemand site : indicators.sites()) {
            csv.append(value(site.siteName())).append(',')
                    .append(value(site.zoneName())).append(',')
                    .append(site.reservations()).append(',')
                    .append(site.people()).append(',')
                    .append(money(site.revenue())).append(',')
                    .append(percent(site.share())).append(',')
                    .append(site.capacityPeople()).append(',')
                    .append(percent(site.occupancyRate())).append('\n');
        }

        csv.append('\n').append("Servicios\n");
        csv.append("Servicio,Tipo,Sitio,Zona,Reservas,Personas,Ingresos,Participacion,Capacidad,Ocupacion\n");
        for (ServiceDemand service : indicators.services()) {
            csv.append(value(service.serviceName())).append(',')
                    .append(value(service.serviceType())).append(',')
                    .append(value(service.siteName())).append(',')
                    .append(value(service.zoneName())).append(',')
                    .append(service.reservations()).append(',')
                    .append(service.people()).append(',')
                    .append(money(service.revenue())).append(',')
                    .append(percent(service.share())).append(',')
                    .append(service.capacityPeople()).append(',')
                    .append(percent(service.occupancyRate())).append('\n');
        }
        return csv.toString();
    }

    private static String value(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f", value * 100.0);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}

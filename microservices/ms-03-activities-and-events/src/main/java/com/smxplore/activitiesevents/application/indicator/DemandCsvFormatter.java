package com.smxplore.activitiesevents.application.indicator;

import com.smxplore.activitiesevents.domain.model.indicator.ActivityDemand;
import com.smxplore.activitiesevents.domain.model.indicator.DemandIndicators;
import com.smxplore.activitiesevents.domain.model.indicator.EventDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ServiceDemand;
import com.smxplore.activitiesevents.domain.model.indicator.SiteDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ZoneDemand;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Flat planning report; it is the same data as the API, ready to open in a spreadsheet. */
public final class DemandCsvFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Prefijos que Excel y LibreOffice interpretan como formula en una celda. Los nombres del catalogo
     * los escriben guias y gestoras, asi que un nombre como {@code =HYPERLINK(...)} se ejecutaria al
     * abrir el informe; tambien se neutralizan tabulador y retorno para que no partan la celda.
     */
    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    private DemandCsvFormatter() {
    }

    public static String format(DemandIndicators indicators) {
        ZoneId zone = ZoneId.of(indicators.timeZone());
        StringBuilder csv = new StringBuilder();
        csv.append("Indicadores de demanda SM-Xplore - actividades y eventos\n");
        csv.append("Desde,").append(DATE_TIME.format(indicators.from().atZone(zone))).append('\n');
        csv.append("Hasta,").append(DATE_TIME.format(indicators.to().atZone(zone))).append('\n');
        csv.append("Alcance,").append(value(indicators.scope())).append('\n');
        csv.append("Zona horaria,").append(value(indicators.timeZone())).append('\n');
        csv.append("Consolidado con servicios,")
                .append(indicators.partial() ? "NO (ms-04 no respondio)" : "SI").append('\n');
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
                .append(decimal(indicators.summary().averagePeoplePerReservation())).append('\n');

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

        csv.append('\n').append("Actividades\n");
        csv.append("Actividad,Categoria,Sitio,Zona,Guia,Reservas,Personas,Ingresos,Participacion,")
                .append("Capacidad,Ocupacion\n");
        for (ActivityDemand activity : indicators.activities()) {
            csv.append(value(activity.name())).append(',')
                    .append(value(activity.category())).append(',')
                    .append(value(activity.siteName())).append(',')
                    .append(value(activity.zoneName())).append(',')
                    .append(value(activity.guideId() == null ? null : activity.guideId().toString())).append(',')
                    .append(activity.reservations()).append(',')
                    .append(activity.people()).append(',')
                    .append(money(activity.revenue())).append(',')
                    .append(percent(activity.share())).append(',')
                    .append(activity.capacityPeople()).append(',')
                    .append(percent(activity.occupancyRate())).append('\n');
        }

        csv.append('\n').append("Eventos\n");
        csv.append("Evento,Categoria,Estado,Sitio,Zona,Gestora,Reservas,Personas,Ingresos,")
                .append("Participacion,Capacidad,Ocupacion,Afluencia esperada,Afluencia reservada %\n");
        for (EventDemand event : indicators.events()) {
            csv.append(value(event.name())).append(',')
                    .append(value(event.category())).append(',')
                    .append(value(event.status() == null ? null : event.status().name())).append(',')
                    .append(value(event.siteName())).append(',')
                    .append(value(event.zoneName())).append(',')
                    .append(value(event.managerId() == null ? null : event.managerId().toString())).append(',')
                    .append(event.reservations()).append(',')
                    .append(event.people()).append(',')
                    .append(money(event.revenue())).append(',')
                    .append(percent(event.share())).append(',')
                    .append(event.capacityPeople()).append(',')
                    .append(percent(event.occupancyRate())).append(',')
                    .append(event.expectedAttendance()).append(',')
                    .append(percent(event.attendanceRate())).append('\n');
        }

        csv.append('\n').append("Servicios (ms-04)\n");
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
        String cell = neutralizeFormula(value);
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }

    /**
     * Antepone un apostrofo para que la hoja trate el contenido como texto: es el mecanismo que usan
     * Excel y LibreOffice para forzar texto sin alterar el valor visible.
     */
    private static String neutralizeFormula(String value) {
        if (value.isEmpty() || FORMULA_PREFIXES.indexOf(value.charAt(0)) < 0) {
            return value;
        }
        return "'" + value;
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

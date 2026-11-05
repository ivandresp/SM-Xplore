package com.smxplore.activitiesevents.application.indicator;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smxplore.activitiesevents.domain.model.indicator.ActivityDemand;
import com.smxplore.activitiesevents.domain.model.indicator.DemandIndicators;
import com.smxplore.activitiesevents.domain.model.indicator.EventDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ServiceDemand;
import com.smxplore.activitiesevents.domain.model.indicator.SiteDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ZoneDemand;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Planning report in PDF. It uses the same data as the API and the CSV; the only purpose is to give
 * the destination manager a printable document.
 */
public final class DemandPdfFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(33, 37, 41));
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(90, 96, 105));
    private static final Font SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(33, 37, 41));
    private static final Font HEADER = new Font(Font.HELVETICA, 7.5f, Font.BOLD, new Color(33, 37, 41));
    private static final Font CELL = new Font(Font.HELVETICA, 7f, Font.NORMAL, new Color(33, 37, 41));
    private static final Color HEADER_BACKGROUND = new Color(226, 232, 240);

    private DemandPdfFormatter() {
    }

    public static byte[] format(DemandIndicators indicators) {
        var output = new ByteArrayOutputStream();
        var zone = ZoneId.of(indicators.timeZone());
        var document = new Document(PageSize.A4.rotate(), 28, 28, 30, 30);
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("Indicadores de demanda - SM-Xplore", TITLE));
            document.add(new Paragraph("Desde " + DATE_TIME.format(indicators.from().atZone(zone))
                    + " hasta " + DATE_TIME.format(indicators.to().atZone(zone))
                    + " · alcance " + indicators.scope()
                    + (indicators.partial() ? " · servicios de ms-04 no disponibles" : ""), SUBTITLE));
            document.add(new Paragraph("Resumen", SECTION));
            document.add(new Paragraph("Reservas: " + indicators.summary().reservations()
                    + " · Personas: " + indicators.summary().people()
                    + " · Ingresos: " + indicators.summary().revenue().toPlainString()
                    + " · Canceladas: " + indicators.summary().cancelledReservations()
                    + " · Completadas: " + indicators.summary().completedReservations()
                    + " · Turistas unicos: " + indicators.summary().uniqueTourists()
                    + " · Cancelacion: " + percent(indicators.summary().cancellationRate())
                    + " · Promedio por reserva: "
                    + decimal(indicators.summary().averagePeoplePerReservation()), CELL));
            document.add(new Paragraph("Comparacion: reservas "
                    + decimal(indicators.comparison().reservationsChange()) + "%, personas "
                    + decimal(indicators.comparison().peopleChange()) + "%, ingresos "
                    + decimal(indicators.comparison().revenueChange()) + "%", CELL));

            addZones(document, indicators);
            addSites(document, indicators);
            addActivities(document, indicators);
            addEvents(document, indicators);
            addServices(document, indicators);
            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Could not generate the demand PDF report.", exception);
        }
    }

    private static void addZones(Document document, DemandIndicators indicators) throws DocumentException {
        if (indicators.zones().isEmpty()) {
            return;
        }
        document.add(new Paragraph("Zonas", SECTION));
        var table = table(new float[]{2.2f, 1.6f, 1.2f, 1.2f, 1.5f, 1.2f, 1.3f, 1.2f});
        header(table, "Zona", "Estado", "Reservas", "Personas", "Ingresos", "Participacion",
                "Capacidad", "Ocupacion");
        for (ZoneDemand zone : indicators.zones()) {
            row(table, zone.zoneName(), zone.zoneStatus(), zone.reservations(), zone.people(),
                    money(zone.revenue()), percent(zone.share()), zone.capacityPeople(),
                    percent(zone.occupancyRate()));
        }
        document.add(table);
    }

    private static void addSites(Document document, DemandIndicators indicators) throws DocumentException {
        if (indicators.sites().isEmpty()) {
            return;
        }
        document.add(new Paragraph("Sitios", SECTION));
        var table = table(new float[]{2.2f, 2.0f, 1.2f, 1.2f, 1.5f, 1.2f, 1.3f, 1.2f});
        header(table, "Sitio", "Zona", "Reservas", "Personas", "Ingresos", "Participacion",
                "Capacidad", "Ocupacion");
        for (SiteDemand site : indicators.sites()) {
            row(table, site.siteName(), site.zoneName(), site.reservations(), site.people(),
                    money(site.revenue()), percent(site.share()), site.capacityPeople(),
                    percent(site.occupancyRate()));
        }
        document.add(table);
    }

    private static void addActivities(Document document, DemandIndicators indicators) throws DocumentException {
        if (indicators.activities().isEmpty()) {
            return;
        }
        document.add(new Paragraph("Actividades", SECTION));
        var table = table(new float[]{2.4f, 1.6f, 2.0f, 1.2f, 1.2f, 1.5f, 1.2f, 1.3f, 1.2f});
        header(table, "Actividad", "Categoria", "Sitio", "Reservas", "Personas", "Ingresos",
                "Participacion", "Capacidad", "Ocupacion");
        for (ActivityDemand activity : indicators.activities()) {
            row(table, activity.name(), activity.category(), activity.siteName(), activity.reservations(),
                    activity.people(), money(activity.revenue()), percent(activity.share()),
                    activity.capacityPeople(), percent(activity.occupancyRate()));
        }
        document.add(table);
    }

    private static void addEvents(Document document, DemandIndicators indicators) throws DocumentException {
        if (indicators.events().isEmpty()) {
            return;
        }
        document.add(new Paragraph("Eventos", SECTION));
        var table = table(new float[]{2.2f, 1.4f, 1.4f, 1.2f, 1.2f, 1.5f, 1.2f, 1.3f, 1.2f,
                1.3f, 1.4f});
        header(table, "Evento", "Categoria", "Estado", "Reservas", "Personas", "Ingresos",
                "Participacion", "Capacidad", "Ocupacion", "Esperada", "Reservada %");
        for (EventDemand event : indicators.events()) {
            row(table, event.name(), event.category(), event.status() == null ? null : event.status().name(),
                    event.reservations(), event.people(), money(event.revenue()), percent(event.share()),
                    event.capacityPeople(), percent(event.occupancyRate()), event.expectedAttendance(),
                    percent(event.attendanceRate()));
        }
        document.add(table);
    }

    private static void addServices(Document document, DemandIndicators indicators) throws DocumentException {
        if (indicators.services().isEmpty()) {
            return;
        }
        document.add(new Paragraph("Servicios (ms-04)", SECTION));
        var table = table(new float[]{2.2f, 1.5f, 2.0f, 2.0f, 1.2f, 1.2f, 1.5f, 1.2f, 1.3f, 1.2f});
        header(table, "Servicio", "Tipo", "Sitio", "Zona", "Reservas", "Personas", "Ingresos",
                "Participacion", "Capacidad", "Ocupacion");
        for (ServiceDemand service : indicators.services()) {
            row(table, service.serviceName(), service.serviceType(), service.siteName(), service.zoneName(),
                    service.reservations(), service.people(), money(service.revenue()),
                    percent(service.share()), service.capacityPeople(), percent(service.occupancyRate()));
        }
        document.add(table);
    }

    private static PdfPTable table(float[] widths) {
        var table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setHeaderRows(1);
        return table;
    }

    private static void header(PdfPTable table, String... values) {
        for (String value : values) {
            var cell = new PdfPCell(new Phrase(value, HEADER));
            cell.setBackgroundColor(HEADER_BACKGROUND);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            cell.setPadding(3);
            table.addCell(cell);
        }
    }

    private static void row(PdfPTable table, Object... values) {
        for (Object value : values) {
            var cell = new PdfPCell(new Phrase(value(value), CELL));
            cell.setPadding(3);
            table.addCell(cell);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String money(java.math.BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f", value * 100.0);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}

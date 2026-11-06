package com.smxplore.users.infrastructure.adapter.out.report;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.port.out.AuditPdfRenderer;
import com.smxplore.users.domain.model.AuditEntry;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Renderiza el reporte de auditoría en PDF con OpenPDF. Usa las fuentes base con codificación WinAnsi, que cubren los
 * acentos del español sin embeber tipografías.
 */
@Component
public class OpenPdfAuditRenderer implements AuditPdfRenderer {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(33, 37, 41));
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(90, 96, 105));
    private static final Font HEADER = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(33, 37, 41));
    private static final Font CELL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, new Color(33, 37, 41));
    private static final Color HEADER_BACKGROUND = new Color(226, 232, 240);
    private static final int MAX_DETAIL_LENGTH = 160;

    @Override
    public byte[] render(List<AuditEntry> entries, AuditSearchCriteria criteria, Instant generatedAt) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 32, 32, 36, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new Footer());
            document.open();
            document.add(new Paragraph("Reporte de auditoría", TITLE));
            document.add(new Chunk("\n"));
            document.add(new Paragraph(
                    "SM-Xplore · Generado el " + DATE_TIME.format(generatedAt) + " UTC · " + entries.size()
                            + (entries.size() == 1 ? " registro" : " registros"),
                    SUBTITLE));
            document.add(new Paragraph("Filtros: " + filters(criteria), SUBTITLE));
            document.add(new Chunk("\n"));
            if (entries.isEmpty()) {
                document.add(new Paragraph("No hay registros para los filtros seleccionados.", CELL));
            } else {
                document.add(table(entries));
            }
            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("No se pudo generar el PDF de auditoría", exception);
        }
    }

    private static PdfPTable table(List<AuditEntry> entries) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] {2.1f, 2.6f, 1.5f, 2.3f, 2.0f, 4.2f});
        table.setWidthPercentage(100);
        header(table, "Fecha (UTC)");
        header(table, "Actor");
        header(table, "Rol");
        header(table, "Acción");
        header(table, "Afectado");
        header(table, "Detalles");
        for (AuditEntry entry : entries) {
            cell(table, DATE_TIME.format(entry.occurredAt()));
            cell(table, entry.actorEmail());
            cell(table, entry.actorRole().code());
            cell(table, entry.action().name());
            cell(
                    table,
                    entry.targetUserId() == null ? "—" : entry.targetUserId().toString());
            cell(table, details(entry));
        }
        return table;
    }

    private static void header(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER));
        cell.setBackgroundColor(HEADER_BACKGROUND);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static void cell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, CELL));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static String details(AuditEntry entry) {
        String value = entry.details().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(detail -> detail.getKey() + "=" + detail.getValue())
                .collect(Collectors.joining("; "));
        return value.length() <= MAX_DETAIL_LENGTH ? value : value.substring(0, MAX_DETAIL_LENGTH - 3) + "...";
    }

    private static String filters(AuditSearchCriteria criteria) {
        List<String> parts = new ArrayList<>();
        if (criteria.actorId() != null) {
            parts.add("actor=" + criteria.actorId());
        }
        if (criteria.action() != null) {
            parts.add("acción=" + criteria.action().name());
        }
        if (criteria.targetUserId() != null) {
            parts.add("afectado=" + criteria.targetUserId());
        }
        if (criteria.from() != null) {
            parts.add("desde=" + DATE_TIME.format(criteria.from()));
        }
        if (criteria.to() != null) {
            parts.add("hasta=" + DATE_TIME.format(criteria.to()));
        }
        return parts.isEmpty() ? "sin filtros" : String.join(" · ", parts);
    }

    /** Pie de página con numeración. */
    private static final class Footer extends PdfPageEventHelper {

        private static final Font FOOTER = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, new Color(120, 126, 134));

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_RIGHT,
                    new Phrase("SM-Xplore · Página " + writer.getPageNumber(), FOOTER),
                    document.right(),
                    document.bottom() - 14,
                    0);
        }
    }
}

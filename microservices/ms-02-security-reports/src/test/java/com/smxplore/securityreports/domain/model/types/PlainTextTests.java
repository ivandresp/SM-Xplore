package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;
import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS02-06: el contenido se rechaza si contiene HTML o caracteres de control (XSS almacenado si un cliente lo
 * renderizara como HTML).
 */
class PlainTextTests {

    @Test
    void rejectsHtmlTagsInReportTitles() {
        assertThatThrownBy(() -> reportWith("<script>alert(1)</script>").validate())
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("HTML is not allowed");
    }

    @Test
    void rejectsHtmlTagsInDescriptions() {
        var report = baseReport().toBuilder()
                .description("<img src=x onerror=alert(1)>")
                .build();

        assertThatThrownBy(report::validate)
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("HTML is not allowed");
    }

    @Test
    void rejectsHtmlTagsInComments() {
        var comment = Comment.builder()
                .reportId(UUID.randomUUID())
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .content("<a href=\"https://attacker.example\">click</a>")
                .build();

        assertThatThrownBy(comment::validate)
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("HTML is not allowed");
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(() -> reportWith("Hola\u0000mundo").validate())
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("control characters");
    }

    @Test
    void stillAllowsPlainTextWithAngleBracketsThatAreNotTags() {
        assertThatCode(() -> reportWith("Robo a < 5 minutos de la playa").validate())
                .doesNotThrowAnyException();
    }

    private static SecurityReport reportWith(String title) {
        return baseReport().toBuilder().title(title).build();
    }

    private static SecurityReport baseReport() {
        return SecurityReport.builder()
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .title("Reporte de prueba")
                .description("Descripcion de prueba")
                .build();
    }
}

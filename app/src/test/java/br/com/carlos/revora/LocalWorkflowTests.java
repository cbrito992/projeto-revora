package br.com.carlos.revora;

import br.com.carlos.revora.model.*;
import br.com.carlos.revora.service.DocumentoService;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local"})
class LocalWorkflowTests {
    @LocalServerPort int port;
    @Autowired DocumentoService documentos;
    final HttpClient client = HttpClient.newHttpClient();

    byte[] docx(String text, int size) throws Exception {
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            var run = doc.createParagraph().createRun(); run.setText(text); run.setFontSize(size);
            var page = doc.getDocument().getBody().addNewSectPr().addNewPgSz();
            page.setW(java.math.BigInteger.valueOf(size == 12 ? 11906 : 14000));
            page.setH(java.math.BigInteger.valueOf(16838));
            doc.write(out); return out.toByteArray();
        }
    }
    String text(byte[] bytes) throws Exception {
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return doc.getParagraphs().stream().map(XWPFParagraph::getText).reduce("", (a,b) -> a + "\n" + b);
        }
    }
    HttpResponse<byte[]> request(String path, String contentType, byte[] body, boolean trusted) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null) builder.GET();
        else {
            builder.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (trusted) builder.header("X-Revora-Local", "1");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    byte[] multipart(byte[] template, byte[] document) throws Exception {
        var out = new ByteArrayOutputStream();
        for (String name : List.of("template", "documento")) {
            out.write(("--revora-test\r\nContent-Disposition: form-data; name=\"" + name
                    + "\"; filename=\"" + name + ".docx\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(name.equals("template") ? template : document); out.write("\r\n".getBytes());
        }
        out.write("--revora-test--\r\n".getBytes()); return out.toByteArray();
    }
    @Test void browserCanReviewWithoutBlobOrAi() throws Exception {
        byte[] source = docx("Uma palavra zzzzzzzinexistente no documento.", 18);
        var response = request("/api/local/revisar", "multipart/form-data; boundary=revora-test",
                multipart(docx("Texto de referência.", 12), source), true);
        assertEquals(200, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        assertTrue(response.headers().firstValue("Content-Disposition").orElseThrow().contains(".docx"));
        String reviewed = text(response.body());
        assertTrue(reviewed.contains("zzzzzzzinexistente"));
        assertTrue(reviewed.toLowerCase().contains("relatório"));
        assertFalse(java.util.Arrays.equals(source, response.body()));
        try (var doc = new XWPFDocument(new ByteArrayInputStream(response.body()))) {
            assertTrue(doc.getParagraphs().stream().flatMap(p -> p.getRuns().stream())
                    .anyMatch(r -> r.getCTR().isSetRPr() && r.getCTR().getRPr().sizeOfHighlightArray() > 0));
        }
    }
    @Test void editorialFailureStillProducesDocxAndWarning() throws Exception {
        byte[] input = docx("Texto para revisar.", 12);
        byte[] result = documentos.processarEModificarDocumento(new ByteArrayInputStream(input),
                new ByteArrayInputStream(input), trechos -> { throw new IOException("provider unavailable"); });
        assertTrue(text(result).contains("temporariamente indisponível"));
    }
    @Test void editorialSuggestionsAreIncludedInReport() throws Exception {
        byte[] input = docx("Texto para revisar.", 12);
        byte[] result = documentos.processarEModificarDocumento(new ByteArrayInputStream(input),
                new ByteArrayInputStream(input), trechos -> new ResultadoAnaliseEditorial(List.of(
                        new OcorrenciaEditorial(trechos.get(0).paragrafoId(), CategoriaEditorial.ABNT,
                                "citacao", "Texto", "Conferir a fonte", "Revisar referência", 0.99))));
        assertTrue(text(result).contains("Conferir a fonte"));
    }
    @Test void rejectsUntrustedPostsInvalidDocxAndDisabledAiTest() throws Exception {
        byte[] settings = "{\"enabled\":false}".getBytes();
        assertEquals(403, request("/api/local/ia/testar", "application/json", settings, false).statusCode());
        assertEquals(400, request("/api/local/ia/testar", "application/json", settings, true).statusCode());
        assertEquals(400, request("/api/local/revisar", "multipart/form-data; boundary=revora-test",
                multipart("bad".getBytes(), "bad".getBytes()), true).statusCode());
        var foreign = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/local/ia/testar"))
                .header("Content-Type", "application/json").header("Origin", "https://example.com")
                .header("X-Revora-Local", "1").POST(HttpRequest.BodyPublishers.ofByteArray(settings)).build();
        assertEquals(403, client.send(foreign, HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(200, request("/local.html", null, null, false).statusCode());
        assertEquals(200, request("/api/local/health", null, null, false).statusCode());
        assertEquals(404, request("/api/revisar", "application/json", settings, true).statusCode());
    }
}

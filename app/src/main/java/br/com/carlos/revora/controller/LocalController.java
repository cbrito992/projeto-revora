package br.com.carlos.revora.controller;

import br.com.carlos.revora.dto.ConfiguracaoIa;
import br.com.carlos.revora.model.ResultadoAnaliseEditorial;
import br.com.carlos.revora.service.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@Profile("local")
public class LocalController {
    private final DocumentoService documentos;
    private final GuiaEditorialService guia;
    private final ObjectMapper json = new ObjectMapper();

    public LocalController(DocumentoService documentos, GuiaEditorialService guia) {
        this.documentos = documentos;
        this.guia = guia;
    }

    @GetMapping("/api/local/health")
    public Map<String, String> health() { return Map.of("status", "ok", "service", "revora-local"); }

    @GetMapping("/")
    public ResponseEntity<Void> inicio() {
        return ResponseEntity.status(302).header("Location", "/local.html").build();
    }

    @PostMapping(value = "/api/local/revisar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> revisar(@RequestParam MultipartFile template,
            @RequestParam MultipartFile documento, @RequestHeader(value = "X-Revora-IA", defaultValue = "{\"enabled\":false}") String configuracao) {
        try {
            validarArquivo(template);
            validarArquivo(documento);
            ConfiguracaoIa config = json.readValue(configuracao, ConfiguracaoIa.class);
            config.validar();
            AnaliseEditorialIaService ia = config.enabled() ? cliente(config)
                    : trechos -> new ResultadoAnaliseEditorial(List.of());
            try (InputStream t = template.getInputStream(); InputStream d = documento.getInputStream()) {
                byte[] resultado = documentos.processarEModificarDocumento(t, d, ia);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documento_revisado.docx\"")
                        .body(resultado);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro",
                    "Não foi possível ler os DOCX. Verifique se são documentos válidos e não protegidos por senha."));
        }
    }

    @PostMapping("/api/local/ia/testar")
    public ResponseEntity<?> testar(@RequestBody ConfiguracaoIa config) {
        try {
            if (!config.enabled()) throw new IllegalArgumentException("Ative a IA para testar a conexão.");
            config.validar();
            cliente(config).testarConexao();
            return ResponseEntity.ok(Map.of("mensagem", "Conexão confirmada com o modelo selecionado."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("erro",
                    IaConnectionException.from(e).getMessage()));
        }
    }

    private GeminiAnaliseEditorialIaService cliente(ConfiguracaoIa c) {
        return new GeminiAnaliseEditorialIaService(guia, c.provider(), c.model(), c.apiKey());
    }

    private void validarArquivo(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 50L * 1024 * 1024
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".docx"))
            throw new IllegalArgumentException("Selecione arquivos DOCX de até 50 MB, não vazios.");
    }
}

package br.com.carlos.revora.controller;

import br.com.carlos.revora.dto.RevisaoRequest;
import br.com.carlos.revora.dto.RevisaoResponse;
import br.com.carlos.revora.service.RevisaoService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentoController {

    private final RevisaoService revisaoService;

    public DocumentoController(
            RevisaoService revisaoService
    ) {
        this.revisaoService = revisaoService;
    }

    @PostMapping("/revisar")
    public ResponseEntity<?> revisar(
            @RequestBody RevisaoRequest request
    ) {

        if (
                request == null
                        || request.sessao() == null
                        || request.sessao().isBlank()
                        || request.templateReadUrl() == null
                        || request.templateReadUrl().isBlank()
                        || request.documentoReadUrl() == null
                        || request.documentoReadUrl().isBlank()
                        || request.resultadoUploadUrl() == null
                        || request.resultadoUploadUrl().isBlank()
        ) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "erro",
                                    "Dados da revisão incompletos."
                            )
                    );
        }

        try {

            revisaoService.processar(request);

            return ResponseEntity.ok(
                    new RevisaoResponse(
                            "concluido",
                            request.sessao()
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "erro",
                                    e.getMessage()
                            )
                    );

        } catch (Exception e) {

            System.err.println(
                    "Falha ao processar revisão: "
                            + e.getClass().getSimpleName()
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "erro",
                                    "Não foi possível concluir a revisão."
                            )
                    );
        }
    }
}
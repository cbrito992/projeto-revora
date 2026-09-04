package br.com.carlos.revora.service;

import br.com.carlos.revora.dto.RevisaoRequest;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class RevisaoService {

    private final DocumentoService documentoService;
    private final ArquivoRemotoService arquivoRemotoService;

    public RevisaoService(
            DocumentoService documentoService,
            ArquivoRemotoService arquivoRemotoService
    ) {
        this.documentoService = documentoService;
        this.arquivoRemotoService = arquivoRemotoService;
    }

    public void processar(
            RevisaoRequest request
    ) throws Exception {

        try (
                InputStream template =
                        arquivoRemotoService.baixar(
                                request.templateReadUrl()
                        );

                InputStream documento =
                        arquivoRemotoService.baixar(
                                request.documentoReadUrl()
                        )
        ) {

            byte[] resultado =
                    documentoService.processarEModificarDocumento(
                            template,
                            documento
                    );

            arquivoRemotoService.enviar(
                    request.resultadoUploadUrl(),
                    resultado
            );
        }
    }
}
package br.com.carlos.revora.dto;

public record RevisaoRequest(
        String sessao,
        String templateReadUrl,
        String documentoReadUrl,
        String resultadoUploadUrl
) {
}
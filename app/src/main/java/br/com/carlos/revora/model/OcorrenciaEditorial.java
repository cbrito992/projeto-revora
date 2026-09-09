package br.com.carlos.revora.model;

public record OcorrenciaEditorial(
        String paragrafoId,
        CategoriaEditorial categoria,
        String subtipo,
        String evidencia,
        String motivo,
        String sugestao,
        double confianca
) {
}
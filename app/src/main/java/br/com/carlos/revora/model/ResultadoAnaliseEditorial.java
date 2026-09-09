package br.com.carlos.revora.model;

import java.util.List;

public record ResultadoAnaliseEditorial(
        List<OcorrenciaEditorial> ocorrencias
) {

    public ResultadoAnaliseEditorial {

        if (ocorrencias == null) {
            ocorrencias = List.of();
        }
    }
}
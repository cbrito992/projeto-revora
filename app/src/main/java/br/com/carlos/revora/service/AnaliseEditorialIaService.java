package br.com.carlos.revora.service;

import br.com.carlos.revora.model.ResultadoAnaliseEditorial;
import br.com.carlos.revora.model.TrechoEditorial;

import java.util.List;

public interface AnaliseEditorialIaService {

    ResultadoAnaliseEditorial analisar(
            List<TrechoEditorial> trechos
    ) throws Exception;
}
package br.com.carlos.revora.model;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;

public class FormatacaoProfile {

    private String fonte;

    private Double tamanhoFonte;

    private ParagraphAlignment alinhamento;

    private Double espacamentoEntreLinhas;

    private Integer recuoEsquerdo;

    private Integer recuoDireito;

    private Boolean negrito;

    private Boolean italico;


    public String getFonte() {
        return fonte;
    }

    public void setFonte(String fonte) {
        this.fonte = fonte;
    }


    public Double getTamanhoFonte() {
        return tamanhoFonte;
    }

    public void setTamanhoFonte(Double tamanhoFonte) {
        this.tamanhoFonte = tamanhoFonte;
    }


    public ParagraphAlignment getAlinhamento() {
        return alinhamento;
    }

    public void setAlinhamento(
            ParagraphAlignment alinhamento
    ) {
        this.alinhamento = alinhamento;
    }


    public Double getEspacamentoEntreLinhas() {
        return espacamentoEntreLinhas;
    }

    public void setEspacamentoEntreLinhas(
            Double espacamentoEntreLinhas
    ) {
        this.espacamentoEntreLinhas =
                espacamentoEntreLinhas;
    }


    public Integer getRecuoEsquerdo() {
        return recuoEsquerdo;
    }

    public void setRecuoEsquerdo(
            Integer recuoEsquerdo
    ) {
        this.recuoEsquerdo = recuoEsquerdo;
    }


    public Integer getRecuoDireito() {
        return recuoDireito;
    }

    public void setRecuoDireito(
            Integer recuoDireito
    ) {
        this.recuoDireito = recuoDireito;
    }


    public Boolean getNegrito() {
        return negrito;
    }

    public void setNegrito(Boolean negrito) {
        this.negrito = negrito;
    }


    public Boolean getItalico() {
        return italico;
    }

    public void setItalico(Boolean italico) {
        this.italico = italico;
    }
}
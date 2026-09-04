package br.com.carlos.revora.model;

public class ConvencaoNumeradaProfile {

    private String rotuloNormalizado;

    private String rotuloOriginal;

    private String separador;

    private int ocorrencias;


    public String getRotuloNormalizado() {
        return rotuloNormalizado;
    }

    public void setRotuloNormalizado(
            String rotuloNormalizado
    ) {
        this.rotuloNormalizado =
                rotuloNormalizado;
    }


    public String getRotuloOriginal() {
        return rotuloOriginal;
    }

    public void setRotuloOriginal(
            String rotuloOriginal
    ) {
        this.rotuloOriginal =
                rotuloOriginal;
    }


    public String getSeparador() {
        return separador;
    }

    public void setSeparador(
            String separador
    ) {
        this.separador = separador;
    }


    public int getOcorrencias() {
        return ocorrencias;
    }

    public void setOcorrencias(
            int ocorrencias
    ) {
        this.ocorrencias = ocorrencias;
    }
}
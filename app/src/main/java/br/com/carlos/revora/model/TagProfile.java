package br.com.carlos.revora.model;

public class TagProfile {

    private String nomeNormalizado;

    private String representacaoPreferida;

    private int ocorrencias;


    public String getNomeNormalizado() {
        return nomeNormalizado;
    }

    public void setNomeNormalizado(
            String nomeNormalizado
    ) {
        this.nomeNormalizado =
                nomeNormalizado;
    }


    public String getRepresentacaoPreferida() {
        return representacaoPreferida;
    }

    public void setRepresentacaoPreferida(
            String representacaoPreferida
    ) {
        this.representacaoPreferida =
                representacaoPreferida;
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
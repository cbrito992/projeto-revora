package br.com.carlos.revora.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class TemplateProfile {

    private LayoutProfile layout;

    private FormatacaoProfile corpoTexto;

    private Map<String, TagProfile> tags =
            new LinkedHashMap<>();

    private Map<String, ConvencaoNumeradaProfile>
            convencoesNumeradas =
            new LinkedHashMap<>();


    public LayoutProfile getLayout() {
        return layout;
    }

    public void setLayout(
            LayoutProfile layout
    ) {
        this.layout = layout;
    }


    public FormatacaoProfile getCorpoTexto() {
        return corpoTexto;
    }

    public void setCorpoTexto(
            FormatacaoProfile corpoTexto
    ) {
        this.corpoTexto = corpoTexto;
    }


    public Map<String, TagProfile> getTags() {
        return tags;
    }

    public void setTags(
            Map<String, TagProfile> tags
    ) {
        this.tags = tags;
    }


    public Map<String, ConvencaoNumeradaProfile>
            getConvencoesNumeradas() {

        return convencoesNumeradas;
    }

    public void setConvencoesNumeradas(
            Map<String, ConvencaoNumeradaProfile>
                    convencoesNumeradas
    ) {

        this.convencoesNumeradas =
                convencoesNumeradas;
    }
}
package br.com.carlos.revora.service;

import br.com.carlos.revora.model.ConvencaoNumeradaProfile;
import br.com.carlos.revora.model.FormatacaoProfile;
import br.com.carlos.revora.model.LayoutProfile;
import br.com.carlos.revora.model.TagProfile;
import br.com.carlos.revora.model.TemplateProfile;

import org.apache.poi.xwpf.usermodel.*;

import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import org.springframework.stereotype.Service;

import java.math.BigInteger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class TemplateService {

    private static final Locale PT_BR =
            Locale.forLanguageTag("pt-BR");


    /*
     * Detecta elementos numerados sem impor
     * previamente "Figura", "Quadro" etc.
     *
     * Exemplos:
     *
     * Figura 1 – Título
     * FIGURA 1: Título
     * Quadro 2. Título
     * Tabela 3 - Título
     */
    private static final Pattern PADRAO_NUMERADO =
            Pattern.compile(
                    "^([\\p{L}][\\p{L}\\s]{0,30}?)\\s+" +
                    "(\\d+)\\s*" +
                    "([–—\\-\\.:])\\s*" +
                    ".+"
            );


    public TemplateProfile analisar(
            XWPFDocument documento
    ) {

        TemplateProfile profile =
                new TemplateProfile();


        profile.setLayout(
                analisarLayout(documento)
        );


        profile.setCorpoTexto(
                analisarFormatacaoPredominante(
                        documento
                )
        );


        profile.setTags(
                analisarTags(documento)
        );


        profile.setConvencoesNumeradas(
                analisarConvencoesNumeradas(
                        documento
                )
        );


        return profile;
    }


    /**
     * =========================================================
     * LAYOUT
     * =========================================================
     */
    private LayoutProfile analisarLayout(
            XWPFDocument documento
    ) {

        LayoutProfile profile =
                new LayoutProfile();


        CTSectPr sectPr =
                documento
                        .getDocument()
                        .getBody()
                        .getSectPr();


        if (sectPr == null) {
            return profile;
        }


        CTPageSz pageSize =
                sectPr.getPgSz();


        if (pageSize != null) {

            double largura =
                    twipsParaCm(
                            pageSize.getW()
                    );


            double altura =
                    twipsParaCm(
                            pageSize.getH()
                    );


            profile.setLarguraPaginaCm(
                    largura
            );

            profile.setAlturaPaginaCm(
                    altura
            );


            profile.setPaisagem(
                    largura > altura
            );
        }


        CTPageMar margens =
                sectPr.getPgMar();


        if (margens != null) {

            profile.setMargemSuperiorCm(
                    twipsParaCm(
                            margens.getTop()
                    )
            );


            profile.setMargemInferiorCm(
                    twipsParaCm(
                            margens.getBottom()
                    )
            );


            profile.setMargemEsquerdaCm(
                    twipsParaCm(
                            margens.getLeft()
                    )
            );


            profile.setMargemDireitaCm(
                    twipsParaCm(
                            margens.getRight()
                    )
            );
        }


        return profile;
    }


    /**
     * =========================================================
     * FORMATAÇÃO PREDOMINANTE DO CORPO
     * =========================================================
     *
     * Em vez de contar runs, usamos a quantidade
     * de caracteres.
     *
     * Um run com 300 caracteres pesa mais do que
     * um título de cinco caracteres.
     */
    private FormatacaoProfile analisarFormatacaoPredominante(
            XWPFDocument documento
    ) {

        Map<String, Integer> fontes =
                new HashMap<>();


        Map<Double, Integer> tamanhos =
                new HashMap<>();


        Map<ParagraphAlignment, Integer>
                alinhamentos =
                new HashMap<>();


        Map<Double, Integer> espacamentos =
                new HashMap<>();


        for (XWPFParagraph paragrafo :
                documento.getParagraphs()) {

            String texto =
                    paragrafo.getText().trim();


            /*
             * Tentamos trabalhar apenas com
             * parágrafos que se parecem com corpo textual.
             */
            if (!ehParagrafoDeCorpo(texto)) {
                continue;
            }


            int pesoParagrafo =
                    Math.max(
                            texto.length(),
                            1
                    );


            ParagraphAlignment alinhamento =
                    paragrafo.getAlignment();


            if (alinhamento != null) {

                alinhamentos.merge(
                        alinhamento,
                        pesoParagrafo,
                        Integer::sum
                );
            }


            double espacamento =
                    paragrafo.getSpacingBetween();


            /*
             * 0 normalmente significa
             * "não especificado diretamente".
             */
            if (espacamento > 0) {

                espacamentos.merge(
                        arredondar(
                                espacamento,
                                2
                        ),
                        pesoParagrafo,
                        Integer::sum
                );
            }


            for (XWPFRun run :
                    paragrafo.getRuns()) {

                String textoRun =
                        run.text();


                if (
                        textoRun == null
                                || textoRun.isBlank()
                ) {

                    continue;
                }


                int peso =
                        textoRun.length();


                String fonte =
                        run.getFontFamily();


                if (fonte != null &&
                        !fonte.isBlank()) {

                    fontes.merge(
                            fonte.trim(),
                            peso,
                            Integer::sum
                    );
                }


                Double tamanho =
                        run.getFontSizeAsDouble();


                if (
                        tamanho != null
                                && tamanho > 0
                ) {

                    tamanhos.merge(
                            arredondar(
                                    tamanho,
                                    1
                            ),
                            peso,
                            Integer::sum
                    );
                }
            }
        }


        FormatacaoProfile profile =
                new FormatacaoProfile();


        profile.setFonte(
                maiorOcorrencia(fontes)
        );


        profile.setTamanhoFonte(
                maiorOcorrencia(tamanhos)
        );


        profile.setAlinhamento(
                maiorOcorrencia(
                        alinhamentos
                )
        );


        profile.setEspacamentoEntreLinhas(
                maiorOcorrencia(
                        espacamentos
                )
        );


        return profile;
    }


    /**
     * =========================================================
     * TAGS
     * =========================================================
     */
    private Map<String, TagProfile> analisarTags(
            XWPFDocument documento
    ) {

        Map<String, Integer> frequencias =
                new LinkedHashMap<>();


        Map<String, Map<String, Integer>>
                representacoes =
                new LinkedHashMap<>();


        for (XWPFParagraph paragrafo :
                documento.getParagraphs()) {

            String original =
                    paragrafo.getText().trim();


            if (!pareceMarcador(original)) {
                continue;
            }


            String normalizado =
                    normalizarTag(original);


            if (normalizado.isBlank()) {
                continue;
            }


            frequencias.merge(
                    normalizado,
                    1,
                    Integer::sum
            );


            representacoes
                    .computeIfAbsent(
                            normalizado,
                            chave ->
                                    new LinkedHashMap<>()
                    )
                    .merge(
                            original,
                            1,
                            Integer::sum
                    );
        }


        Map<String, TagProfile> resultado =
                new LinkedHashMap<>();


        for (Map.Entry<String, Integer> entry :
                frequencias.entrySet()) {

            /*
             * O principal filtro:
             *
             * estruturas repetidas têm alta probabilidade
             * de serem tags de abertura/fechamento.
             *
             * Isso evita transformar títulos únicos
             * em estruturas obrigatórias.
             */
            if (entry.getValue() < 2) {
                continue;
            }


            String nome =
                    entry.getKey();


            String preferida =
                    maiorOcorrencia(
                            representacoes.get(nome)
                    );


            TagProfile tag =
                    new TagProfile();


            tag.setNomeNormalizado(
                    nome
            );


            tag.setRepresentacaoPreferida(
                    preferida
            );


            tag.setOcorrencias(
                    entry.getValue()
            );


            resultado.put(
                    nome,
                    tag
            );
        }


        return resultado;
    }


    /**
     * =========================================================
     * CONVENÇÕES NUMERADAS
     * =========================================================
     */
    private Map<String, ConvencaoNumeradaProfile>
            analisarConvencoesNumeradas(
                    XWPFDocument documento
    ) {

        Map<String, Map<String, Integer>>
                separadores =
                new LinkedHashMap<>();


        Map<String, String>
                rotulosOriginais =
                new LinkedHashMap<>();


        Map<String, Integer>
                frequencias =
                new LinkedHashMap<>();


        for (XWPFParagraph paragrafo :
                documento.getParagraphs()) {

            String texto =
                    paragrafo.getText().trim();


            Matcher matcher =
                    PADRAO_NUMERADO.matcher(
                            texto
                    );


            if (!matcher.matches()) {
                continue;
            }


            String rotuloOriginal =
                    matcher.group(1).trim();


            String rotuloNormalizado =
                    rotuloOriginal
                            .toUpperCase(PT_BR);


            String separador =
                    matcher.group(3);


            rotulosOriginais.putIfAbsent(
                    rotuloNormalizado,
                    rotuloOriginal
            );


            frequencias.merge(
                    rotuloNormalizado,
                    1,
                    Integer::sum
            );


            separadores
                    .computeIfAbsent(
                            rotuloNormalizado,
                            chave ->
                                    new LinkedHashMap<>()
                    )
                    .merge(
                            separador,
                            1,
                            Integer::sum
                    );
        }


        Map<String, ConvencaoNumeradaProfile>
                resultado =
                new LinkedHashMap<>();


        for (String rotulo :
                frequencias.keySet()) {

            ConvencaoNumeradaProfile profile =
                    new ConvencaoNumeradaProfile();


            profile.setRotuloNormalizado(
                    rotulo
            );


            profile.setRotuloOriginal(
                    rotulosOriginais.get(
                            rotulo
                    )
            );


            profile.setSeparador(
                    maiorOcorrencia(
                            separadores.get(
                                    rotulo
                            )
                    )
            );


            profile.setOcorrencias(
                    frequencias.get(
                            rotulo
                    )
            );


            resultado.put(
                    rotulo,
                    profile
            );
        }


        return resultado;
    }


    /**
     * =========================================================
     * HEURÍSTICAS
     * =========================================================
     */
    private boolean ehParagrafoDeCorpo(
            String texto
    ) {

        if (
                texto == null
                        || texto.isBlank()
        ) {

            return false;
        }


        /*
         * Evita títulos, tags, legendas curtas etc.
         */
        if (texto.length() < 80) {
            return false;
        }


        if (pareceMarcador(texto)) {
            return false;
        }


        /*
         * Precisa possuir letras minúsculas.
         */
        boolean possuiMinuscula =
                texto.chars()
                        .anyMatch(
                                Character::isLowerCase
                        );


        return possuiMinuscula;
    }


    public boolean pareceMarcador(
            String texto
    ) {

        if (
                texto == null
                        || texto.isBlank()
                        || texto.length() > 70
        ) {

            return false;
        }


        /*
         * Marcadores explícitos possuem prioridade.
         */
        if (
                texto.contains("#")
                        || texto.startsWith("[")
                        || texto.endsWith("]")
                        || texto.startsWith("<")
                        || texto.endsWith(">")
        ) {

            return true;
        }


        String letras =
                texto.replaceAll(
                        "[^\\p{L}]",
                        ""
                );


        if (letras.isBlank()) {
            return false;
        }


        /*
         * Caixa alta curta é candidata.
         *
         * Só será realmente considerada uma tag
         * se aparecer repetidamente.
         */
        return letras.equals(
                letras.toUpperCase(PT_BR)
        );
    }


    public String normalizarTag(
            String texto
    ) {

        if (texto == null) {
            return "";
        }


        String resultado =
                texto.trim();


        resultado =
                resultado.replaceAll(
                        "^#+",
                        ""
                );


        resultado =
                resultado.replaceAll(
                        "#+$",
                        ""
                );


        resultado =
                resultado.replaceAll(
                        "^\\[+",
                        ""
                );


        resultado =
                resultado.replaceAll(
                        "\\]+$",
                        ""
                );


        resultado =
                resultado.replaceAll(
                        "^<+",
                        ""
                );


        resultado =
                resultado.replaceAll(
                        ">+$",
                        ""
                );


        resultado =
                resultado
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        return resultado.toUpperCase(
                PT_BR
        );
    }


    /**
     * =========================================================
     * UTILITÁRIOS
     * =========================================================
     */
    private double twipsParaCm(
            Object valor
    ) {

        if (valor == null) {
            return 0;
        }


        double twips;


        if (valor instanceof BigInteger bi) {

            twips =
                    bi.doubleValue();

        } else {

            try {

                twips =
                        Double.parseDouble(
                                valor.toString()
                        );

            } catch (NumberFormatException e) {

                return 0;
            }
        }


        /*
         * 1440 twips = 1 polegada
         * 1 polegada = 2,54 cm
         */
        return arredondar(
                twips / 1440.0 * 2.54,
                2
        );
    }


    private double arredondar(
            double valor,
            int casas
    ) {

        double fator =
                Math.pow(
                        10,
                        casas
                );


        return Math.round(
                valor * fator
        ) / fator;
    }


    private <T> T maiorOcorrencia(
            Map<T, Integer> mapa
    ) {

        if (
                mapa == null
                        || mapa.isEmpty()
        ) {

            return null;
        }


        T resultado =
                null;


        int maior =
                Integer.MIN_VALUE;


        for (Map.Entry<T, Integer> entry :
                mapa.entrySet()) {

            if (
                    entry.getValue()
                            > maior
            ) {

                maior =
                        entry.getValue();

                resultado =
                        entry.getKey();
            }
        }


        return resultado;
    }
}
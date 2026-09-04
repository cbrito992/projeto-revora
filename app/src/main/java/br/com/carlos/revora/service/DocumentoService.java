package br.com.carlos.revora.service;

import br.com.carlos.revora.model.ConvencaoNumeradaProfile;
import br.com.carlos.revora.model.FormatacaoProfile;
import br.com.carlos.revora.model.LayoutProfile;
import br.com.carlos.revora.model.TagProfile;
import br.com.carlos.revora.model.TemplateProfile;

import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.store.ByteBuffersDirectory;

import org.apache.poi.xwpf.usermodel.*;

import org.languagetool.rules.RuleMatch;

import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class DocumentoService {

    private static final Locale PT_BR =
            Locale.forLanguageTag("pt-BR");


    private static final Pattern PADRAO_PALAVRA =
            Pattern.compile(
                    "\\p{L}+(?:[-'’]\\p{L}+)*"
            );


    /*
     * O mesmo princípio usado no TemplateService:
     * não pressupõe Figura/Quadro/Tabela.
     */
    private static final Pattern PADRAO_NUMERADO =
            Pattern.compile(
                    "^([\\p{L}][\\p{L}\\s]{0,30}?)\\s+" +
                    "(\\d+)\\s*" +
                    "([–—\\-\\.:])\\s*" +
                    ".+"
            );


    private final GramaticaService
            gramaticaService;


    private final TemplateService
            templateService;


    private Hunspell hunspellChecker;


    public DocumentoService(
            GramaticaService gramaticaService,
            TemplateService templateService
    ) {

        this.gramaticaService =
                gramaticaService;

        this.templateService =
                templateService;
    }


    /**
     * =========================================================
     * HUNSPELL
     * =========================================================
     */
    private synchronized void carregarHunspell()
            throws Exception {

        if (hunspellChecker != null) {
            return;
        }


        try (
                InputStream affStream =
                        getClass()
                                .getResourceAsStream(
                                        "/pt_BR.aff"
                                );

                InputStream dicStream =
                        getClass()
                                .getResourceAsStream(
                                        "/pt_BR.dic"
                                )
        ) {

            if (
                    affStream == null
                            || dicStream == null
            ) {

                throw new IllegalStateException(
                        "Não foi possível localizar " +
                        "pt_BR.aff e pt_BR.dic."
                );
            }


            Dictionary dictionary =
                    new Dictionary(
                            new ByteBuffersDirectory(),
                            "hunspell",
                            affStream,
                            dicStream
                    );


            hunspellChecker =
                    new Hunspell(
                            dictionary
                    );
        }
    }


    /**
     * =========================================================
     * MÉTODO PRINCIPAL
     * =========================================================
     */
    public byte[] processarEModificarDocumento(
            InputStream templateStream,
            InputStream documentoStream
    ) throws Exception {

        carregarHunspell();

        TemplateProfile perfil;

        try (
                XWPFDocument docTemplate =
                        new XWPFDocument(templateStream)
        ) {
            perfil = templateService.analisar(docTemplate);
        }

        ByteArrayOutputStream baos =
                new ByteArrayOutputStream();

        try (
                XWPFDocument documento =
                        new XWPFDocument(documentoStream)
        ) {

            List<String> relatorio =
                    new ArrayList<>();

            validarLayout(
                    documento,
                    perfil,
                    relatorio
            );

            for (XWPFParagraph paragrafo :
                    documento.getParagraphs()) {

                revisarParagrafo(paragrafo);

                validarTag(
                        paragrafo,
                        perfil,
                        relatorio
                );

                validarConvencaoNumerada(
                        paragrafo,
                        perfil,
                        relatorio
                );

                validarFormatacaoCorpo(
                        paragrafo,
                        perfil,
                        relatorio
                );
            }

            for (XWPFTable tabela :
                    documento.getTables()) {

                processarTabela(
                        tabela,
                        perfil,
                        relatorio
                );
            }

            anexarRelatorio(
                    documento,
                    relatorio
            );

            documento.write(baos);
        }

        return baos.toByteArray();
    }


    /**
     * =========================================================
     * LAYOUT
     * =========================================================
     */
    private void validarLayout(
            XWPFDocument documento,
            TemplateProfile perfil,
            List<String> relatorio
    ) {

        LayoutProfile esperado =
                perfil.getLayout();


        if (esperado == null) {
            return;
        }


        CTSectPr sectPr =
                documento
                        .getDocument()
                        .getBody()
                        .getSectPr();


        if (sectPr == null) {
            return;
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


            if (
                    diferente(
                            largura,
                            esperado.getLarguraPaginaCm(),
                            0.15
                    )
                    ||
                    diferente(
                            altura,
                            esperado.getAlturaPaginaCm(),
                            0.15
                    )
            ) {

                relatorio.add(
                        "Tamanho da página divergente. " +
                        "Template: " +
                        formatarCm(
                                esperado.getLarguraPaginaCm()
                        ) +
                        " × " +
                        formatarCm(
                                esperado.getAlturaPaginaCm()
                        ) +
                        " cm. Documento: " +
                        formatarCm(largura) +
                        " × " +
                        formatarCm(altura) +
                        " cm."
                );
            }
        }


        CTPageMar margens =
                sectPr.getPgMar();


        if (margens == null) {
            return;
        }


        compararMargem(
                "superior",
                twipsParaCm(
                        margens.getTop()
                ),
                esperado.getMargemSuperiorCm(),
                relatorio
        );


        compararMargem(
                "inferior",
                twipsParaCm(
                        margens.getBottom()
                ),
                esperado.getMargemInferiorCm(),
                relatorio
        );


        compararMargem(
                "esquerda",
                twipsParaCm(
                        margens.getLeft()
                ),
                esperado.getMargemEsquerdaCm(),
                relatorio
        );


        compararMargem(
                "direita",
                twipsParaCm(
                        margens.getRight()
                ),
                esperado.getMargemDireitaCm(),
                relatorio
        );
    }


    private void compararMargem(
            String nome,
            double encontrada,
            double esperada,
            List<String> relatorio
    ) {

        if (
                diferente(
                        encontrada,
                        esperada,
                        0.15
                )
        ) {

            relatorio.add(
                    "Margem " +
                    nome +
                    " divergente. Template: " +
                    formatarCm(esperada) +
                    " cm. Documento: " +
                    formatarCm(encontrada) +
                    " cm."
            );
        }
    }


    /**
     * =========================================================
     * TAGS
     * =========================================================
     */
    private void validarTag(
            XWPFParagraph paragrafo,
            TemplateProfile perfil,
            List<String> relatorio
    ) {

        String texto =
                paragrafo
                        .getText()
                        .trim();


        if (
                !templateService
                        .pareceMarcador(texto)
        ) {

            return;
        }


        String normalizado =
                templateService
                        .normalizarTag(
                                texto
                        );


        TagProfile tagEsperada =
                perfil
                        .getTags()
                        .get(normalizado);


        /*
         * Marcador conhecido.
         */
        if (tagEsperada != null) {

            String preferida =
                    tagEsperada
                            .getRepresentacaoPreferida();


            if (
                    preferida != null
                            &&
                            !texto.equals(
                                    preferida
                            )
            ) {

                destacarParagrafo(
                        paragrafo,
                        "yellow"
                );


                relatorio.add(
                        "Estrutura \"" +
                        normalizado +
                        "\" encontrada como \"" +
                        texto +
                        "\", mas o padrão predominante " +
                        "do template é \"" +
                        preferida +
                        "\"."
                );
            }


            return;
        }


        /*
         * Se parece muito com uma tag,
         * mas não pertence ao perfil,
         * classificamos como estrutura não prevista.
         *
         * Não afirmamos que está errada.
         */
        if (
                texto.contains("#")
                        || texto.startsWith("[")
                        || texto.startsWith("<")
        ) {

            relatorio.add(
                    "Estrutura não prevista no template: \"" +
                    texto +
                    "\"."
            );
        }
    }


    /**
     * =========================================================
     * CONVENÇÕES NUMERADAS
     * =========================================================
     */
    private void validarConvencaoNumerada(
            XWPFParagraph paragrafo,
            TemplateProfile perfil,
            List<String> relatorio
    ) {

        String texto =
                paragrafo
                        .getText()
                        .trim();


        Matcher matcher =
                PADRAO_NUMERADO.matcher(
                        texto
                );


        if (!matcher.matches()) {
            return;
        }


        String rotulo =
                matcher
                        .group(1)
                        .trim();


        String normalizado =
                rotulo.toUpperCase(
                        PT_BR
                );


        String separadorEncontrado =
                matcher.group(3);


        ConvencaoNumeradaProfile esperado =
                perfil
                        .getConvencoesNumeradas()
                        .get(normalizado);


        if (esperado == null) {
            return;
        }


        if (
                esperado.getSeparador() != null
                        &&
                        !esperado
                                .getSeparador()
                                .equals(
                                        separadorEncontrado
                                )
        ) {

            destacarParagrafo(
                    paragrafo,
                    "yellow"
            );


            relatorio.add(
                    "Padronização divergente em \"" +
                    rotulo +
                    "\". O template usa \"" +
                    esperado.getRotuloOriginal() +
                    " N " +
                    esperado.getSeparador() +
                    "\", mas o documento usa \"" +
                    rotulo +
                    " N " +
                    separadorEncontrado +
                    "\"."
            );
        }
    }


    /**
     * =========================================================
     * FORMATAÇÃO DO CORPO
     * =========================================================
     */
    private void validarFormatacaoCorpo(
            XWPFParagraph paragrafo,
            TemplateProfile perfil,
            List<String> relatorio
    ) {

        String texto =
                paragrafo
                        .getText()
                        .trim();


        /*
         * Ainda usamos uma heurística conservadora.
         *
         * Não queremos marcar títulos ou legendas
         * usando a formatação do corpo.
         */
        if (!ehParagrafoDeCorpo(texto)) {
            return;
        }


        FormatacaoProfile esperado =
                perfil.getCorpoTexto();


        if (esperado == null) {
            return;
        }


        boolean divergente =
                false;


        if (
                esperado.getAlinhamento()
                        != null
                        &&
                        paragrafo.getAlignment()
                                != esperado.getAlinhamento()
        ) {

            divergente =
                    true;
        }


        if (
                esperado.getEspacamentoEntreLinhas()
                        != null
                        &&
                        paragrafo.getSpacingBetween() > 0
                        &&
                        diferente(
                                paragrafo.getSpacingBetween(),
                                esperado
                                        .getEspacamentoEntreLinhas(),
                                0.05
                        )
        ) {

            divergente =
                    true;
        }


        /*
         * Fonte e tamanho são avaliados run por run.
         */
        for (XWPFRun run :
                paragrafo.getRuns()) {

            if (
                    run.text() == null
                            || run.text().isBlank()
            ) {

                continue;
            }


            boolean runDivergente =
                    false;


            if (
                    esperado.getFonte() != null
                            &&
                            run.getFontFamily() != null
                            &&
                            !esperado
                                    .getFonte()
                                    .equalsIgnoreCase(
                                            run.getFontFamily()
                                    )
            ) {

                runDivergente =
                        true;
            }


            Double tamanho =
                    run.getFontSizeAsDouble();


            if (
                    esperado.getTamanhoFonte()
                            != null
                            &&
                            tamanho != null
                            &&
                            diferente(
                                    tamanho,
                                    esperado
                                            .getTamanhoFonte(),
                                    0.25
                            )
            ) {

                runDivergente =
                        true;
            }


            if (runDivergente) {

                run.setTextHighlightColor(
                        "yellow"
                );


                divergente =
                        true;
            }
        }


        /*
         * Alinhamento/espaçamento são propriedades
         * de parágrafo, então destacamos os runs
         * caso haja divergência global.
         */
        if (divergente) {

            destacarParagrafo(
                    paragrafo,
                    "yellow"
            );
        }
    }


    private boolean ehParagrafoDeCorpo(
            String texto
    ) {

        if (
                texto == null
                        || texto.isBlank()
                        || texto.length() < 80
        ) {

            return false;
        }


        boolean possuiMinuscula =
                texto.chars()
                        .anyMatch(
                                Character::isLowerCase
                        );


        return possuiMinuscula;
    }


    /**
     * =========================================================
     * ORTOGRAFIA + GRAMÁTICA
     * =========================================================
     */
    private void revisarParagrafo(
            XWPFParagraph paragrafo
    ) throws IOException {

        List<MapaRun> mapaRuns =
                mapearRuns(
                        paragrafo
                );


        if (mapaRuns.isEmpty()) {
            return;
        }


        StringBuilder builder =
                new StringBuilder();


        for (MapaRun mapa :
                mapaRuns) {

            builder.append(
                    mapa.texto
            );
        }


        String texto =
                builder.toString();


        if (texto.isBlank()) {
            return;
        }


        List<Marcacao> marcacoes =
                new ArrayList<>();


        localizarErrosOrtograficos(
                texto,
                marcacoes
        );


        localizarErrosGramaticais(
                texto,
                marcacoes
        );


        if (marcacoes.isEmpty()) {
            return;
        }


        for (
                int i =
                        mapaRuns.size() - 1;
                i >= 0;
                i--
        ) {

            MapaRun mapa =
                    mapaRuns.get(i);


            if (
                    mapa.hyperlink
                            || mapa.especial
            ) {

                continue;
            }


            List<MarcacaoLocal>
                    marcacoesRun =
                    localizarMarcacoesDoRun(
                            mapa,
                            marcacoes
                    );


            if (!marcacoesRun.isEmpty()) {

                substituirRun(
                        paragrafo,
                        mapa,
                        marcacoesRun
                );
            }
        }
    }


    private void localizarErrosOrtograficos(
            String texto,
            List<Marcacao> marcacoes
    ) {

        Matcher matcher =
                PADRAO_PALAVRA.matcher(
                        texto
                );


        while (matcher.find()) {

            String palavra =
                    matcher.group();


            if (palavra.length() <= 1) {
                continue;
            }


            if (!palavraCorreta(palavra)) {

                marcacoes.add(
                        new Marcacao(
                                matcher.start(),
                                matcher.end(),
                                TipoMarcacao.ORTOGRAFIA
                        )
                );
            }
        }
    }


    private boolean palavraCorreta(
            String palavra
    ) {

        if (
                palavra == null
                        || palavra.isBlank()
                        || hunspellChecker == null
        ) {

            return true;
        }


        String normalizada =
                palavra.toLowerCase(
                        PT_BR
                );


        if (
                hunspellChecker.spell(
                        palavra
                )
        ) {

            return true;
        }


        if (
                hunspellChecker.spell(
                        normalizada
                )
        ) {

            return true;
        }


        if (
                palavra.contains("-")
                        || palavra.contains("'")
                        || palavra.contains("’")
        ) {

            String[] partes =
                    palavra.split(
                            "[-'’]"
                    );


            for (String parte :
                    partes) {

                if (parte.isBlank()) {
                    continue;
                }


                if (
                        !hunspellChecker.spell(
                                parte
                        )
                                &&
                                !hunspellChecker.spell(
                                        parte.toLowerCase(
                                                PT_BR
                                        )
                                )
                ) {

                    return false;
                }
            }


            return true;
        }


        return false;
    }


    private void localizarErrosGramaticais(
            String texto,
            List<Marcacao> marcacoes
    ) throws IOException {

        List<RuleMatch> problemas =
                gramaticaService.revisar(
                        texto
                );


        for (RuleMatch problema :
                problemas) {

            int inicio =
                    problema.getFromPos();


            int fim =
                    problema.getToPos();


            if (
                    inicio < 0
                            || fim > texto.length()
                            || inicio >= fim
            ) {

                continue;
            }


            marcacoes.add(
                    new Marcacao(
                            inicio,
                            fim,
                            TipoMarcacao.GRAMATICA
                    )
            );
        }
    }


    /**
     * =========================================================
     * TABELAS
     * =========================================================
     */
    private void processarTabela(
            XWPFTable tabela,
            TemplateProfile perfil,
            List<String> relatorio
    ) throws IOException {

        for (XWPFTableRow linha :
                tabela.getRows()) {

            for (XWPFTableCell celula :
                    linha.getTableCells()) {

                for (XWPFParagraph paragrafo :
                        celula.getParagraphs()) {

                    revisarParagrafo(paragrafo);

                    validarTag(
                            paragrafo,
                            perfil,
                            relatorio
                    );

                    validarConvencaoNumerada(
                            paragrafo,
                            perfil,
                            relatorio
                    );

                    validarFormatacaoCorpo(
                            paragrafo,
                            perfil,
                            relatorio
                    );
                }

                for (XWPFTable interna :
                        celula.getTables()) {

                    processarTabela(
                            interna,
                            perfil,
                            relatorio
                    );
                }
            }
        }
    }


    /**
     * =========================================================
     * RELATÓRIO
     * =========================================================
     */
    private void anexarRelatorio(
            XWPFDocument documento,
            List<String> problemas
    ) {

        /*
         * Evita duplicatas iguais.
         */
        Set<String> unicos =
                new LinkedHashSet<>(
                        problemas
                );


        if (unicos.isEmpty()) {
            return;
        }


        XWPFParagraph quebra =
                documento.createParagraph();


        quebra.setPageBreak(
                true
        );


        XWPFParagraph titulo =
                documento.createParagraph();


        titulo.setAlignment(
                ParagraphAlignment.CENTER
        );


        XWPFRun tituloRun =
                titulo.createRun();


        tituloRun.setBold(
                true
        );


        tituloRun.setText(
                "RELATÓRIO REVORA"
        );


        for (String problema :
                unicos) {

            XWPFParagraph p =
                    documento.createParagraph();


            XWPFRun run =
                    p.createRun();


            run.setText(
                    "• " +
                    problema
            );


            run.setTextHighlightColor(
                    "darkYellow"
            );
        }
    }


    /**
     * =========================================================
     * RUNS
     * =========================================================
     */
    private List<MapaRun> mapearRuns(
            XWPFParagraph paragrafo
    ) {

        List<MapaRun> resultado =
                new ArrayList<>();


        int posicao =
                0;


        List<XWPFRun> runs =
                paragrafo.getRuns();


        for (
                int i = 0;
                i < runs.size();
                i++
        ) {

            XWPFRun run =
                    runs.get(i);


            String texto =
                    run.text();


            if (texto == null) {
                texto = "";
            }


            int inicio =
                    posicao;


            int fim =
                    inicio +
                    texto.length();


            resultado.add(
                    new MapaRun(
                            run,
                            i,
                            texto,
                            inicio,
                            fim,
                            run instanceof
                                    XWPFHyperlinkRun,
                            texto.contains("\t")
                                    ||
                                    texto.contains("\n")
                                    ||
                                    texto.contains("\r")
                    )
            );


            posicao =
                    fim;
        }


        return resultado;
    }


    private List<MarcacaoLocal>
            localizarMarcacoesDoRun(
                    MapaRun mapa,
                    List<Marcacao> marcacoes
    ) {

        List<MarcacaoLocal> resultado =
                new ArrayList<>();


        for (Marcacao marcacao :
                marcacoes) {

            int inicio =
                    Math.max(
                            mapa.inicioGlobal,
                            marcacao.inicio
                    );


            int fim =
                    Math.min(
                            mapa.fimGlobal,
                            marcacao.fim
                    );


            if (inicio < fim) {

                resultado.add(
                        new MarcacaoLocal(
                                inicio -
                                mapa.inicioGlobal,

                                fim -
                                mapa.inicioGlobal,

                                marcacao.tipo
                        )
                );
            }
        }


        return resultado;
    }


    private void substituirRun(
            XWPFParagraph paragrafo,
            MapaRun mapa,
            List<MarcacaoLocal> marcacoes
    ) {

        String texto =
                mapa.texto;


        if (texto.isEmpty()) {
            return;
        }


        CTRPr propriedades =
                null;


        if (
                mapa.run
                        .getCTR()
                        .isSetRPr()
        ) {

            propriedades =
                    (CTRPr)
                            mapa.run
                                    .getCTR()
                                    .getRPr()
                                    .copy();
        }


        Set<Integer> limites =
                new LinkedHashSet<>();


        limites.add(0);
        limites.add(
                texto.length()
        );


        for (MarcacaoLocal m :
                marcacoes) {

            limites.add(
                    m.inicio
            );

            limites.add(
                    m.fim
            );
        }


        List<Integer> lista =
                new ArrayList<>(
                        limites
                );


        lista.sort(
                Integer::compareTo
        );


        paragrafo.removeRun(
                mapa.indiceRun
        );


        int indice =
                mapa.indiceRun;


        for (
                int i = 0;
                i < lista.size() - 1;
                i++
        ) {

            int inicio =
                    lista.get(i);


            int fim =
                    lista.get(i + 1);


            if (inicio >= fim) {
                continue;
            }


            String trecho =
                    texto.substring(
                            inicio,
                            fim
                    );


            TipoMarcacao tipo =
                    escolherTipo(
                            inicio,
                            fim,
                            marcacoes
                    );


            criarRun(
                    paragrafo,
                    indice++,
                    trecho,
                    propriedades,
                    tipo
            );
        }
    }


    private TipoMarcacao escolherTipo(
            int inicio,
            int fim,
            List<MarcacaoLocal> marcacoes
    ) {

        TipoMarcacao resultado =
                TipoMarcacao.NENHUMA;


        for (MarcacaoLocal m :
                marcacoes) {

            if (
                    inicio < m.inicio
                            || fim > m.fim
            ) {

                continue;
            }


            /*
             * Ortografia tem prioridade visual.
             */
            if (
                    m.tipo ==
                            TipoMarcacao.ORTOGRAFIA
            ) {

                return TipoMarcacao.ORTOGRAFIA;
            }


            if (
                    m.tipo ==
                            TipoMarcacao.GRAMATICA
            ) {

                resultado =
                        TipoMarcacao.GRAMATICA;
            }
        }


        return resultado;
    }


    private void criarRun(
            XWPFParagraph paragrafo,
            int indice,
            String texto,
            CTRPr propriedades,
            TipoMarcacao tipo
    ) {

        XWPFRun run =
                paragrafo.insertNewRun(
                        indice
                );


        if (propriedades != null) {

            run
                    .getCTR()
                    .setRPr(
                            (CTRPr)
                                    propriedades.copy()
                    );
        }


        run.setText(
                texto
        );


        if (
                tipo ==
                        TipoMarcacao.ORTOGRAFIA
        ) {

            run.setTextHighlightColor(
                    "red"
            );

        } else if (
                tipo ==
                        TipoMarcacao.GRAMATICA
        ) {

            run.setTextHighlightColor(
                    "cyan"
            );
        }
    }


    private void destacarParagrafo(
            XWPFParagraph paragrafo,
            String cor
    ) {

        for (XWPFRun run :
                paragrafo.getRuns()) {

            run.setTextHighlightColor(
                    cor
            );
        }
    }


    /**
     * =========================================================
     * UTILITÁRIOS
     * =========================================================
     */
    private boolean diferente(
            double a,
            double b,
            double tolerancia
    ) {

        return Math.abs(
                a - b
        ) > tolerancia;
    }


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

            } catch (
                    NumberFormatException e
            ) {

                return 0;
            }
        }


        return Math.round(
                (
                        twips /
                        1440.0 *
                        2.54
                ) * 100.0
        ) / 100.0;
    }


    private String formatarCm(
            double valor
    ) {

        return String.format(
                PT_BR,
                "%.2f",
                valor
        );
    }


    /**
     * =========================================================
     * TIPOS INTERNOS
     * =========================================================
     */
    private enum TipoMarcacao {

        NENHUMA,
        ORTOGRAFIA,
        GRAMATICA
    }


    private static class Marcacao {

        private final int inicio;
        private final int fim;
        private final TipoMarcacao tipo;


        private Marcacao(
                int inicio,
                int fim,
                TipoMarcacao tipo
        ) {

            this.inicio =
                    inicio;

            this.fim =
                    fim;

            this.tipo =
                    tipo;
        }
    }


    private static class MarcacaoLocal {

        private final int inicio;
        private final int fim;
        private final TipoMarcacao tipo;


        private MarcacaoLocal(
                int inicio,
                int fim,
                TipoMarcacao tipo
        ) {

            this.inicio =
                    inicio;

            this.fim =
                    fim;

            this.tipo =
                    tipo;
        }
    }


    private static class MapaRun {

        private final XWPFRun run;

        private final int indiceRun;

        private final String texto;

        private final int inicioGlobal;

        private final int fimGlobal;

        private final boolean hyperlink;

        private final boolean especial;


        private MapaRun(
                XWPFRun run,
                int indiceRun,
                String texto,
                int inicioGlobal,
                int fimGlobal,
                boolean hyperlink,
                boolean especial
        ) {

            this.run =
                    run;

            this.indiceRun =
                    indiceRun;

            this.texto =
                    texto;

            this.inicioGlobal =
                    inicioGlobal;

            this.fimGlobal =
                    fimGlobal;

            this.hyperlink =
                    hyperlink;

            this.especial =
                    especial;
        }
    }
}
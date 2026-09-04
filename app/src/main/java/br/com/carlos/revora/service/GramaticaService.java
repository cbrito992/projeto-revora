package br.com.carlos.revora.service;

import org.languagetool.JLanguageTool;
import org.languagetool.Languages;
import org.languagetool.rules.RuleMatch;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class GramaticaService {

    /*
     * Categorias que o Revora considera relevantes.
     *
     * O objetivo aqui é reduzir falsos positivos e
     * evitar sugestões de estilo, repetição etc.
     */
    private static final Set<String> CATEGORIAS_ACEITAS = Set.of(
            "GRAMMAR",
            "CONFUSED_WORDS",
            "COMPOUNDING",
            "CASING",
            "PUNCTUATION",
            "SEMANTICS"
    );


    /*
     * Lista opcional para regras específicas que comprovadamente
     * gerem falsos positivos no Revora.
     *
     * Por enquanto deixamos vazia.
     *
     * Depois, se o console mostrar uma regra problemática,
     * basta colocar o ID aqui.
     */
    private static final Set<String> REGRAS_IGNORADAS = Set.of(
            // Exemplo:
            // "ID_DA_REGRA"
    );


    /*
     * Ative durante o desenvolvimento para descobrir
     * por que determinada marcação apareceu.
     */
    private static final boolean MODO_DIAGNOSTICO = true;


    /*
     * JLanguageTool não é thread-safe.
     *
     * Cada thread recebe sua própria instância.
     */
    private final ThreadLocal<JLanguageTool> languageTool =
            ThreadLocal.withInitial(this::criarLanguageTool);


    /**
     * Configura o LanguageTool para português brasileiro.
     */
    private JLanguageTool criarLanguageTool() {

        JLanguageTool tool = new JLanguageTool(
                Languages.getLanguageForShortCode("pt-BR")
        );


        /*
         * O Revora já utiliza Vero/Hunspell
         * para ortografia.
         *
         * Evitamos duplicidade.
         */
        tool.disableRule(
                "MORFOLOGIK_RULE_PT_BR"
        );


        return tool;
    }


    /**
     * Analisa um texto e retorna apenas os problemas
     * considerados relevantes para o Revora.
     */
    public List<RuleMatch> revisar(String texto)
            throws IOException {

        if (texto == null || texto.isBlank()) {
            return Collections.emptyList();
        }


        List<RuleMatch> resultados =
                languageTool
                        .get()
                        .check(texto);


        return resultados
                .stream()
                .filter(match ->
                        aceitarMatch(
                                match,
                                texto
                        )
                )
                .toList();
    }


    /**
     * Decide se uma ocorrência do LanguageTool
     * deve realmente ser mostrada pelo Revora.
     */
    private boolean aceitarMatch(
            RuleMatch match,
            String texto
    ) {

        String idRegra =
                match
                        .getRule()
                        .getId();


        String categoria =
                match
                        .getRule()
                        .getCategory()
                        .getId()
                        .toString();


        /*
         * 1. Regras manualmente bloqueadas.
         */
        if (
                REGRAS_IGNORADAS.contains(
                        idRegra
                )
        ) {

            logIgnorado(
                    match,
                    texto,
                    categoria,
                    "regra bloqueada"
            );

            return false;
        }


        /*
         * 2. Só aceitamos categorias
         * úteis para revisão linguística.
         */
        if (
                !CATEGORIAS_ACEITAS.contains(
                        categoria
                )
        ) {

            logIgnorado(
                    match,
                    texto,
                    categoria,
                    "categoria não utilizada"
            );

            return false;
        }


        /*
         * 3. Proteção básica contra posições inválidas.
         */
        int inicio =
                match.getFromPos();

        int fim =
                match.getToPos();


        if (
                inicio < 0
                        || fim > texto.length()
                        || inicio >= fim
        ) {

            logIgnorado(
                    match,
                    texto,
                    categoria,
                    "posição inválida"
            );

            return false;
        }


        logAceito(
                match,
                texto,
                categoria
        );


        return true;
    }


    /**
     * Mostra no console uma ocorrência aceita.
     */
    private void logAceito(
            RuleMatch match,
            String texto,
            String categoria
    ) {

        if (!MODO_DIAGNOSTICO) {
            return;
        }


        String trecho =
                extrairTrecho(
                        match,
                        texto
                );


        System.out.println();
        System.out.println(
                "===== REVORA / LANGUAGETOOL ====="
        );

        System.out.println(
                "STATUS: ACEITO"
        );

        System.out.println(
                "Trecho: [" +
                        trecho +
                        "]"
        );

        System.out.println(
                "Regra: " +
                        match
                                .getRule()
                                .getId()
        );

        System.out.println(
                "Categoria: " +
                        categoria
        );

        System.out.println(
                "Mensagem: " +
                        match.getMessage()
        );

        System.out.println(
                "Sugestões: " +
                        match
                                .getSuggestedReplacements()
        );

        System.out.println(
                "================================="
        );
    }


    /**
     * Mostra no console ocorrências descartadas.
     *
     * Isso será muito útil para descobrir
     * o caso dos "A" soltos do seu print.
     */
    private void logIgnorado(
            RuleMatch match,
            String texto,
            String categoria,
            String motivo
    ) {

        if (!MODO_DIAGNOSTICO) {
            return;
        }


        String trecho =
                extrairTrecho(
                        match,
                        texto
                );


        System.out.println();
        System.out.println(
                "===== REVORA / LANGUAGETOOL ====="
        );

        System.out.println(
                "STATUS: IGNORADO"
        );

        System.out.println(
                "Motivo: " +
                        motivo
        );

        System.out.println(
                "Trecho: [" +
                        trecho +
                        "]"
        );

        System.out.println(
                "Regra: " +
                        match
                                .getRule()
                                .getId()
        );

        System.out.println(
                "Categoria: " +
                        categoria
        );

        System.out.println(
                "Mensagem: " +
                        match.getMessage()
        );

        System.out.println(
                "================================="
        );
    }


    /**
     * Recupera com segurança o trecho
     * correspondente ao RuleMatch.
     */
    private String extrairTrecho(
            RuleMatch match,
            String texto
    ) {

        int inicio =
                match.getFromPos();

        int fim =
                match.getToPos();


        if (
                inicio < 0
                        || fim > texto.length()
                        || inicio >= fim
        ) {

            return "";
        }


        return texto.substring(
                inicio,
                fim
        );
    }

}


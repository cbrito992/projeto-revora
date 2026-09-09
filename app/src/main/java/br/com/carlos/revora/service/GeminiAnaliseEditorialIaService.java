package br.com.carlos.revora.service;

import br.com.carlos.revora.model.OcorrenciaEditorial;
import br.com.carlos.revora.model.ResultadoAnaliseEditorial;
import br.com.carlos.revora.model.TrechoEditorial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;

import java.time.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
@ConditionalOnProperty(
        name = "revora.ia.provider",
        havingValue = "gemini",
        matchIfMissing = true
)
public class GeminiAnaliseEditorialIaService
        implements AnaliseEditorialIaService {

    private static final String GEMINI_URL_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";


    /*
     * Para a demonstração evitamos mandar um documento
     * gigantesco em uma única requisição.
     */
    private static final int MAX_PARAGRAFOS_POR_LOTE =
            20;


    private static final int MAX_CARACTERES_POR_LOTE =
            20_000;


    /*
     * Criamos o ObjectMapper diretamente.
     *
     * Isso evita o problema anterior em que o Spring
     * tentou injetar um ObjectMapper e não encontrou
     * um bean compatível.
     */
    private final ObjectMapper objectMapper =
            new ObjectMapper();


    private final GuiaEditorialService guiaEditorialService;


    private final HttpClient httpClient;


    @Value("${revora.ia.enabled:false}")
    private boolean enabled;


    @Value("${revora.ia.gemini.api-key:}")
    private String apiKey;


    @Value("${revora.ia.gemini.model:gemini-2.5-flash}")
    private String model;


    @Value("${revora.ia.gemini.timeout-seconds:90}")
    private int timeoutSeconds;


    public GeminiAnaliseEditorialIaService(
            GuiaEditorialService guiaEditorialService
    ) {

        this.guiaEditorialService =
                guiaEditorialService;


        this.httpClient =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(20)
                        )
                        .build();
    }


    /**
     * =========================================================
     * MÉTODO PRINCIPAL
     * =========================================================
     */
    @Override
    public ResultadoAnaliseEditorial analisar(
            List<TrechoEditorial> trechos
    ) throws Exception {

        if (!enabled) {

            return new ResultadoAnaliseEditorial(
                    List.of()
            );
        }


        if (
                trechos == null
                        ||
                trechos.isEmpty()
        ) {

            return new ResultadoAnaliseEditorial(
                    List.of()
            );
        }


        if (
                apiKey == null
                        ||
                apiKey.isBlank()
        ) {

            throw new IllegalStateException(
                    "A análise por IA está habilitada, " +
                    "mas GEMINI_API_KEY não foi configurada."
            );
        }


        /*
         * Divide documentos maiores em lotes.
         */
        List<List<TrechoEditorial>> lotes =
                criarLotes(
                        trechos
                );


        List<OcorrenciaEditorial> todasOcorrencias =
                new ArrayList<>();


        for (
                List<TrechoEditorial> lote :
                lotes
        ) {

            ResultadoAnaliseEditorial resultado =
                    analisarLote(
                            lote
                    );


            if (
                    resultado != null
                            &&
                    resultado.ocorrencias() != null
            ) {

                todasOcorrencias.addAll(
                        resultado.ocorrencias()
                );
            }
        }


        return new ResultadoAnaliseEditorial(
                todasOcorrencias
        );
    }


    /**
     * =========================================================
     * ANÁLISE DE UM LOTE
     * =========================================================
     */
    private ResultadoAnaliseEditorial analisarLote(
            List<TrechoEditorial> trechos
    ) throws Exception {

        String requisicaoJson =
                criarRequisicao(
                        trechos
                );


        String url =
                GEMINI_URL_BASE
                        +
                model
                        +
                ":generateContent";


        HttpRequest request =
                HttpRequest
                        .newBuilder()
                        .uri(
                                URI.create(
                                        url
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(
                                        timeoutSeconds
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "x-goog-api-key",
                                apiKey
                        )
                        .POST(
                                HttpRequest
                                        .BodyPublishers
                                        .ofString(
                                                requisicaoJson,
                                                StandardCharsets.UTF_8
                                        )
                        )
                        .build();


        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse
                                .BodyHandlers
                                .ofString(
                                        StandardCharsets.UTF_8
                                )
                );


        if (
                response.statusCode() < 200
                        ||
                response.statusCode() >= 300
        ) {

            throw criarErroApi(
                    response.statusCode(),
                    response.body()
            );
        }


        ResultadoAnaliseEditorial resultado =
                interpretarResposta(
                        response.body()
                );


        /*
         * A IA não é considerada fonte absoluta.
         *
         * Antes de devolver as ocorrências ao
         * DocumentoService, validamos:
         *
         * - se o ID realmente existe;
         * - se a evidência existe literalmente;
         * - se a confiança é válida.
         */
        return validarResultado(
                trechos,
                resultado
        );
    }


    /**
     * =========================================================
     * REQUISIÇÃO GEMINI
     * =========================================================
     */
    private String criarRequisicao(
            List<TrechoEditorial> trechos
    ) throws Exception {

        Map<String, Object> requisicao =
                new LinkedHashMap<>();


        requisicao.put(
                "systemInstruction",
                Map.of(
                        "parts",
                        List.of(
                                Map.of(
                                        "text",
                                        criarInstrucoes()
                                )
                        )
                )
        );


        requisicao.put(
                "contents",
                List.of(
                        Map.of(
                                "role",
                                "user",

                                "parts",
                                List.of(
                                        Map.of(
                                                "text",
                                                criarEntrada(
                                                        trechos
                                                )
                                        )
                                )
                        )
                )
        );


        Map<String, Object> generationConfig =
                new LinkedHashMap<>();


        /*
         * Obriga o modelo a responder JSON.
         */
        generationConfig.put(
                "responseMimeType",
                "application/json"
        );


        /*
         * Schema que precisa corresponder
         * aos records Java do Revora.
         */
        generationConfig.put(
                "responseJsonSchema",
                criarSchema()
        );


        /*
         * Menos criatividade é melhor para
         * revisão editorial.
         */
        generationConfig.put(
                "temperature",
                0.2
        );


        generationConfig.put(
                "maxOutputTokens",
                8192
        );


        requisicao.put(
                "generationConfig",
                generationConfig
        );


        return objectMapper
                .writeValueAsString(
                        requisicao
                );
    }


    /**
     * =========================================================
     * PROMPT
     * =========================================================
     */
    private String criarInstrucoes() {

        return """
                Você é o mecanismo de análise editorial do sistema Revora.

                Sua função é identificar problemas editoriais em textos
                acadêmicos e educacionais.

                Você NÃO é um detector de texto produzido por IA.

                Nunca afirme que um texto foi escrito por inteligência
                artificial.

                A categoria HUMANIZACAO deve ser utilizada somente quando
                houver características textuais que mereçam revisão editorial,
                como:

                - escrita mecanizada;
                - repetição sem avanço argumentativo;
                - pensamento circular;
                - transições artificiais;
                - construções excessivamente genéricas;
                - clichês;
                - enumerações artificiais;
                - excesso de estruturas repetitivas;
                - baixa naturalidade textual;
                - referências vagas;
                - adjetivação excessiva;
                - períodos desnecessariamente artificiais.

                A categoria ABNT deve ser utilizada para possíveis problemas
                relacionados à apresentação acadêmica de citações e referências.

                REGRAS OBRIGATÓRIAS:

                1. Seja conservador.

                2. Não invente erros.

                3. Não reescreva o documento inteiro.

                4. Não invente autores, datas, páginas, obras ou referências.

                5. Cada ocorrência deve utilizar exatamente um paragrafoId
                   recebido na entrada.

                6. O campo evidencia deve ser uma cópia LITERAL de um trecho
                   existente dentro do parágrafo indicado.

                7. Nunca corrija ou altere o texto no campo evidencia.

                8. Se não houver evidência suficiente de problema, não
                   gere ocorrência.

                9. A confiança deve variar entre 0 e 1.

                10. HUMANIZACAO significa necessidade de revisão editorial,
                    e não autoria por inteligência artificial.


                DIRETRIZES DE HUMANIZAÇÃO DO REVORA:

                """
                +
                guiaEditorialService.getHumanizacao()
                +
                """



                DIRETRIZES DE ABNT DO REVORA:

                """
                +
                guiaEditorialService.getAbnt();
    }


    private String criarEntrada(
            List<TrechoEditorial> trechos
    ) {

        StringBuilder builder =
                new StringBuilder();


        builder.append(
                """
                Analise os parágrafos abaixo.

                Retorne somente ocorrências que realmente mereçam revisão.

                """
        );


        for (
                TrechoEditorial trecho :
                trechos
        ) {

            if (
                    trecho == null
                            ||
                    trecho.paragrafoId() == null
                            ||
                    trecho.texto() == null
                            ||
                    trecho.texto().isBlank()
            ) {

                continue;
            }


            builder
                    .append("[")
                    .append(
                            trecho.paragrafoId()
                    )
                    .append("]")
                    .append("\n")
                    .append(
                            trecho.texto()
                    )
                    .append("\n\n");
        }


        return builder.toString();
    }


    /**
     * =========================================================
     * JSON SCHEMA
     * =========================================================
     */
    private Map<String, Object> criarSchema() {

        Map<String, Object> propertiesOcorrencia =
                new LinkedHashMap<>();


        propertiesOcorrencia.put(
                "paragrafoId",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "ID exato do parágrafo recebido, por exemplo P0001."
                )
        );


        propertiesOcorrencia.put(
                "categoria",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of(
                                "HUMANIZACAO",
                                "ABNT"
                        )
                )
        );


        propertiesOcorrencia.put(
                "subtipo",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "Tipo resumido do problema editorial."
                )
        );


        propertiesOcorrencia.put(
                "evidencia",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "Trecho literal existente no parágrafo."
                )
        );


        propertiesOcorrencia.put(
                "motivo",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "Explicação objetiva do motivo da marcação."
                )
        );


        propertiesOcorrencia.put(
                "sugestao",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "Orientação breve para melhorar o trecho."
                )
        );


        propertiesOcorrencia.put(
                "confianca",
                Map.of(
                        "type",
                        "number",
                        "minimum",
                        0,
                        "maximum",
                        1
                )
        );


        Map<String, Object> ocorrencia =
                new LinkedHashMap<>();


        ocorrencia.put(
                "type",
                "object"
        );


        ocorrencia.put(
                "properties",
                propertiesOcorrencia
        );


        ocorrencia.put(
                "required",
                List.of(
                        "paragrafoId",
                        "categoria",
                        "subtipo",
                        "evidencia",
                        "motivo",
                        "sugestao",
                        "confianca"
                )
        );


        ocorrencia.put(
                "additionalProperties",
                false
        );


        Map<String, Object> schema =
                new LinkedHashMap<>();


        schema.put(
                "type",
                "object"
        );


        schema.put(
                "properties",
                Map.of(
                        "ocorrencias",
                        Map.of(
                                "type",
                                "array",
                                "items",
                                ocorrencia
                        )
                )
        );


        schema.put(
                "required",
                List.of(
                        "ocorrencias"
                )
        );


        schema.put(
                "additionalProperties",
                false
        );


        return schema;
    }


    /**
     * =========================================================
     * RESPOSTA GEMINI
     * =========================================================
     */
    private ResultadoAnaliseEditorial interpretarResposta(
            String corpo
    ) throws Exception {

        JsonNode root =
                objectMapper.readTree(
                        corpo
                );


        JsonNode candidates =
                root.path(
                        "candidates"
                );


        if (
                !candidates.isArray()
                        ||
                candidates.isEmpty()
        ) {

            String motivo =
                    root
                            .path(
                                    "promptFeedback"
                            )
                            .path(
                                    "blockReason"
                            )
                            .asText();


            if (motivo.isBlank()) {

                motivo =
                        "nenhum candidato retornado";
            }


            throw new IOException(
                    "O Gemini não retornou uma análise. Motivo: "
                            +
                    motivo
            );
        }


        JsonNode parts =
                candidates
                        .get(0)
                        .path(
                                "content"
                        )
                        .path(
                                "parts"
                        );


        if (
                !parts.isArray()
                        ||
                parts.isEmpty()
        ) {

            throw new IOException(
                    "O Gemini respondeu sem conteúdo textual."
            );
        }


        StringBuilder textoResposta =
                new StringBuilder();


        for (
                JsonNode part :
                parts
        ) {

            String texto =
                    part
                            .path(
                                    "text"
                            )
                            .asText();


            if (!texto.isBlank()) {

                textoResposta.append(
                        texto
                );
            }
        }


        if (textoResposta.isEmpty()) {

            throw new IOException(
                    "O Gemini não retornou o JSON da análise editorial."
            );
        }


        return objectMapper.readValue(
                textoResposta.toString(),
                ResultadoAnaliseEditorial.class
        );
    }


    /**
     * =========================================================
     * VALIDAÇÃO DAS OCORRÊNCIAS
     * =========================================================
     */
    private ResultadoAnaliseEditorial validarResultado(
            List<TrechoEditorial> trechos,
            ResultadoAnaliseEditorial resultado
    ) {

        if (
                resultado == null
                        ||
                resultado.ocorrencias() == null
        ) {

            return new ResultadoAnaliseEditorial(
                    List.of()
            );
        }


        Map<String, String> textosPorId =
                new LinkedHashMap<>();


        for (
                TrechoEditorial trecho :
                trechos
        ) {

            if (
                    trecho != null
                            &&
                    trecho.paragrafoId() != null
                            &&
                    trecho.texto() != null
            ) {

                textosPorId.put(
                        trecho.paragrafoId(),
                        trecho.texto()
                );
            }
        }


        List<OcorrenciaEditorial> validas =
                new ArrayList<>();


        Set<String> duplicadas =
                new HashSet<>();


        for (
                OcorrenciaEditorial ocorrencia :
                resultado.ocorrencias()
        ) {

            if (ocorrencia == null) {
                continue;
            }


            String textoOriginal =
                    textosPorId.get(
                            ocorrencia.paragrafoId()
                    );


            if (textoOriginal == null) {
                continue;
            }


            if (
                    ocorrencia.evidencia() == null
                            ||
                    ocorrencia.evidencia().isBlank()
                            ||
                    !textoOriginal.contains(
                            ocorrencia.evidencia()
                    )
            ) {

                continue;
            }


            if (
                    ocorrencia.categoria() == null
            ) {

                continue;
            }


            if (
                    Double.isNaN(
                            ocorrencia.confianca()
                    )
                            ||
                    ocorrencia.confianca() < 0
                            ||
                    ocorrencia.confianca() > 1
            ) {

                continue;
            }


            /*
             * Evita a mesma ocorrência repetida.
             */
            String chave =
                    ocorrencia.paragrafoId()
                            +
                    "|"
                            +
                    ocorrencia.categoria()
                            +
                    "|"
                            +
                    ocorrencia.evidencia();


            if (
                    !duplicadas.add(
                            chave
                    )
            ) {

                continue;
            }


            validas.add(
                    ocorrencia
            );
        }


        return new ResultadoAnaliseEditorial(
                validas
        );
    }


    /**
     * =========================================================
     * DIVISÃO EM LOTES
     * =========================================================
     */
    private List<List<TrechoEditorial>> criarLotes(
            List<TrechoEditorial> trechos
    ) {

        List<List<TrechoEditorial>> lotes =
                new ArrayList<>();


        List<TrechoEditorial> atual =
                new ArrayList<>();


        int caracteres =
                0;


        for (
                TrechoEditorial trecho :
                trechos
        ) {

            if (
                    trecho == null
                            ||
                    trecho.texto() == null
                            ||
                    trecho.texto().isBlank()
            ) {

                continue;
            }


            int tamanho =
                    trecho.texto().length();


            boolean limiteParagrafos =
                    atual.size()
                            >=
                    MAX_PARAGRAFOS_POR_LOTE;


            boolean limiteCaracteres =
                    !atual.isEmpty()
                            &&
                    caracteres + tamanho
                            >
                    MAX_CARACTERES_POR_LOTE;


            if (
                    limiteParagrafos
                            ||
                    limiteCaracteres
            ) {

                lotes.add(
                        List.copyOf(
                                atual
                        )
                );


                atual.clear();


                caracteres =
                        0;
            }


            atual.add(
                    trecho
            );


            caracteres +=
                    tamanho;
        }


        if (!atual.isEmpty()) {

            lotes.add(
                    List.copyOf(
                            atual
                    )
            );
        }


        return lotes;
    }


    /**
     * =========================================================
     * ERROS DA API
     * =========================================================
     */
    private IOException criarErroApi(
            int status,
            String corpo
    ) {

        String mensagem =
                "";


        try {

            JsonNode root =
                    objectMapper.readTree(
                            corpo
                    );


            mensagem =
                    root
                            .path(
                                    "error"
                            )
                            .path(
                                    "message"
                            )
                            .asText();

        } catch (Exception ignored) {

            /*
             * Não fazemos nada.
             */
        }


        if (
                mensagem == null
                        ||
                mensagem.isBlank()
        ) {

            mensagem =
                    "erro não detalhado pela API";
        }


        return new IOException(
                "Erro na Gemini API. HTTP "
                        +
                status
                        +
                ": "
                        +
                mensagem
        );
    }
}
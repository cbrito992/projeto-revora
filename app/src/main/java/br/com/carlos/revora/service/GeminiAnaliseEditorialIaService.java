package br.com.carlos.revora.service;

import br.com.carlos.revora.model.OcorrenciaEditorial;
import br.com.carlos.revora.model.ResultadoAnaliseEditorial;
import br.com.carlos.revora.model.TrechoEditorial;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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


    private static final HttpClient DEFAULT_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    private final HttpClient httpClient;


    @Value("${revora.ia.enabled:false}")
    private boolean enabled;


    @Value("${revora.ia.gemini.api-key:}")
    private String apiKey;


    @Value("${revora.ia.gemini.model:gemini-3.6-flash}")
    private String model;


    @Value("${revora.ia.gemini.timeout-seconds:90}")
    private int timeoutSeconds;


    @org.springframework.beans.factory.annotation.Autowired
    public GeminiAnaliseEditorialIaService(
            GuiaEditorialService guiaEditorialService
    ) {

        this.guiaEditorialService =
                guiaEditorialService;


        this.httpClient = DEFAULT_CLIENT;
    }


    private String provider = "gemini";

    public GeminiAnaliseEditorialIaService(GuiaEditorialService guia, String provider,
            String model, String apiKey) {
        this(guia, provider, model, apiKey, DEFAULT_CLIENT);
    }

    GeminiAnaliseEditorialIaService(GuiaEditorialService guia, String provider,
            String model, String apiKey, HttpClient client) {
        this.guiaEditorialService = guia;
        this.httpClient = client;
        this.provider = provider;
        this.model = model;
        this.apiKey = apiKey;
        this.enabled = true;
        this.timeoutSeconds = 90;
    }

    public void testarConexao() throws Exception {
        completar("Responda apenas OK.", "Teste de conexão.", false);
    }

    private String completar(String instrucoes, String entrada, boolean json) throws Exception {
        boolean gemini = provider.equals("gemini");
        String url = switch (provider) {
            case "gemini" -> GEMINI_URL_BASE + model + ":generateContent";
            case "groq" -> "https://api.groq.com/openai/v1/chat/completions";
            case "openrouter" -> "https://openrouter.ai/api/v1/chat/completions";
            default -> throw new IllegalArgumentException("Provedor inválido.");
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        if (gemini) {
            payload.put("systemInstruction", Map.of("parts", List.of(Map.of("text", instrucoes))));
            payload.put("contents", List.of(Map.of("parts", List.of(Map.of("text", entrada)))));
            Map<String, Object> generation = new LinkedHashMap<>();
            generation.put("temperature", 0.2);
            if (json) {
                generation.put("responseMimeType", "application/json");
                generation.put("responseJsonSchema", criarSchema());
            }
            payload.put("generationConfig", generation);
        } else {
            payload.put("model", model);
            payload.put("messages", List.of(Map.of("role", "system", "content", instrucoes),
                    Map.of("role", "user", "content", entrada)));
            payload.put("temperature", 0.2);
            if (json) payload.put("response_format", Map.of("type", "json_object"));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header(gemini ? "x-goog-api-key" : "Authorization", gemini ? apiKey : "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Provedor de IA retornou HTTP " + response.statusCode()
                    + ". Verifique chave, modelo, saldo e permissões.");
        JsonNode root = objectMapper.readTree(response.body());
        String text;
        if (gemini) {
            StringBuilder parts = new StringBuilder();
            for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) parts.append(part.path("text").asText(""));
            }
            text = parts.toString();
        } else text = root.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) throw new IOException("O provedor respondeu sem conteúdo textual.");
        return text;
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

        String instrucoes = criarInstrucoes() + "\nRetorne somente JSON com o schema: "
                + objectMapper.writeValueAsString(criarSchema());
        String texto = completar(instrucoes, criarEntrada(trechos), true);
        ResultadoAnaliseEditorial resultado = objectMapper.readValue(texto, ResultadoAnaliseEditorial.class);
        return validarResultado(trechos, resultado);
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



}
package br.com.carlos.revora.service;

import br.com.carlos.revora.dto.ConfiguracaoIa;
import br.com.carlos.revora.model.TrechoEditorial;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PersonalIaTests {
    private static final String RESULT = "{\"ocorrencias\":[{\"paragrafoId\":\"P0001\",\"categoria\":\"ABNT\",\"subtipo\":\"citacao\",\"evidencia\":\"Texto\",\"motivo\":\"Verificar fonte\",\"sugestao\":\"Conferir\",\"confianca\":0.9},{\"paragrafoId\":\"inventado\",\"categoria\":\"ABNT\",\"evidencia\":\"Texto\",\"confianca\":0.9}]}";

    private static String body(HttpRequest request) {
        var out = new java.io.ByteArrayOutputStream();
        var complete = new java.util.concurrent.CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); out.writeBytes(bytes);
            }
            public void onError(Throwable error) { complete.completeExceptionally(error); }
            public void onComplete() { complete.complete(out.toString(java.nio.charset.StandardCharsets.UTF_8)); }
        });
        return complete.join();
    }

    @Test void validatesPersonalSettingsWithoutExposingKey() {
        var config = new ConfiguracaoIa(true, "groq", "llama-3.3-70b-versatile", "personal-secret");
        config.validar();
        assertFalse(config.toString().contains("personal-secret"));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracaoIa(true, "other", "m", "k").validar());
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracaoIa(true, "gemini", "../?key=x", "k").validar());
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracaoIa(true, "groq", "m", "\r\nkey").validar());
        new ConfiguracaoIa(false, null, null, null).validar();
    }

    @Test @SuppressWarnings("unchecked") void allProvidersUsePersonalAuthAndValidateEvidence() throws Exception {
        for (String provider : List.of("gemini", "groq", "openrouter")) {
            var client = mock(HttpClient.class);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            String escaped = new tools.jackson.databind.ObjectMapper().writeValueAsString(RESULT);
            when(response.body()).thenReturn(provider.equals("gemini")
                    ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + escaped + "}]}}]}"
                    : "{\"choices\":[{\"message\":{\"content\":" + escaped + "}}]}");
            when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String expectedHost = switch(provider) {
                    case "gemini" -> "generativelanguage.googleapis.com";
                    case "groq" -> "api.groq.com";
                    default -> "openrouter.ai";
                };
                assertEquals(expectedHost, request.uri().getHost());
                assertEquals("Revora/0.2.1", request.headers().firstValue("User-Agent").orElseThrow());
                assertEquals(provider.equals("gemini") ? "key-" + provider : "Bearer key-" + provider,
                        request.headers().firstValue(provider.equals("gemini") ? "x-goog-api-key" : "Authorization").orElseThrow());
                assertFalse(request.uri().toString().contains("key-"));
                var payload = new tools.jackson.databind.ObjectMapper().readTree(body(request));
                if (provider.equals("gemini")) {
                    assertTrue(request.uri().getPath().contains("chosen-model:generateContent"));
                    assertTrue(payload.has("contents"));
                    assertTrue(payload.has("systemInstruction"));
                } else {
                    assertEquals("chosen-model", payload.path("model").asText());
                    assertEquals("system", payload.path("messages").path(0).path("role").asText());
                }
                return response;
            });
            var guia = mock(GuiaEditorialService.class);
            when(guia.getHumanizacao()).thenReturn("humanizacao");
            when(guia.getAbnt()).thenReturn("abnt");
            var service = new GeminiAnaliseEditorialIaService(guia, provider, "chosen-model", "key-" + provider, client);
            var result = service.analisar(List.of(new TrechoEditorial("P0001", "Texto de exemplo.")));
            assertEquals(1, result.ocorrencias().size());
            assertEquals("P0001", result.ocorrencias().get(0).paragrafoId());
            service.testarConexao();
            verify(client, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
    }

    @Test @SuppressWarnings("unchecked") void failedAuthenticationDoesNotExposeProviderBody() throws Exception {
        var client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("secret-key-echo");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var service = new GeminiAnaliseEditorialIaService(mock(GuiaEditorialService.class), "groq", "model", "secret", client);
        var error = assertThrows(java.io.IOException.class, service::testarConexao);
        assertTrue(error.getMessage().contains("401"));
        assertFalse(error.getMessage().contains("secret"));
    }
    @Test @SuppressWarnings("unchecked") void providerFailuresExposeOnlySafeDiagnostics() throws Exception {
        for (int status : List.of(400, 401, 402, 403, 404, 422, 429, 500, 503)) {
            var client = mock(HttpClient.class);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(status);
            when(response.body()).thenReturn("<html>secret-key-echo</html>");
            when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
            var service = new GeminiAnaliseEditorialIaService(mock(GuiaEditorialService.class), "groq", "model", "secret", client);
            var error = assertThrows(IaConnectionException.class, service::testarConexao);
            assertTrue(error.getMessage().contains("HTTP " + status));
            assertFalse(error.getMessage().contains("secret"));
            if (status == 429) assertTrue(error.getMessage().contains("quota"));
        }
        var model = IaConnectionException.http(400, "model_decommissioned");
        assertTrue(model.getMessage().contains("descontinuado"));
        assertFalse(IaConnectionException.http(400, "secret-key-echo").getMessage().contains("secret"));
    }

    @Test @SuppressWarnings("unchecked") void networkErrorsDoNotExposeOriginalExceptionMessages() throws Exception {
        var unresolved = new java.net.ConnectException("secret");
        unresolved.initCause(new java.net.UnknownHostException("secret"));
        assertTrue(IaConnectionException.from(unresolved).getMessage().contains("DNS"));
        for (Exception failure : List.of(new HttpTimeoutException("secret"),
                new javax.net.ssl.SSLHandshakeException("secret"),
                new java.net.UnknownHostException("secret"), new java.net.ConnectException("secret"))) {
            var client = mock(HttpClient.class);
            when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(failure);
            var service = new GeminiAnaliseEditorialIaService(mock(GuiaEditorialService.class), "groq", "model", "secret", client);
            var error = assertThrows(IaConnectionException.class, service::testarConexao);
            assertFalse(error.getMessage().contains("secret"));
            if (failure instanceof javax.net.ssl.SSLHandshakeException) assertTrue(error.getMessage().contains("certificado"));
            if (failure instanceof java.net.UnknownHostException) assertTrue(error.getMessage().contains("DNS"));
            if (failure instanceof HttpTimeoutException) assertTrue(error.getMessage().contains("prazo"));
        }
    }

    @Test @SuppressWarnings("unchecked") void malformedSuccessfulResponseGetsSafeDiagnostic() throws Exception {
        var client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("secret malformed response");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var service = new GeminiAnaliseEditorialIaService(mock(GuiaEditorialService.class), "groq", "model", "secret", client);
        var error = assertThrows(IaConnectionException.class, service::testarConexao);
        assertTrue(error.getMessage().contains("resposta textual válida"));
        assertFalse(error.getMessage().contains("secret"));
    }

}

package br.com.carlos.revora.controller;

import br.com.carlos.revora.dto.ConfiguracaoIa;
import br.com.carlos.revora.service.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalIaDiagnosticsTests {
    @Test void connectionEndpointReturnsActionableSafeError() throws Exception {
        var controller = new LocalController(mock(DocumentoService.class), mock(GuiaEditorialService.class));
        try (var clients = mockConstruction(GeminiAnaliseEditorialIaService.class, (client, context) -> {
            doThrow(IaConnectionException.http(401, "invalid_api_key")).when(client).testarConexao();
        })) {
            var response = controller.testar(new ConfiguracaoIa(true, "groq", "model", "personal-secret"));
            assertEquals(502, response.getStatusCode().value());
            String message = (String) ((Map<?, ?>) response.getBody()).get("erro");
            assertTrue(message.contains("HTTP 401"));
            assertTrue(message.contains("chave"));
            assertFalse(message.contains("personal-secret"));
            assertEquals(1, clients.constructed().size());
        }
    }
}

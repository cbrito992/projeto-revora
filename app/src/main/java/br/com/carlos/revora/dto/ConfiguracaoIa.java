package br.com.carlos.revora.dto;

import java.util.Set;

/** Personal settings live only for the request; never serialize or log the key. */
public record ConfiguracaoIa(boolean enabled, String provider, String model, String apiKey) {
    public void validar() {
        if (!enabled) return;
        if (provider == null || !Set.of("gemini", "groq", "openrouter").contains(provider))
            throw new IllegalArgumentException("Selecione Gemini, Groq ou OpenRouter.");
        if (model == null || !model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}"))
            throw new IllegalArgumentException("Informe um identificador de modelo válido.");
        if (apiKey == null || apiKey.isBlank() || apiKey.length() > 4096
                || apiKey.chars().anyMatch(c -> c <= 32 || c >= 127))
            throw new IllegalArgumentException("Informe uma chave de API válida.");
    }

    @Override public String toString() {
        return "ConfiguracaoIa[enabled=" + enabled + ", provider=" + provider + ", apiKey=REDACTED]";
    }
}

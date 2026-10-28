package br.com.carlos.revora.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLException;

/** Only fixed, safe messages may cross the local API boundary. */
public final class IaConnectionException extends IOException {
    private IaConnectionException(String message) { super(message); }

    public static IaConnectionException http(int status, String code) {
        String hint = switch (code) {
            case "model_not_found", "model_decommissioned", "model_not_supported", "model_permission_error" ->
                    "O modelo não existe, foi descontinuado ou não está disponível para sua conta. Confira o identificador no painel do provedor.";
            case "invalid_api_key", "API_KEY_INVALID" ->
                    "A chave foi recusada. Use uma chave ativa gerada no painel do provedor selecionado.";
            case "insufficient_quota", "quota_exceeded" ->
                    "A conta está sem quota ou saldo disponível. Confira o painel do provedor.";
            default -> switch (status) {
                case 400, 422 -> "O provedor recusou os parâmetros. Confira o modelo selecionado e os recursos aceitos por ele.";
                case 401 -> "A chave foi recusada. Use uma chave ativa do provedor selecionado.";
                case 402 -> "O provedor informou falta de créditos. Confira o saldo da conta.";
                case 403 -> "O acesso foi recusado. Verifique as permissões da chave e do modelo e possíveis restrições de região, VPN ou proxy.";
                case 404, 410 -> "O modelo ou recurso não está disponível. Confira o identificador no painel do provedor.";
                case 429 -> "O limite de uso ou quota foi atingido. Confira os limites da conta e tente novamente mais tarde.";
                default -> status >= 500
                        ? "O provedor está temporariamente indisponível. Tente novamente mais tarde."
                        : "O provedor recusou a solicitação. Confira o acesso no painel da sua conta.";
            };
        };
        return new IaConnectionException("IA: HTTP " + status + ". " + hint);
    }

    public static IaConnectionException invalidResponse() {
        return new IaConnectionException("O provedor respondeu sem uma resposta textual válida. Confira se o modelo aceita chat e tente outro modelo.");
    }

    public static IaConnectionException from(Throwable error) {
        // Never expose exception messages or response bodies: either can echo credentials.
        boolean connectionFailure = false;
        for (int depth = 0; error != null && depth < 16; depth++, error = error.getCause()) {
            if (error instanceof IaConnectionException safe) return safe;
            if (error instanceof HttpTimeoutException)
                return new IaConnectionException("O provedor não respondeu dentro do prazo. Verifique a conexão e tente novamente.");
            if (error instanceof SSLException)
                return new IaConnectionException("Falha no certificado HTTPS. Confira data e hora do Windows e a configuração de certificados do antivírus ou proxy. Não desative a verificação TLS.");
            if (error instanceof UnknownHostException || error instanceof java.nio.channels.UnresolvedAddressException)
                return new IaConnectionException("Não foi possível localizar o servidor do provedor (DNS). Confira a conexão e a configuração de rede.");
            if (error instanceof ConnectException) connectionFailure = true;
        }
        if (connectionFailure)
            return new IaConnectionException("Não foi possível conectar ao provedor. Verifique internet, firewall, VPN ou proxy.");
        return new IaConnectionException("Não foi possível concluir a conexão com a IA. Verifique a rede e tente novamente.");
    }
}

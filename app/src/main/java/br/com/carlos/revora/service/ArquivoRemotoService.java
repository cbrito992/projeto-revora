package br.com.carlos.revora.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class ArquivoRemotoService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    public InputStream baixar(
            String url
    ) throws IOException, InterruptedException {

        validarUrlBlob(url);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

        HttpResponse<InputStream> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

        if (
                response.statusCode() < 200
                        || response.statusCode() >= 300
        ) {
            response.body().close();

            throw new IOException(
                    "Falha ao acessar arquivo temporário."
            );
        }

        return response.body();
    }

    public void enviar(
            String url,
            byte[] conteudo
    ) throws IOException, InterruptedException {

        validarUrlBlob(url);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header(
                                "Content-Type",
                                DOCX_MIME
                        )
                        .PUT(
                                HttpRequest.BodyPublishers
                                        .ofByteArray(conteudo)
                        )
                        .build();

        HttpResponse<Void> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.discarding()
                );

        if (
                response.statusCode() < 200
                        || response.statusCode() >= 300
        ) {
            throw new IOException(
                    "Falha ao salvar o documento revisado."
            );
        }
    }

    private void validarUrlBlob(
            String url
    ) {

        if (
                url == null
                        || url.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "URL de arquivo inválida."
            );
        }

        URI uri =
                URI.create(url);

        String host =
                uri.getHost();

        if (
                !"https".equalsIgnoreCase(uri.getScheme())
                        || host == null
                        || !host.endsWith(
                                ".private.blob.vercel-storage.com"
                        )
        ) {
            throw new IllegalArgumentException(
                    "Origem de arquivo não autorizada."
            );
        }
    }
}
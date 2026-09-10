package br.com.carlos.revora.service;

import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import java.util.regex.Pattern;


@Service
public class ArquivoRemotoService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";


    private static final int CONNECT_TIMEOUT_MS =
            30_000;


    private static final int READ_TIMEOUT_MS =
            60_000;


    /*
     * URLs de leitura de blobs privados usam o host:
     *
     * <store>.private.blob.vercel-storage.com
     *
     * O identificador do store pode conter "_".
     */
    private static final Pattern VERCEL_PRIVATE_BLOB_HOST =
            Pattern.compile(
                    "^[A-Za-z0-9_-]+\\.private\\.blob\\.vercel-storage\\.com$",
                    Pattern.CASE_INSENSITIVE
            );


    /*
     * URLs assinadas de escrita (PUT) do @vercel/blob atual
     * podem apontar para o control plane da Vercel.
     *
     * Em vez de liberar qualquer host da Vercel, aceitamos
     * somente o host e o caminho específicos usados pela API
     * de Blob.
     */
    private static final String VERCEL_BLOB_CONTROL_HOST =
            "vercel.com";


    private static final String VERCEL_BLOB_CONTROL_PATH =
            "/api/blob";


    /**
     * =========================================================
     * DOWNLOAD
     * =========================================================
     */
    public InputStream baixar(
            String url
    ) throws IOException {

        URL endereco =
                validarUrlLeituraBlob(
                        url
                );


        HttpsURLConnection conexao =
                abrirConexao(
                        endereco
                );


        try {

            conexao.setRequestMethod(
                    "GET"
            );


            int status =
                    conexao.getResponseCode();


            if (
                    status < 200
                            ||
                    status >= 300
            ) {

                String contentType =
                        conexao.getContentType();


                fecharStreamErro(
                        conexao
                );


                conexao.disconnect();


                throw new IOException(
                        "Falha ao acessar arquivo temporário. "
                                +
                        "HTTP "
                                +
                        status
                                +
                        " | host="
                                +
                        endereco.getHost()
                                +
                        " | content-type="
                                +
                        (
                                contentType != null
                                        ?
                                contentType
                                        :
                                "desconhecido"
                        )
                );
            }


            InputStream entrada =
                    conexao.getInputStream();


            /*
             * A conexão permanece aberta enquanto
             * o InputStream estiver sendo utilizado.
             *
             * Quando RevisaoService fechar o stream,
             * desconectamos também a conexão HTTPS.
             */
            return new FilterInputStream(
                    entrada
            ) {

                @Override
                public void close()
                        throws IOException {

                    try {

                        super.close();

                    } finally {

                        conexao.disconnect();
                    }
                }
            };

        } catch (IOException e) {

            conexao.disconnect();

            throw e;
        }
    }


    /**
     * =========================================================
     * UPLOAD DO DOCUMENTO REVISADO
     * =========================================================
     */
    public void enviar(
            String url,
            byte[] conteudo
    ) throws IOException {

        if (conteudo == null) {

            throw new IllegalArgumentException(
                    "Conteúdo do documento revisado inválido."
            );
        }


        URL endereco =
                validarUrlUploadBlob(
                        url
                );


        HttpsURLConnection conexao =
                abrirConexao(
                        endereco
                );


        try {

            conexao.setRequestMethod(
                    "PUT"
            );


            conexao.setDoOutput(
                    true
            );


            conexao.setRequestProperty(
                    "Content-Type",
                    DOCX_MIME
            );


            conexao.setRequestProperty(
                    "x-content-type",
                    DOCX_MIME
            );


            conexao.setRequestProperty(
                    "x-vercel-blob-access",
                    "private"
            );


            conexao.setFixedLengthStreamingMode(
                    conteudo.length
            );


            try (
                    OutputStream output =
                            conexao.getOutputStream()
            ) {

                output.write(
                        conteudo
                );


                output.flush();
            }


            int status =
                    conexao.getResponseCode();


            if (
                    status < 200
                            ||
                    status >= 300
            ) {

                String contentType =
                        conexao.getContentType();


                fecharStreamErro(
                        conexao
                );


                throw new IOException(
                        "Falha ao salvar o documento revisado. "
                                +
                        "HTTP "
                                +
                        status
                                +
                        " | host="
                                +
                        endereco.getHost()
                                +
                        " | content-type="
                                +
                        (
                                contentType != null
                                        ?
                                contentType
                                        :
                                "desconhecido"
                        )
                );
            }

        } finally {

            conexao.disconnect();
        }
    }


    /**
     * =========================================================
     * CONEXÃO HTTPS
     * =========================================================
     */
    private HttpsURLConnection abrirConexao(
            URL endereco
    ) throws IOException {

        URLConnection conexaoBase =
                endereco.openConnection();


        if (
                !(conexaoBase
                        instanceof HttpsURLConnection conexao)
        ) {

            throw new IOException(
                    "A conexão do arquivo não utiliza HTTPS."
            );
        }


        conexao.setConnectTimeout(
                CONNECT_TIMEOUT_MS
        );


        conexao.setReadTimeout(
                READ_TIMEOUT_MS
        );


        /*
         * Não seguimos redirects.
         *
         * Isso evita que uma URL inicialmente autorizada
         * redirecione o backend para uma origem diferente.
         */
        conexao.setInstanceFollowRedirects(
                false
        );


        conexao.setUseCaches(
                false
        );


        return conexao;
    }


    /**
     * =========================================================
     * VALIDAÇÃO DAS URLs
     * =========================================================
     */

    /*
     * URLs de GET precisam apontar diretamente para
     * o host privado do Vercel Blob.
     */
    private URL validarUrlLeituraBlob(
            String valor
    ) {

        URL endereco =
                criarUrlHttpsSegura(
                        valor
                );


        String host =
                endereco.getHost();


        if (
                host == null
                        ||
                host.isBlank()
                        ||
                !VERCEL_PRIVATE_BLOB_HOST
                        .matcher(
                                host
                        )
                        .matches()
        ) {

            throw new IllegalArgumentException(
                    "Origem de leitura do arquivo não autorizada."
            );
        }


        return endereco;
    }


    /*
     * URLs de PUT podem ter duas formas:
     *
     * 1. host privado do próprio Blob;
     * 2. URL assinada do control plane:
     *    https://vercel.com/api/blob?...assinatura...
     *
     * Mantemos uma allowlist estrita para não transformar
     * o backend em um cliente HTTP para destinos arbitrários.
     */
    private URL validarUrlUploadBlob(
            String valor
    ) {

        URL endereco =
                criarUrlHttpsSegura(
                        valor
                );


        String host =
                endereco.getHost();


        String path =
                endereco.getPath();


        boolean hostPrivadoBlob =
                host != null
                        &&
                VERCEL_PRIVATE_BLOB_HOST
                        .matcher(
                                host
                        )
                        .matches();


        boolean controlPlaneBlob =
                host != null
                        &&
                VERCEL_BLOB_CONTROL_HOST
                        .equalsIgnoreCase(
                                host
                        )
                        &&
                path != null
                        &&
                (
                        path.equals(
                                VERCEL_BLOB_CONTROL_PATH
                        )
                                ||
                        path.startsWith(
                                VERCEL_BLOB_CONTROL_PATH + "/"
                        )
                );


        if (
                !hostPrivadoBlob
                        &&
                !controlPlaneBlob
        ) {

            throw new IllegalArgumentException(
                    "Origem de upload do arquivo não autorizada."
            );
        }


        return endereco;
    }


    /*
     * Regras comuns de segurança para qualquer URL aceita.
     */
    private URL criarUrlHttpsSegura(
            String valor
    ) {

        if (
                valor == null
                        ||
                valor.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "URL de arquivo inválida."
            );
        }


        final URL endereco;


        try {

            endereco =
                    new URL(
                            valor
                    );

        } catch (MalformedURLException e) {

            throw new IllegalArgumentException(
                    "URL de arquivo inválida.",
                    e
            );
        }


        String protocolo =
                endereco.getProtocol();


        String host =
                endereco.getHost();


        if (
                !"https".equalsIgnoreCase(
                        protocolo
                )
                        ||
                host == null
                        ||
                host.isBlank()
                        ||
                endereco.getUserInfo() != null
                        ||
                endereco.getPort() != -1
                        ||
                endereco.getRef() != null
        ) {

            throw new IllegalArgumentException(
                    "Origem de arquivo não autorizada."
            );
        }


        return endereco;
    }


    /**
     * =========================================================
     * UTILITÁRIO
     * =========================================================
     */
    private void fecharStreamErro(
            HttpsURLConnection conexao
    ) {

        InputStream erro =
                conexao.getErrorStream();


        if (erro == null) {
            return;
        }


        try {

            erro.close();

        } catch (IOException ignored) {

            /*
             * A falha ao fechar o stream de erro
             * não deve esconder o erro HTTP original.
             */
        }
    }
}
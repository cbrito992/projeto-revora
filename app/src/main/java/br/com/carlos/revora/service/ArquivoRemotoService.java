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
     * URLs privadas do Vercel Blob podem usar
     * identificadores contendo "_".
     *
     * Exemplo:
     *
     * store_abc123.private.blob.vercel-storage.com
     *
     * java.net.URI#getHost() pode retornar null
     * para esse formato. Por isso a validação
     * passa a utilizar java.net.URL.
     */
    private static final Pattern VERCEL_PRIVATE_BLOB_HOST =
            Pattern.compile(
                    "^[A-Za-z0-9_-]+\\.private\\.blob\\.vercel-storage\\.com$",
                    Pattern.CASE_INSENSITIVE
            );


    /**
     * =========================================================
     * DOWNLOAD
     * =========================================================
     */
    public InputStream baixar(
            String url
    ) throws IOException {

        URL endereco =
                validarUrlBlob(
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
                validarUrlBlob(
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


            /*
             * Mesmos cabeçalhos usados pelo
             * upload direto do frontend.
             */
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
         * A URL assinada pelo Blob já deve apontar
         * diretamente para o destino autorizado.
         *
         * Isso também impede que um eventual redirect
         * leve o backend para outra origem.
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
     * VALIDAÇÃO DA ORIGEM
     * =========================================================
     */
    private URL validarUrlBlob(
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


        /*
         * Não aceitamos:
         *
         * - HTTP;
         * - outro domínio;
         * - user info;
         * - porta customizada;
         * - fragmento.
         *
         * O "_" é permitido somente no identificador
         * do store porque a Vercel pode utilizá-lo
         * em URLs privadas assinadas.
         */
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
                        ||
                !VERCEL_PRIVATE_BLOB_HOST
                        .matcher(
                                host
                        )
                        .matches()
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
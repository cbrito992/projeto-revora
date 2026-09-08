const clickSound =
    new Audio('mixkit-mouse-click-close-1113.wav');

const errorSound =
    new Audio('mixkit-click-error-1110.wav');

const MAX_FILE_SIZE =
    50 * 1024 * 1024;

const DOCX_MIME =
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

const REQUEST_TIMEOUT =
    30 * 1000;


/**
 * =========================================================
 * FETCH COM TIMEOUT
 * =========================================================
 */
async function fetchComTimeout(
    url,
    options = {},
    timeout = REQUEST_TIMEOUT
) {

    const controller =
        new AbortController();

    const timer =
        setTimeout(
            () => controller.abort(),
            timeout
        );

    try {

        return await fetch(
            url,
            {
                ...options,
                signal:
                    controller.signal
            }
        );

    } finally {

        clearTimeout(timer);
    }
}


/**
 * =========================================================
 * VALIDAÇÃO DO ARQUIVO
 * =========================================================
 */
function validarExtensao(
    inputElement
) {

    const file =
        inputElement.files[0];

    if (
        file &&
        !file.name
            .toLowerCase()
            .endsWith('.docx')
    ) {

        errorSound.play();

        alert(
            'Formato inválido. Por favor, envie apenas arquivos .docx.'
        );

        inputElement.value = '';

        return false;
    }


    if (
        file &&
        file.size > MAX_FILE_SIZE
    ) {

        errorSound.play();

        alert(
            'O limite é de 50 MB por arquivo.'
        );

        inputElement.value = '';

        return false;
    }


    return true;
}


/**
 * =========================================================
 * LEITURA PADRONIZADA DAS RESPOSTAS
 * =========================================================
 */
async function lerJson(
    response
) {

    const data =
        await response
            .json()
            .catch(
                () => ({})
            );


    if (!response.ok) {

        throw new Error(
            data.erro ||
            `O servidor retornou erro HTTP ${response.status}.`
        );
    }


    return data;
}


/**
 * =========================================================
 * CRIAÇÃO DA SESSÃO
 * =========================================================
 */
async function criarSessao(
    templateFile,
    documentoFile
) {

    const response =
        await fetchComTimeout(
            '/blob/session',
            {
                method:
                    'POST',

                headers: {
                    'Content-Type':
                        'application/json'
                },

                body:
                    JSON.stringify({
                        templateSize:
                            templateFile.size,

                        documentoSize:
                            documentoFile.size
                    })
            }
        );


    return lerJson(
        response
    );
}


/**
 * =========================================================
 * UPLOAD DIRETO PARA O BLOB
 * =========================================================
 */
async function enviarArquivo(
    url,
    file
) {

    if (!url) {

        throw new Error(
            'URL de upload não recebida.'
        );
    }


    const response =
        await fetchComTimeout(
            url,
            {
                method:
                    'PUT',

                headers: {
                    'Content-Type':
                        DOCX_MIME,

                    'x-content-type':
                        DOCX_MIME,

                    'x-vercel-blob-access':
                        'private'
                },

                body:
                    file
            }
        );


    if (!response.ok) {

        throw new Error(
            `Não foi possível enviar um dos arquivos. HTTP ${response.status}.`
        );
    }
}


/**
 * =========================================================
 * URLs PARA O BACKEND JAVA
 * =========================================================
 */
async function obterAcessoProcessamento(
    sessao
) {

    const response =
        await fetchComTimeout(
            '/blob/process-access',
            {
                method:
                    'POST',

                headers: {
                    'Content-Type':
                        'application/json'
                },

                body:
                    JSON.stringify({
                        sessao
                    })
            }
        );


    const acesso =
        await lerJson(
            response
        );


    if (
        !acesso.templateReadUrl ||
        !acesso.documentoReadUrl ||
        !acesso.resultadoUploadUrl
    ) {

        throw new Error(
            'O servidor não forneceu todas as URLs necessárias para o processamento.'
        );
    }


    return acesso;
}


/**
 * =========================================================
 * PROCESSAMENTO JAVA
 * =========================================================
 */
async function processarDocumento(
    acesso
) {

    const response =
        await fetchComTimeout(
            '/api/revisar',
            {
                method:
                    'POST',

                headers: {
                    'Content-Type':
                        'application/json'
                },

                body:
                    JSON.stringify({
                        sessao:
                            acesso.sessao,

                        templateReadUrl:
                            acesso.templateReadUrl,

                        documentoReadUrl:
                            acesso.documentoReadUrl,

                        resultadoUploadUrl:
                            acesso.resultadoUploadUrl
                    })
            },

            /*
             * A revisão pode levar mais que
             * uma requisição HTTP comum.
             */
            5 * 60 * 1000
        );


    return lerJson(
        response
    );
}


/**
 * =========================================================
 * URL DE DOWNLOAD
 * =========================================================
 */
async function obterDownload(
    sessao
) {

    const response =
        await fetchComTimeout(
            '/blob/download-access',
            {
                method:
                    'POST',

                headers: {
                    'Content-Type':
                        'application/json'
                },

                body:
                    JSON.stringify({
                        sessao
                    })
            }
        );


    const data =
        await lerJson(
            response
        );


    if (!data.downloadUrl) {

        throw new Error(
            'O servidor não forneceu a URL do documento revisado.'
        );
    }


    return data;
}


/**
 * =========================================================
 * DOWNLOAD DO RESULTADO
 * =========================================================
 */
async function baixarDocumento(
    url
) {

    const response =
        await fetchComTimeout(
            url,
            {},
            60 * 1000
        );


    if (!response.ok) {

        throw new Error(
            `Não foi possível baixar o documento revisado. HTTP ${response.status}.`
        );
    }


    return response.blob();
}


/**
 * =========================================================
 * LIMPEZA DA SESSÃO
 * =========================================================
 */
async function limparSessao(
    sessao
) {

    if (!sessao) {
        return;
    }


    try {

        await fetchComTimeout(
            '/blob/cleanup',
            {
                method:
                    'POST',

                headers: {
                    'Content-Type':
                        'application/json'
                },

                body:
                    JSON.stringify({
                        sessao
                    })
            }
        );

    } catch (error) {

        /*
         * Falha de limpeza não deve impedir
         * a entrega do resultado ao usuário.
         */
        console.warn(
            'Não foi possível limpar a sessão temporária.'
        );
    }
}


/**
 * =========================================================
 * DOWNLOAD LOCAL
 * =========================================================
 */
function salvarDocumento(
    blob
) {

    const url =
        URL.createObjectURL(
            blob
        );

    const link =
        document.createElement(
            'a'
        );


    link.href =
        url;

    link.download =
        'documento_revisado.docx';


    document.body
        .appendChild(
            link
        );


    link.click();

    link.remove();


    setTimeout(
        () => {

            URL.revokeObjectURL(
                url
            );

        },
        1000
    );
}


/**
 * =========================================================
 * EVENTOS DOS INPUTS
 * =========================================================
 */
document
    .getElementById(
        'template'
    )
    .addEventListener(
        'change',
        function () {

            validarExtensao(
                this
            );
        }
    );


document
    .getElementById(
        'documento'
    )
    .addEventListener(
        'change',
        function () {

            validarExtensao(
                this
            );
        }
    );


/**
 * =========================================================
 * PROCESSO PRINCIPAL
 * =========================================================
 */
document
    .getElementById(
        'uploadForm'
    )
    .addEventListener(
        'submit',
        async function (e) {

            e.preventDefault();

            clickSound.play();


            const templateInput =
                document.getElementById(
                    'template'
                );

            const documentoInput =
                document.getElementById(
                    'documento'
                );

            const templateFile =
                templateInput.files[0];

            const documentoFile =
                documentoInput.files[0];

            const btnSubmit =
                document.getElementById(
                    'btnSubmit'
                );

            const loadingSection =
                document.getElementById(
                    'loadingSection'
                );

            const progressBar =
                document.getElementById(
                    'progressBar'
                );


            if (
                !templateFile ||
                !documentoFile
            ) {

                errorSound.play();

                alert(
                    'Selecione o template e o documento que deseja revisar.'
                );

                return;
            }


            if (
                !validarExtensao(
                    templateInput
                ) ||
                !validarExtensao(
                    documentoInput
                )
            ) {

                return;
            }


            let sessao =
                null;


            function resetUI() {

                btnSubmit.disabled =
                    false;

                btnSubmit.innerText =
                    'Iniciar Revisão';

                loadingSection.style.display =
                    'none';

                progressBar.style.width =
                    '0%';
            }


            btnSubmit.disabled =
                true;
            btnSubmit.innerText =
                'Processando...';

            loadingSection.style.display =
                'block';

            progressBar.style.width =
                '5%';


            try {

                /*
                 * 1. Cria sessão.
                 */
                const dadosSessao =
                    await criarSessao(
                        templateFile,
                        documentoFile
                    );


                sessao =
                    dadosSessao.sessao;


                if (!sessao) {

                    throw new Error(
                        'O servidor não retornou uma sessão válida.'
                    );
                }


                progressBar.style.width =
                    '15%';


                /*
                 * 2. Envia os dois arquivos.
                 */
                await Promise.all([
                    enviarArquivo(
                        dadosSessao
                            .templateUploadUrl,

                        templateFile
                    ),

                    enviarArquivo(
                        dadosSessao
                            .documentoUploadUrl,

                        documentoFile
                    )
                ]);


                progressBar.style.width =
                    '45%';


                /*
                 * 3. Obtém URLs GET/PUT
                 * específicas para o Java.
                 */
                const acesso =
                    await obterAcessoProcessamento(
                        sessao
                    );


                progressBar.style.width =
                    '55%';


                /*
                 * 4. Java revisa o DOCX.
                 */
                await processarDocumento(
                    acesso
                );


                progressBar.style.width =
                    '85%';


                /*
                 * 5. Solicita URL do resultado.
                 */
                const download =
                    await obterDownload(
                        sessao
                    );


                progressBar.style.width =
                    '90%';


                /*
                 * 6. Baixa o resultado.
                 */
                const arquivoRevisado =
                    await baixarDocumento(
                        download.downloadUrl
                    );


                progressBar.style.width =
                    '98%';


                /*
                 * 7. Remove os temporários.
                 */
                await limparSessao(
                    sessao
                );

                sessao =
                    null;


                /*
                 * 8. Salva no computador.
                 */
                salvarDocumento(
                    arquivoRevisado
                );


                progressBar.style.width =
                    '100%';


                setTimeout(
                    () => {

                        alert(
                            'Revisão concluída! O arquivo foi baixado.'
                        );

                        resetUI();

                    },
                    500
                );


            } catch (error) {

                if (sessao) {

                    await limparSessao(
                        sessao
                    );
                }


                errorSound.play();


                let mensagem =
                    'Erro desconhecido.';


                if (
                    error instanceof Error
                ) {

                    if (
                        error.name ===
                        'AbortError'
                    ) {

                        mensagem =
                            'A operação demorou mais que o esperado e foi cancelada.';

                    } else {

                        mensagem =
                            error.message;
                    }
                }


                alert(
                    'Erro no processamento: ' +
                    mensagem
                );


                resetUI();
            }
        }
    );
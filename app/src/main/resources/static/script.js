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
 * ELEMENTOS PRINCIPAIS DA INTERFACE
 * =========================================================
 */

const uploadForm =
    document.getElementById(
        'uploadForm'
    );

const templateInput =
    document.getElementById(
        'template'
    );

const documentoInput =
    document.getElementById(
        'documento'
    );

const btnSubmit =
    document.getElementById(
        'btnSubmit'
    );

const loadingSection =
    document.getElementById(
        'loadingSection'
    );

const loadingText =
    document.getElementById(
        'loadingText'
    );

const progressBar =
    document.getElementById(
        'progressBar'
    );


/**
 * =========================================================
 * SOM
 * =========================================================
 */

function tocarSom(
    audio
) {

    if (!audio) {
        return;
    }


    audio
        .play()
        .catch(
            () => {

                /*
                 * O som é apenas complementar.
                 * Uma falha de reprodução não deve
                 * interferir no funcionamento do Revora.
                 */
            }
        );
}


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

        clearTimeout(
            timer
        );
    }
}


/**
 * =========================================================
 * UTILITÁRIOS VISUAIS
 * =========================================================
 */

function formatarTamanhoArquivo(
    bytes
) {

    if (
        !Number.isFinite(bytes)
        ||
        bytes <= 0
    ) {

        return '0 KB';
    }


    const mb =
        bytes / (1024 * 1024);


    if (mb >= 1) {

        return `${mb.toFixed(2)} MB`;
    }


    const kb =
        bytes / 1024;


    return `${Math.max(
        1,
        Math.round(kb)
    )} KB`;
}


function obterFilePicker(
    inputElement
) {

    if (!inputElement) {
        return null;
    }


    return document.querySelector(
        `.file-picker[for="${inputElement.id}"]`
    );
}


function atualizarVisualArquivo(
    inputElement,
    file
) {

    const picker =
        obterFilePicker(
            inputElement
        );


    if (!picker) {
        return;
    }


    const titulo =
        picker.querySelector(
            '.file-picker-copy strong'
        );

    const descricao =
        picker.querySelector(
            '.file-picker-copy span'
        );


    if (
        !titulo
        ||
        !descricao
    ) {

        return;
    }


    /*
     * Nenhum arquivo selecionado.
     */
    if (!file) {

        if (
            inputElement.id ===
            'template'
        ) {

            titulo.textContent =
                'Selecionar template';

        } else {

            titulo.textContent =
                'Selecionar documento';
        }


        descricao.textContent =
            'Arquivo DOCX de até 50 MB';


        picker.classList.remove(
            'has-file'
        );


        picker.removeAttribute(
            'title'
        );


        return;
    }


    /*
     * Arquivo válido selecionado.
     */
    titulo.textContent =
        file.name;


    descricao.textContent =
        `DOCX • ${formatarTamanhoArquivo(file.size)} • arquivo selecionado`;


    picker.classList.add(
        'has-file'
    );


    picker.title =
        file.name;
}


function atualizarBotao(
    texto,
    desabilitado
) {

    if (!btnSubmit) {
        return;
    }


    btnSubmit.disabled =
        desabilitado;


    const textoBotao =
        btnSubmit.querySelector(
            'span:first-child'
        );


    if (textoBotao) {

        textoBotao.textContent =
            texto;

    } else {

        btnSubmit.textContent =
            texto;
    }
}


function atualizarProgresso(
    percentual,
    mensagem
) {

    const valor =
        Math.max(
            0,
            Math.min(
                100,
                percentual
            )
        );


    if (progressBar) {

        progressBar.style.width =
            `${valor}%`;


        const container =
            progressBar.parentElement;


        if (container) {

            container.setAttribute(
                'aria-valuenow',
                String(valor)
            );
        }
    }


    if (
        loadingText
        &&
        mensagem
    ) {

        loadingText.textContent =
            mensagem;
    }
}


function mostrarCarregamento() {

    if (loadingSection) {

        loadingSection.style.display =
            'block';
    }


    if (uploadForm) {

        uploadForm.setAttribute(
            'aria-busy',
            'true'
        );
    }
}


function esconderCarregamento() {

    if (loadingSection) {

        loadingSection.style.display =
            'none';
    }


    if (uploadForm) {

        uploadForm.removeAttribute(
            'aria-busy'
        );
    }
}


function resetUI() {

    atualizarBotao(
        'Iniciar revisão',
        false
    );


    atualizarProgresso(
        0,
        'Analisando documento...'
    );


    esconderCarregamento();
}


/**
 * =========================================================
 * VALIDAÇÃO DO ARQUIVO
 * =========================================================
 */

function validarExtensao(
    inputElement
) {

    if (!inputElement) {
        return false;
    }


    const file =
        inputElement.files[0];


    /*
     * Usuário removeu/cancelou o arquivo.
     */
    if (!file) {

        atualizarVisualArquivo(
            inputElement,
            null
        );


        return true;
    }


    /*
     * Valida extensão.
     */
    if (
        !file.name
            .toLowerCase()
            .endsWith('.docx')
    ) {

        tocarSom(
            errorSound
        );


        alert(
            'Formato inválido. Por favor, envie apenas arquivos .docx.'
        );


        inputElement.value =
            '';


        atualizarVisualArquivo(
            inputElement,
            null
        );


        return false;
    }


    /*
     * Valida tamanho máximo.
     */
    if (
        file.size >
        MAX_FILE_SIZE
    ) {

        tocarSom(
            errorSound
        );


        alert(
            'O limite é de 50 MB por arquivo.'
        );


        inputElement.value =
            '';


        atualizarVisualArquivo(
            inputElement,
            null
        );


        return false;
    }


    atualizarVisualArquivo(
        inputElement,
        file
    );


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
            data.erro
            ||
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
 * UPLOAD DIRETO PARA O VERCEL BLOB
 * =========================================================
 */

async function enviarArquivo(
    url,
    file,
    pathnameEsperado
) {

    if (!url) {

        throw new Error(
            'URL de upload não recebida.'
        );
    }


    if (!pathnameEsperado) {

        throw new Error(
            'Pathname do arquivo não recebido.'
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
            },

            2 * 60 * 1000
        );


    if (!response.ok) {

        throw new Error(
            `Não foi possível enviar o arquivo ${pathnameEsperado}. HTTP ${response.status}.`
        );
    }
}


/**
 * =========================================================
 * URLs PARA O BACKEND JAVA
 * =========================================================
 */

async function obterAcessoProcessamento(
    sessao,
    templatePathname,
    documentoPathname
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
                        sessao,
                        templatePathname,
                        documentoPathname
                    })
            }
        );


    const acesso =
        await lerJson(
            response
        );


    if (
        !acesso.templateReadUrl
        ||
        !acesso.documentoReadUrl
        ||
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
             * A revisão pode envolver:
             *
             * - leitura do DOCX;
             * - Hunspell;
             * - comparação com template;
             * - análise editorial por IA;
             * - geração do novo DOCX.
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
         * Falha de limpeza não impede
         * a entrega do documento ao usuário.
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

if (templateInput) {

    templateInput.addEventListener(
        'change',
        function () {

            validarExtensao(
                this
            );
        }
    );
}


if (documentoInput) {

    documentoInput.addEventListener(
        'change',
        function () {

            validarExtensao(
                this
            );
        }
    );
}


/**
 * =========================================================
 * PROCESSO PRINCIPAL
 * =========================================================
 */

if (uploadForm) {

    uploadForm.addEventListener(
        'submit',
        async function (e) {

            e.preventDefault();


            tocarSom(
                clickSound
            );


            const templateFile =
                templateInput
                    ? templateInput.files[0]
                    : null;


            const documentoFile =
                documentoInput
                    ? documentoInput.files[0]
                    : null;


            /*
             * Confirma presença dos dois arquivos.
             */
            if (
                !templateFile
                ||
                !documentoFile
            ) {

                tocarSom(
                    errorSound
                );


                alert(
                    'Selecione o template e o documento que deseja revisar.'
                );


                return;
            }


            /*
             * Nova validação antes do envio.
             */
            if (
                !validarExtensao(
                    templateInput
                )
                ||
                !validarExtensao(
                    documentoInput
                )
            ) {

                return;
            }


            let sessao =
                null;


            atualizarBotao(
                'Processando...',
                true
            );


            mostrarCarregamento();


            atualizarProgresso(
                5,
                'Preparando a revisão...'
            );


            try {

                /*
                 * =================================================
                 * 1. CRIA SESSÃO
                 * =================================================
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


                atualizarProgresso(
                    15,
                    'Sessão criada. Enviando arquivos...'
                );


                /*
                 * =================================================
                 * 2. ENVIA TEMPLATE E DOCUMENTO
                 * =================================================
                 */

                if (
                    !dadosSessao.templatePathname
                    ||
                    !dadosSessao.documentoPathname
                ) {

                    throw new Error(
                        'O servidor não retornou os pathnames dos arquivos.'
                    );
                }


                await Promise.all([

                    enviarArquivo(
                        dadosSessao
                            .templateUploadUrl,

                        templateFile,

                        dadosSessao
                            .templatePathname
                    ),

                    enviarArquivo(
                        dadosSessao
                            .documentoUploadUrl,

                        documentoFile,

                        dadosSessao
                            .documentoPathname
                    )

                ]);


                atualizarProgresso(
                    45,
                    'Arquivos enviados. Preparando análise...'
                );


                /*
                 * =================================================
                 * 3. GERA ACESSOS PARA O JAVA
                 * =================================================
                 */

                const acesso =
                    await obterAcessoProcessamento(
                        sessao,

                        dadosSessao
                            .templatePathname,

                        dadosSessao
                            .documentoPathname
                    );


                atualizarProgresso(
                    55,
                    'Revisando ortografia, padrões e conteúdo editorial...'
                );


                /*
                 * =================================================
                 * 4. PROCESSAMENTO DO DOCUMENTO
                 * =================================================
                 *
                 * Se a IA estiver indisponível, o backend
                 * continua com Hunspell + Template.
                 */

                await processarDocumento(
                    acesso
                );


                atualizarProgresso(
                    85,
                    'Revisão concluída. Preparando o arquivo...'
                );


                /*
                 * =================================================
                 * 5. GERA URL PARA DOWNLOAD
                 * =================================================
                 */

                const download =
                    await obterDownload(
                        sessao
                    );


                atualizarProgresso(
                    90,
                    'Preparando download...'
                );


                /*
                 * =================================================
                 * 6. BAIXA O RESULTADO
                 * =================================================
                 */

                const arquivoRevisado =
                    await baixarDocumento(
                        download.downloadUrl
                    );


                atualizarProgresso(
                    97,
                    'Finalizando...'
                );


                /*
                 * =================================================
                 * 7. REMOVE ARQUIVOS TEMPORÁRIOS
                 * =================================================
                 */

                await limparSessao(
                    sessao
                );


                sessao =
                    null;


                /*
                 * =================================================
                 * 8. SALVA NO COMPUTADOR
                 * =================================================
                 */

                salvarDocumento(
                    arquivoRevisado
                );


                atualizarProgresso(
                    100,
                    'Revisão concluída.'
                );


                setTimeout(
                    () => {

                        alert(
                            'Revisão concluída! O arquivo revisado foi baixado.'
                        );


                        resetUI();

                    },
                    500
                );


            } catch (error) {

                /*
                 * =================================================
                 * TRATAMENTO DE ERRO
                 * =================================================
                 */

                atualizarProgresso(
                    0,
                    'Não foi possível concluir a revisão.'
                );


                /*
                 * Mesmo com erro, tenta limpar
                 * arquivos temporários.
                 */
                if (sessao) {

                    await limparSessao(
                        sessao
                    );
                }


                tocarSom(
                    errorSound
                );


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
                    'Erro no processamento: '
                    +
                    mensagem
                );


                resetUI();
            }
        }
    );
}


/**
 * =========================================================
 * ESTADO INICIAL DOS SELETORES
 * =========================================================
 */

atualizarVisualArquivo(
    templateInput,

    templateInput
        ? templateInput.files[0]
        : null
);


atualizarVisualArquivo(
    documentoInput,

    documentoInput
        ? documentoInput.files[0]
        : null
);
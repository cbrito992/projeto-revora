const clickSound = new Audio('mixkit-mouse-click-close-1113.wav');
const errorSound = new Audio('mixkit-click-error-1110.wav');

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const DOCX_MIME =
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

function validarExtensao(inputElement) {
    const file = inputElement.files[0];

    if (file && !file.name.toLowerCase().endsWith('.docx')) {
        errorSound.play();
        alert('Formato inválido. Por favor, envie apenas arquivos .docx.');
        inputElement.value = '';
        return false;
    }

    if (file && file.size > MAX_FILE_SIZE) {
        errorSound.play();
        alert('O limite é de 50 MB por arquivo.');
        inputElement.value = '';
        return false;
    }

    return true;
}

async function lerJson(response) {
    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        throw new Error(
            data.erro || 'O servidor retornou um erro.'
        );
    }

    return data;
}

async function criarSessao(templateFile, documentoFile) {
    const response = await fetch('/blob/session', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            templateSize: templateFile.size,
            documentoSize: documentoFile.size
        })
    });

    return lerJson(response);
}

async function enviarArquivo(url, file) {
    const response = await fetch(url, {
        method: 'PUT',
        headers: {
            'Content-Type': DOCX_MIME
        },
        body: file
    });

    if (!response.ok) {
        throw new Error(
            'Não foi possível enviar um dos arquivos.'
        );
    }
}

async function obterAcessoProcessamento(sessao) {
    const response = await fetch('/blob/process-access', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            sessao
        })
    });

    return lerJson(response);
}

async function processarDocumento(acesso) {
    const response = await fetch('/api/revisar', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            sessao: acesso.sessao,
            templateReadUrl: acesso.templateReadUrl,
            documentoReadUrl: acesso.documentoReadUrl,
            resultadoUploadUrl: acesso.resultadoUploadUrl
        })
    });

    return lerJson(response);
}

async function obterDownload(sessao) {
    const response = await fetch('/blob/download-access', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            sessao
        })
    });

    return lerJson(response);
}

async function baixarDocumento(url) {
    const response = await fetch(url);

    if (!response.ok) {
        throw new Error(
            'Não foi possível baixar o documento revisado.'
        );
    }

    return response.blob();
}

async function limparSessao(sessao) {
    if (!sessao) {
        return;
    }

    try {
        await fetch('/blob/cleanup', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessao
            })
        });
    } catch {
    }
}

function salvarDocumento(blob) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');

    link.href = url;
    link.download = 'documento_revisado.docx';

    document.body.appendChild(link);
    link.click();
    link.remove();

    setTimeout(() => {
        URL.revokeObjectURL(url);
    }, 1000);
}

document
    .getElementById('template')
    .addEventListener('change', function () {
        validarExtensao(this);
    });

document
    .getElementById('documento')
    .addEventListener('change', function () {
        validarExtensao(this);
    });

document
    .getElementById('uploadForm')
    .addEventListener('submit', async function (e) {
        e.preventDefault();

        clickSound.play();

        const templateInput =
            document.getElementById('template');

        const documentoInput =
            document.getElementById('documento');

        const templateFile =
            templateInput.files[0];

        const documentoFile =
            documentoInput.files[0];

        const btnSubmit =
            document.getElementById('btnSubmit');

        const loadingSection =
            document.getElementById('loadingSection');

        const progressBar =
            document.getElementById('progressBar');

        if (!templateFile || !documentoFile) {
            errorSound.play();
            alert(
                'Selecione o template e o documento que deseja revisar.'
            );
            return;
        }

        if (
            !validarExtensao(templateInput) ||
            !validarExtensao(documentoInput)
        ) {
            return;
        }

        let sessao = null;

        btnSubmit.disabled = true;
        btnSubmit.innerText = 'Processando...';

        loadingSection.style.display = 'block';
        progressBar.style.width = '5%';

        try {
            const dadosSessao =
                await criarSessao(
                    templateFile,
                    documentoFile
                );

            sessao = dadosSessao.sessao;

            progressBar.style.width = '15%';

            await Promise.all([
                enviarArquivo(
                    dadosSessao.templateUploadUrl,
                    templateFile
                ),
                enviarArquivo(
                    dadosSessao.documentoUploadUrl,
                    documentoFile
                )
            ]);

            progressBar.style.width = '45%';

            const acesso =
                await obterAcessoProcessamento(
                    sessao
                );

            progressBar.style.width = '55%';

            await processarDocumento(acesso);

            progressBar.style.width = '85%';

            const download =
                await obterDownload(
                    sessao
                );

            progressBar.style.width = '90%';

            const arquivoRevisado =
                await baixarDocumento(
                    download.downloadUrl
                );

            progressBar.style.width = '98%';

            await limparSessao(sessao);

            sessao = null;

            salvarDocumento(
                arquivoRevisado
            );

            progressBar.style.width = '100%';

            setTimeout(() => {
                alert(
                    'Revisão concluída! O arquivo foi baixado.'
                );

                resetUI();
            }, 500);

        } catch (error) {
            if (sessao) {
                await limparSessao(sessao);
            }

            errorSound.play();

            alert(
                'Erro no processamento: ' +
                (
                    error instanceof Error
                        ? error.message
                        : 'Erro desconhecido.'
                )
            );

            resetUI();
        }

        function resetUI() {
            btnSubmit.disabled = false;
            btnSubmit.innerText = 'Iniciar Revisão';

            loadingSection.style.display = 'none';
            progressBar.style.width = '0%';
        }
    });
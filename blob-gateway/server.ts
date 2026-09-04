import { createServer } from "node:http";
import { randomUUID } from "node:crypto";

import {
    issueSignedToken,
    presignUrl
} from "@vercel/blob";


const PORT = Number(process.env.PORT ?? 3000);

const MAX_FILE_SIZE = 50 * 1024 * 1024;

const DOCX_MIME =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

const UPLOAD_TTL =
    15 * 60 * 1000;

const PROCESS_TTL =
    10 * 60 * 1000;

const DOWNLOAD_TTL =
    15 * 60 * 1000;


function responderJson(
    res: import("node:http").ServerResponse,
    status: number,
    body: unknown
) {

    res.writeHead(status, {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store"
    });

    res.end(JSON.stringify(body));
}


async function lerJson(
    req: import("node:http").IncomingMessage
): Promise<Record<string, unknown>> {

    let body = "";

    for await (const chunk of req) {
        body += chunk.toString();
    }

    if (!body.trim()) {
        return {};
    }

    return JSON.parse(body);
}


function sessaoValida(
    sessao: unknown
): sessao is string {

    if (typeof sessao !== "string") {
        return false;
    }

    return /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
        .test(sessao);
}


function caminhosDaSessao(
    sessao: string
) {

    return {
        template:
            `revora/${sessao}/template.docx`,

        documento:
            `revora/${sessao}/documento.docx`,

        resultado:
            `revora/${sessao}/revisado.docx`
    };
}


async function criarUrl(
    pathname: string,
    operation: "put" | "get" | "delete",
    ttl: number
) {

    const token =
        await issueSignedToken({
            pathname,
            operations: [operation],
            validUntil:
                Date.now() + ttl
        });


    if (operation === "put") {

        return presignUrl(
            token,
            {
                pathname,
                operation,
                access: "private",

                allowedContentTypes: [
                    DOCX_MIME
                ],

                maximumSizeInBytes:
                    MAX_FILE_SIZE,

                validUntil:
                    Date.now() + ttl
            }
        );
    }


    return presignUrl(
        token,
        {
            pathname,
            operation,
            access: "private",

            validUntil:
                Date.now() + ttl
        }
    );
}


async function apagarArquivo(
    pathname: string
) {

    const { presignedUrl } =
        await criarUrl(
            pathname,
            "delete",
            60 * 1000
        );


    const resposta =
        await fetch(
            presignedUrl,
            {
                method: "DELETE"
            }
        );


    if (
        !resposta.ok &&
        resposta.status !== 404
    ) {

        throw new Error(
            `Falha ao excluir ${pathname}.`
        );
    }
}


const server =
    createServer(
        async (req, res) => {

            try {

                if (
                    req.method === "GET" &&
                    req.url === "/blob/health"
                ) {

                    responderJson(
                        res,
                        200,
                        {
                            status: "ok",
                            service:
                                "revora-blob-gateway"
                        }
                    );

                    return;
                }


                if (
                    req.method === "POST" &&
                    req.url === "/blob/session"
                ) {

                    const body =
                        await lerJson(req);


                    const templateSize =
                        Number(body.templateSize);

                    const documentoSize =
                        Number(body.documentoSize);


                    if (
                        !Number.isFinite(templateSize) ||
                        !Number.isFinite(documentoSize)
                    ) {

                        responderJson(
                            res,
                            400,
                            {
                                erro:
                                    "Tamanho dos arquivos inválido."
                            }
                        );

                        return;
                    }


                    if (
                        templateSize <= 0 ||
                        documentoSize <= 0
                    ) {

                        responderJson(
                            res,
                            400,
                            {
                                erro:
                                    "Os arquivos não podem estar vazios."
                            }
                        );

                        return;
                    }


                    if (
                        templateSize > MAX_FILE_SIZE ||
                        documentoSize > MAX_FILE_SIZE
                    ) {

                        responderJson(
                            res,
                            413,
                            {
                                erro:
                                    "O limite é de 50 MB por arquivo."
                            }
                        );

                        return;
                    }


                    const sessao =
                        randomUUID();


                    const caminhos =
                        caminhosDaSessao(
                            sessao
                        );


                    const templateUpload =
                        await criarUrl(
                            caminhos.template,
                            "put",
                            UPLOAD_TTL
                        );


                    const documentoUpload =
                        await criarUrl(
                            caminhos.documento,
                            "put",
                            UPLOAD_TTL
                        );


                    responderJson(
                        res,
                        200,
                        {
                            sessao,

                            templateUploadUrl:
                                templateUpload.presignedUrl,

                            documentoUploadUrl:
                                documentoUpload.presignedUrl,

                            limiteMb:
                                50
                        }
                    );

                    return;
                }


                if (
                    req.method === "POST" &&
                    req.url ===
                        "/blob/process-access"
                ) {

                    const body =
                        await lerJson(req);


                    if (
                        !sessaoValida(
                            body.sessao
                        )
                    ) {

                        responderJson(
                            res,
                            400,
                            {
                                erro:
                                    "Sessão inválida."
                            }
                        );

                        return;
                    }


                    const caminhos =
                        caminhosDaSessao(
                            body.sessao
                        );


                    const templateRead =
                        await criarUrl(
                            caminhos.template,
                            "get",
                            PROCESS_TTL
                        );


                    const documentoRead =
                        await criarUrl(
                            caminhos.documento,
                            "get",
                            PROCESS_TTL
                        );


                    const resultadoUpload =
                        await criarUrl(
                            caminhos.resultado,
                            "put",
                            PROCESS_TTL
                        );


                    responderJson(
                        res,
                        200,
                        {
                            sessao:
                                body.sessao,

                            templateReadUrl:
                                templateRead.presignedUrl,

                            documentoReadUrl:
                                documentoRead.presignedUrl,

                            resultadoUploadUrl:
                                resultadoUpload.presignedUrl
                        }
                    );

                    return;
                }


                if (
                    req.method === "POST" &&
                    req.url ===
                        "/blob/download-access"
                ) {

                    const body =
                        await lerJson(req);


                    if (
                        !sessaoValida(
                            body.sessao
                        )
                    ) {

                        responderJson(
                            res,
                            400,
                            {
                                erro:
                                    "Sessão inválida."
                            }
                        );

                        return;
                    }


                    const caminhos =
                        caminhosDaSessao(
                            body.sessao
                        );


                    const download =
                        await criarUrl(
                            caminhos.resultado,
                            "get",
                            DOWNLOAD_TTL
                        );


                    responderJson(
                        res,
                        200,
                        {
                            downloadUrl:
                                download.presignedUrl
                        }
                    );

                    return;
                }


                if (
                    req.method === "POST" &&
                    req.url ===
                        "/blob/cleanup"
                ) {

                    const body =
                        await lerJson(req);


                    if (
                        !sessaoValida(
                            body.sessao
                        )
                    ) {

                        responderJson(
                            res,
                            400,
                            {
                                erro:
                                    "Sessão inválida."
                            }
                        );

                        return;
                    }


                    const caminhos =
                        caminhosDaSessao(
                            body.sessao
                        );


                    await Promise.all([
                        apagarArquivo(
                            caminhos.template
                        ),

                        apagarArquivo(
                            caminhos.documento
                        ),

                        apagarArquivo(
                            caminhos.resultado
                        )
                    ]);


                    responderJson(
                        res,
                        200,
                        {
                            status:
                                "arquivos removidos"
                        }
                    );

                    return;
                }


                responderJson(
                    res,
                    404,
                    {
                        erro:
                            "Rota não encontrada."
                    }
                );


            } catch (erro) {

                console.error(
                    "Erro no Blob Gateway:",
                    erro instanceof Error
                        ? erro.message
                        : "Erro desconhecido"
                );


                responderJson(
                    res,
                    500,
                    {
                        erro:
                            "Erro interno no armazenamento temporário."
                    }
                );
            }
        }
    );


server.listen(
    PORT,
    () => {

        console.log(
            `Revora Blob Gateway iniciado na porta ${PORT}`
        );
    }
);
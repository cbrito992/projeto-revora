# Revora

**Revora** é uma aplicação web para revisão de documentos `.docx`, combinando validações locais, comparação com template e análise editorial assistida por IA.

O objetivo do projeto é oferecer uma revisão rápida e visual, preservando o documento original e destacando possíveis pontos de atenção para que o revisor humano tome a decisão final.

**Aplicação:** https://projeto-revora.vercel.app

---

## Visão geral

O Revora recebe dois arquivos:

1. um **template de referência**;
2. um **documento para revisão**.

A aplicação compara o documento com o template, verifica possíveis problemas ortográficos e, quando a camada de IA está disponível, acrescenta análises editoriais relacionadas à ABNT e à naturalidade do texto.

Ao final, o usuário recebe um novo arquivo `.docx` com marcações visuais e um relatório de revisão.

---

## Funcionalidades

### Ortografia

A revisão ortográfica utiliza **Hunspell**, por meio do Apache Lucene, para identificar possíveis palavras grafadas incorretamente.

**Cor no documento:** vermelho.

### Padronização pelo template

O Revora compara características do documento com o arquivo de referência, incluindo:

- margens;
- tamanho de página;
- estrutura;
- formatação;
- estilos;
- convenções identificadas no template.

**Cor no documento:** amarelo.

### ABNT

A camada editorial por IA analisa possíveis inconsistências relacionadas a citações, referências bibliográficas, referências ausentes e apresentação bibliográfica.

**Cor no documento:** ciano.

### Humanização textual

A IA também identifica trechos que podem apresentar baixa naturalidade textual, como repetições excessivas, construções genéricas, pensamento circular, acúmulo artificial de qualificadores e linguagem mecanizada.

**Cor no documento:** magenta.

> O Revora não tenta determinar se um texto foi ou não escrito por inteligência artificial. A análise de humanização observa características de estilo e naturalidade textual.

---

## Funcionamento quando a IA está indisponível

A inteligência artificial é uma camada complementar.

Se o provedor de IA estiver indisponível, responder com erro, atingir limite de uso ou sofrer timeout, o Revora continua o processamento com:

- revisão ortográfica;
- validação pelo template;
- geração do documento revisado;
- relatório final.

Nesse caso, apenas as análises de **ABNT** e **humanização textual** podem ficar indisponíveis naquela execução.

---

## Fluxo da aplicação

```text
Usuário
  │
  ├── envia template.docx
  └── envia documento.docx
          │
          ▼
     Blob Gateway
          │
          ├── cria sessão temporária
          ├── gera URLs assinadas
          └── armazena arquivos no Vercel Blob
          │
          ▼
     Spring Boot
          │
          ├── baixa os arquivos temporários
          ├── lê e processa o DOCX com Apache POI
          ├── executa revisão ortográfica
          ├── compara documento e template
          ├── solicita análise editorial por IA
          └── gera documento revisado
          │
          ▼
     Vercel Blob
          │
          ├── recebe documento revisado
          ├── gera acesso temporário para download
          └── remove arquivos temporários
          │
          ▼
        Usuário
```

---

## Tecnologias

### Backend

- Java
- Spring Boot 4.1.1
- Spring Web MVC
- Apache POI 5.2.4
- Apache Lucene 8.11.2
- Hunspell
- Jackson 3
- Maven

### Inteligência artificial

- Google Gemini
- integração via API REST
- modelo configurável por variável de ambiente
- modelo padrão atual: `gemini-3.6-flash`

### Frontend

- HTML5
- CSS3
- JavaScript
- Fetch API

### Armazenamento e deploy

- Vercel
- Vercel Blob
- Node.js
- TypeScript
- `@vercel/blob`
- container para a aplicação Spring Boot

---

## Arquitetura

O projeto é dividido em dois serviços principais.

### `app/`

Aplicação principal em Spring Boot, responsável por servir o frontend, receber a solicitação de revisão, processar os arquivos, executar as validações, chamar o provedor de IA e gerar o documento revisado.

### `blob-gateway/`

Serviço em TypeScript responsável pela comunicação segura com o Vercel Blob, incluindo criação de sessões, URLs assinadas, download, upload do resultado e limpeza dos arquivos temporários.

O roteamento entre os serviços é definido no arquivo `vercel.json`.

---

## Estrutura simplificada

```text
projeto-revora/
│
├── app/
│   ├── Dockerfile.vercel
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   └── src/
│       └── main/
│           ├── java/
│           │   └── br/com/carlos/revora/
│           │       ├── controller/
│           │       ├── dto/
│           │       ├── model/
│           │       └── service/
│           └── resources/
│               ├── application.properties
│               ├── regras-editoriais/
│               └── static/
│                   ├── index.html
│                   ├── style.css
│                   └── script.js
│
├── blob-gateway/
│   ├── package.json
│   ├── server.ts
│   └── tsconfig.json
│
├── vercel.json
└── README.md
```

---

## Principais componentes Java

### `DocumentoController`

Expõe a rota principal:

```http
POST /api/revisar
```

### `RevisaoService`

Coordena download dos arquivos, processamento e envio do documento revisado.

### `DocumentoService`

Executa a lógica principal de revisão, incluindo leitura do DOCX, ortografia, comparação com template, aplicação das marcações, análise editorial e relatório.

### `GeminiAnaliseEditorialIaService`

Integra o Revora à API Gemini.

### `ArquivoRemotoService`

Controla o acesso seguro do backend aos arquivos temporários armazenados no Vercel Blob.

### `GuiaEditorialService`

Carrega as regras editoriais usadas pela IA.

---

## Rotas do Blob Gateway

```text
POST /blob/session
POST /blob/process-access
POST /blob/download-access
POST /blob/cleanup
GET  /blob/health
```

---

## Variáveis de ambiente

```env
REVORA_IA_ENABLED=true
REVORA_IA_PROVIDER=gemini

GEMINI_API_KEY=sua_chave
GEMINI_MODEL=gemini-3.6-flash
GEMINI_TIMEOUT_SECONDS=90
```

O Vercel Blob também precisa estar configurado no projeto conforme a integração utilizada pelo `@vercel/blob`.

> Nunca publique chaves reais de API no GitHub.

---

## Executando localmente

### Requisitos

- Git
- Java
- Maven ou Maven Wrapper
- Node.js
- npm

### Backend no Windows

```cmd
cd app
mvnw.cmd clean test
mvnw.cmd spring-boot:run
```

Por padrão, fora da Vercel:

```text
http://localhost:8080
```

### Gateway TypeScript

```cmd
cd blob-gateway
npm install
npx tsc --noEmit
```

> Executar apenas o Spring Boot localmente não reproduz todo o fluxo do Vercel Blob, porque as rotas `/blob/*` são encaminhadas em produção pelo `vercel.json`.

---

## Deploy na Vercel

O `vercel.json` define dois serviços:

```text
revora → app/
blob   → blob-gateway/
```

As rotas `/blob/*` são encaminhadas ao gateway. As demais seguem para a aplicação Spring Boot.

Com o repositório conectado à Vercel, um `git push` na branch de produção inicia um novo deploy automaticamente.

---

## Segurança

O Revora utiliza URLs assinadas e temporárias para evitar exposição direta dos arquivos.

O fluxo procura garantir que:

- os arquivos sejam temporários;
- o backend aceite apenas origens autorizadas;
- as URLs tenham escopo e validade limitados;
- redirects externos não sejam seguidos pelo backend Java;
- os arquivos da sessão sejam removidos ao final do processamento.

URLs assinadas e chaves de API não devem ser registradas em logs públicos.

---

## Limitações atuais

O Revora está em fase de MVP.

- não substitui revisão humana;
- Hunspell atua como verificador ortográfico, não como revisor gramatical completo;
- análises ABNT por IA devem ser tratadas como sugestões;
- humanização textual não é detector de autoria por IA;
- análises editoriais dependem da disponibilidade do provedor de IA;
- documentos muito complexos podem exigir refinamentos adicionais.

---

## Próximas melhorias

- fallback entre provedores de IA;
- retry automático para erros temporários;
- refinamento da prioridade das cores;
- ampliação das regras ABNT;
- melhorias no relatório final;
- mais testes automatizados;
- refinamentos de interface e experiência de uso.

---

## Status do projeto

**MVP funcional.**

```text
upload
→ armazenamento temporário
→ revisão
→ geração do DOCX
→ download
→ limpeza dos temporários
```

---

## Autor

**Carlos A. B. Oliveira**

Projeto pessoal desenvolvido para estudo e experimentação com processamento de documentos, validação de padrões e inteligência artificial aplicada à revisão textual.

---

## Licença

Este projeto é disponibilizado sob a
**PolyForm Noncommercial License 1.0.0**.

O uso, estudo, modificação e distribuição são permitidos
para fins não comerciais.

O uso comercial não é permitido.

Consulte o arquivo [LICENSE](LICENSE) para os termos aplicáveis.

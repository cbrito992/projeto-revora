# Revora local para Windows

Versão portátil para Windows 10/11 x64. A interface abre no navegador padrão; o
processamento DOCX acontece no Java local. Não usa Node.js, Vercel ou Vercel Blob.
O serviço web de produção continua disponível no perfil padrão; o perfil `local`
ativa a interface e API desktop e desativa a rota de processamento por URLs remotas.

## Usar o pacote

1. Baixe `Revora-0.2.0-windows-x64.zip` e o arquivo `.sha256` da distribuição.
2. Confira a integridade no PowerShell:
   `Get-FileHash .\Revora-0.2.0-windows-x64.zip -Algorithm SHA256`.
   Compare com o hash publicado pelo responsável pela distribuição.
3. Extraia **todo** o ZIP para uma pasta do seu usuário, por exemplo
   `Documentos\Revora`. Não execute de dentro do ZIP nem da prévia do Google Drive.
4. Abra `Iniciar-Revora.cmd`. O Java já está incluído: não precisa instalar Java,
   Maven, Node ou ter conta na Vercel. Aguarde o navegador abrir.
5. Selecione o template e o documento `.docx` (até 50 MB cada) e clique em
   **Revisar e baixar DOCX**. O navegador salva `documento_revisado.docx` na pasta
   de downloads ou pergunta onde salvar, conforme suas preferências.
6. Confira as marcações e o relatório no final do arquivo. Os originais não são
   alterados. O link permite repetir o download até fechar/recarregar a página.
7. Para encerrar, volte à janela do inicializador e pressione Enter. Fechar apenas
   a aba não encerra o Java. Não encerre durante uma revisão.

A porta `18765` precisa estar livre. O servidor escuta somente em `127.0.0.1`;
não crie regras de firewall para acesso externo. Se houver conflito, encerre a
outra instância. Logs de inicialização ficam em `%LOCALAPPDATA%\Revora\logs`.
A interface e os documentos não são enviados para um servidor de armazenamento.
Uploads multipart podem passar pela pasta temporária do sistema durante a
requisição; o contêiner os remove ao final. O resultado fica em memória até o
download. Use disco protegido e as políticas locais para documentos sensíveis.

## Configuração pessoal de IA

A análise por IA vem **desativada**. Ortografia Hunspell, comparação com template,
marcações e geração do relatório/DOCX funcionam sem internet.

Para ABNT e naturalidade textual, marque **Usar análise editorial por IA**,
selecione Gemini, Groq ou OpenRouter, informe o identificador de modelo disponível
na sua conta e cole **sua própria chave**. Clique em **Testar conexão**: é feita uma
requisição real curta ao modelo escolhido; ela pode consumir quota ou gerar custo.
O teste confirma acesso e resposta textual. A revisão ainda pode falhar por limites
de contexto, quota ou falta de suporte a JSON do modelo escolhido. Groq e
OpenRouter usam a API compatível com chat completions e `response_format=json_object`;
selecione um modelo que aceite esse modo. Gemini usa saída JSON com schema.

Os identificadores sugeridos são editáveis, não uma garantia de disponibilidade.
A chave permanece apenas na memória da página e das requisições locais; não é
salva em arquivo, cookie, localStorage, log ou instalador. Cada revisor deve inserir
a própria chave em cada sessão. Apenas provedor e modelo são lembrados no perfil
do navegador. **Limpar chave** remove o campo; recarregar/fechar a página também.
Não compartilhe capturas que mostrem uma chave nem inclua arquivos `.env` no pacote.

Com IA ativa, trechos do documento e as diretrizes editoriais são enviados por
HTTPS ao provedor selecionado. Consulte as políticas de retenção e os preços da
sua conta antes de enviar documentos confidenciais. Destinos usados:

| Provedor | Endpoint |
| --- | --- |
| Gemini | `generativelanguage.googleapis.com` |
| Groq | `api.groq.com` |
| OpenRouter | `openrouter.ai` |

O teste informa falha para chave inválida, modelo sem acesso, falta de saldo,
timeout ou erro de rede sem expor o corpo retornado pelo provedor. Uma falha de IA
durante a revisão mantém ortografia e template e acrescenta um aviso no relatório.
A IA não determina autoria: naturalidade textual continua sendo uma sugestão editorial.

## Gerar o ZIP no Windows

Pré-requisitos da máquina de build: Git, PowerShell 5.1+ e **JDK 17 completo para
Windows x64** (por exemplo Eclipse Temurin). Internet para Maven Central. Use uma
cópia limpa da branch `feat/windows-local`; não coloque chaves nela.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17...'
.\packaging\windows\Build-Windows.ps1 -JdkHome $env:JAVA_HOME
```

O script executa `mvnw.cmd clean verify` (sem pular testes), gera um runtime Windows
com `jlink`, inclui somente JAR, runtime, inicializadores, licença e instruções,
e faz um smoke test HTTP de interface e revisão DOCX/Hunspell usando **o runtime empacotado**. Somente depois gera
`dist\Revora-0.2.0-windows-x64.zip` e seu `.sha256`. O staging é temporário; arquivos
do usuário, fontes, logs, `.env`, chaves e configurações pessoais não são copiados.
`dist/` é ignorado pelo Git. Use `-Version 0.2.1` para a próxima versão;
um ZIP existente não é sobrescrito. O pacote é portátil, não um MSI/EXE assinado.
O runtime inclui avisos e licenças do Java; preserve-os na distribuição.

O workflow `.github/workflows/windows.yml` oferece o mesmo build em
`windows-latest`, por pull request, push nesta branch ou execução manual.
Baixe o artifact `Revora-windows-x64`, extraia o invólucro do artifact e distribua
o ZIP do Revora e o `.sha256` que estão dentro dele.

Antes de distribuir, valide em Windows 10/11 x64 sem Java instalado: inicialização
por duplo clique, abertura do navegador, revisão de um DOCX real com template,
download, encerramento e reabertura; valide também uma chave pessoal de cada
provedor. Esses testes manuais complementam os testes automatizados de integração
DOCX/HTTP e de contratos dos provedores. Uma máquina Linux não valida execução nativa
Windows nem abertura do navegador Windows.

## Distribuir pelo Google Drive

1. Gere e valide o pacote no Windows ou obtenha o artifact de um CI bem-sucedido.
2. Crie uma pasta de versão no Drive, por exemplo `Revora/0.2.0/`.
3. Envie somente o ZIP final, `.sha256` e estas instruções. Não envie o checkout,
   pastas de usuário ou chaves. Não edite o ZIP depois de calcular o hash.
4. Defina acesso de visualização/download para os revisores desejados. Teste o
   download com uma conta de revisor e confira novamente o SHA256 após baixar.
5. Compartilhe o link da pasta e instrua a extrair o ZIP antes de abrir.

O build não publica automaticamente no Drive. A conta/pasta de destino e a
publicação são administradas pelo distribuidor. Não há chaves ou credenciais
compartilhadas no pacote. A licença PolyForm Noncommercial do projeto continua
aplicável; uso comercial requer autorização do titular.

## Desenvolvimento e testes

Linux/macOS, JDK completo 17:

```sh
cd app
bash mvnw -B -ntp verify
bash mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Abra a interface em `http://127.0.0.1:18765/local.html`. Para mudar a porta em
desenvolvimento, defina `REVORA_PORT`. O inicializador Windows fixa a porta padrão.
O perfil local exige host loopback, origem correspondente e cabeçalho
`X-Revora-Local: 1` nos POSTs, enviado pela interface. Não configure CORS amplo.
As configurações de IA são passadas por requisição: não alteram o bean global,
não são persistidas e não são compartilhadas entre revisões. O processamento do
motor é serializado para proteger o estado do Hunspell.

Os testes cobrem upload multipart real, DOCX válido e inválido, geração de relatório,
marcações, aplicação de sugestões editoriais, fallback sem IA, bloqueio de origens
externas e contratos de autenticação/resposta dos três provedores com HTTP simulado.
Testes com serviços externos não fazem parte do build e exigem chaves pessoais;
não se deve armazená-las em fixtures ou secrets de distribuição.

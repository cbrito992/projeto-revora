'use strict';
const $ = id => document.getElementById(id);
const defaults = {gemini: 'gemini-2.5-flash', groq: 'llama-3.3-70b-versatile', openrouter: 'google/gemini-2.5-flash'};
let resultUrl;
try {
    const prefs = JSON.parse(localStorage.getItem('revora.preferences') || '{}');
    if (Object.hasOwn(defaults, prefs.provider)) $('provider').value = prefs.provider;
    $('model').value = typeof prefs.model === 'string' ? prefs.model : defaults[$('provider').value];
} catch { $('model').value = defaults.gemini; }
function settings() {
    return {enabled: $('enabled').checked, provider: $('provider').value,
        model: $('model').value.trim(), apiKey: $('apiKey').value.trim()};
}
function preferences() {
    try { localStorage.setItem('revora.preferences', JSON.stringify({provider: $('provider').value, model: $('model').value})); } catch { /* Private browsing may disable storage. */ }
}
$('provider').addEventListener('change', () => { $('model').value = defaults[$('provider').value]; preferences(); $('aiStatus').textContent = ''; });
$('model').addEventListener('change', preferences);
$('clear').addEventListener('click', () => { $('apiKey').value = ''; $('aiStatus').textContent = 'Chave removida desta sessão.'; });
function status(id, message, error = false) { $(id).textContent = message; $(id).classList.toggle('error', error); }
async function post(url, body, json = false, extraHeaders = {}) {
    const headers = {'X-Revora-Local': '1', ...extraHeaders};
    if (json) headers['Content-Type'] = 'application/json';
    const response = await fetch(url, {method: 'POST', headers, body: json ? JSON.stringify(body) : body});
    if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.erro || `Não foi possível concluir a operação (HTTP ${response.status}).`);
    }
    return response;
}
$('test').addEventListener('click', async () => {
    $('test').disabled = true;
    status('aiStatus', 'Testando o modelo selecionado…');
    try {
        const response = await post('/api/local/ia/testar', settings(), true);
        status('aiStatus', (await response.json()).mensagem);
    } catch (error) { status('aiStatus', error.message, true); }
    finally { $('test').disabled = false; }
});
$('review').addEventListener('submit', async event => {
    event.preventDefault();
    const form = new FormData();
    for (const name of ['template', 'documento']) {
        const file = $(name).files[0];
        if (!file || !/\.docx$/i.test(file.name) || file.size === 0 || file.size > 50 * 1024 * 1024) {
            status('reviewStatus', 'Selecione dois arquivos DOCX não vazios de até 50 MB.', true); return;
        }
        form.append(name, file);
    }
    $('submit').disabled = true;
    $('download').hidden = true;
    status('reviewStatus', 'Revisando… A análise por IA pode levar alguns minutos. Mantenha o inicializador aberto.');
    try {
        const response = await post('/api/local/revisar', form, false, {'X-Revora-IA': JSON.stringify(settings())});
        const blob = await response.blob();
        if (resultUrl) URL.revokeObjectURL(resultUrl);
        resultUrl = URL.createObjectURL(blob);
        $('download').href = resultUrl;
        $('download').download = 'documento_revisado.docx';
        $('download').hidden = false;
        $('download').click();
        status('reviewStatus', 'Revisão concluída. Consulte o relatório no final do DOCX, inclusive avisos sobre indisponibilidade da IA.');
    } catch (error) { status('reviewStatus', error.message, true); }
    finally { $('submit').disabled = false; }
});
window.addEventListener('pagehide', () => { $('apiKey').value = ''; if (resultUrl) URL.revokeObjectURL(resultUrl); });

const fs = require('node:fs');
const path = require('node:path');
const [modulePath, collectionFile, environmentFile, reportDir, baseUrl, database] = process.argv.slice(2);
const newman = require(modulePath);
const collection = JSON.parse(fs.readFileSync(collectionFile,'utf8'));
const environment = JSON.parse(fs.readFileSync(environmentFile,'utf8'));
environment.values.find(v=>v.key==='baseUrl').value=baseUrl;
fs.mkdirSync(reportDir,{recursive:true});
let count=0;
async function main(){
const mailbox=await require('./caixa-email-teste.cjs').start();
environment.values.push({key:'mailboxUrl',value:mailbox.url,enabled:true},{key:'mailboxKey',value:mailbox.key,enabled:true});
const run=newman.run({collection,environment,bail:true,timeoutRequest:10000,timeoutScript:20000,reporters:[]},(error,summary)=>{
    mailbox.close();
    const executions=(summary?.run?.executions||[]).map(e=>({
        nome:e.item.name,statusHTTP:e.response?.code||null,
        erro:e.requestError?.message||null,
        verificacoes:(e.assertions||[]).map(a=>({nome:a.assertion,aprovado:!a.error,erro:a.error?.message||null}))
    }));
    const failures=summary?.run?.failures||[];
    const report={data:new Date().toISOString(),banco:database,api:baseUrl,
        requisicoes:summary?.run?.stats?.requests?.total||0,
        verificacoes:summary?.run?.stats?.assertions?.total||0,
        falhas:failures.length+(error?1:0),erro:error?.message||null,
        versaoNewman:require(path.join(modulePath,'package.json')).version,
        observacao:'Somente dados fictícios e banco de teste. Relatório sem tokens e sem senha do PostgreSQL.',execucoes:executions};
    fs.writeFileSync(path.join(reportDir,'Resultado-Postman.json'),JSON.stringify(report,null,2));
    const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const rows=executions.map(e=>`<tr><td>${esc(e.nome)}</td><td>${e.statusHTTP??'—'}</td><td>${e.verificacoes.filter(a=>a.aprovado).length}/${e.verificacoes.length}</td><td>${esc(e.erro||e.verificacoes.filter(a=>!a.aprovado).map(a=>a.erro).join('; ')||'Aprovado')}</td></tr>`).join('');
    const html=`<!doctype html><html lang="pt-BR"><meta charset="utf-8"><title>Aquarius Circus — Testes Postman</title><style>body{font:16px system-ui,sans-serif;color:#172c3b;background:#f5f8fa;max-width:1100px;margin:40px auto;padding:0 24px}h1{color:#164e63}section{padding:20px;background:white;border-radius:12px;margin:20px 0}.resultado{font-size:24px;font-weight:700;color:${report.falhas?'#b42318':'#067647'}}table{border-collapse:collapse;width:100%;background:white}td,th{border:1px solid #d9e1e6;padding:10px;text-align:left}th{background:#dceef3}small{color:#526674}</style><h1>Aquarius Circus — testes de integração</h1><section><div class="resultado">${report.falhas?'Falhas encontradas':'Todos os testes aprovados'}</div><p>${report.requisicoes} requisições · ${report.verificacoes} verificações · ${report.falhas} falhas</p><p>Banco PostgreSQL: <strong>${esc(database)}</strong><br>API: ${esc(baseUrl)}<br>Execução: ${esc(report.data)}<br>Newman: ${esc(report.versaoNewman)}</p><small>${esc(report.observacao)} Os resultados comprovam somente os cenários executados, e não uma homologação completa de produção.</small></section><table><thead><tr><th>Cenário</th><th>HTTP</th><th>Verificações aprovadas</th><th>Resultado</th></tr></thead><tbody>${rows}</tbody></table></html>`;
    fs.writeFileSync(path.join(reportDir,'Resultado-Postman.html'),html);
    console.log(JSON.stringify({requisicoes:report.requisicoes,verificacoes:report.verificacoes,falhas:report.falhas}));
    if(error||failures.length){
        for(const f of failures.slice(0,8)) console.error(`${f.source?.name||f.at}: ${f.error?.message||'Falha'}`);
        process.exitCode=1;
    }
});
run.on('request',()=>{if(++count%25===0)console.log(`Requisições processadas: ${count}`);});
}
main().catch(e=>{console.error('Não foi possível iniciar o executor/caixa de teste: '+e.message);process.exitCode=1;});

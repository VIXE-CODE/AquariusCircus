// Caixa SMTP/HTTP temporária, somente em loopback e para destinatários fictícios.
const net=require('node:net');
const http=require('node:http');
const crypto=require('node:crypto');
function decode(data){
    const parts=data.split(/\r?\n\r?\n/);const headers=parts.shift()||'';let body=parts.join('\r\n\r\n');
    if(/Content-Transfer-Encoding:\s*base64/i.test(headers))return Buffer.from(body.replace(/\s/g,''),'base64').toString('utf8');
    if(/Content-Transfer-Encoding:\s*quoted-printable/i.test(headers)){
        body=body.replace(/=\r?\n/g,'');
        const bytes=[];
        for(let i=0;i<body.length;i++){
            if(body[i]==='='&&/^[0-9a-f]{2}$/i.test(body.slice(i+1,i+3))){bytes.push(parseInt(body.slice(i+1,i+3),16));i+=2;}
            else bytes.push(...Buffer.from(body[i],'utf8'));
        }
        return Buffer.from(bytes).toString('utf8');
    }
    return body;
}
exports.start=async function(){
    const messages=[];const key=crypto.randomBytes(24).toString('hex');const sockets=new Set();
    const smtp=net.createServer(socket=>{
        sockets.add(socket);socket.on('close',()=>sockets.delete(socket));socket.on('error',()=>{});
        socket.setEncoding('utf8');let buffer='',receivers=[],lines=[],dataMode=false;
        socket.write('220 localhost Aquarius SMTP de teste\r\n');
        socket.on('data',chunk=>{
            buffer+=chunk;let pos;
            while((pos=buffer.indexOf('\n'))>=0){
                let line=buffer.slice(0,pos).replace(/\r$/,'');buffer=buffer.slice(pos+1);
                if(dataMode){
                    if(line==='.'){
                        const body=decode(lines.join('\r\n'));
                        messages.push({destinatarios:[...receivers],token:body.match(/[?&]token=([A-Za-z0-9_-]{43})/)?.[1]||null});
                        dataMode=false;lines=[];receivers=[];socket.write('250 Mensagem ficticia recebida\r\n');
                    }else lines.push(line.startsWith('..')?line.slice(1):line);
                    continue;
                }
                if(/^(EHLO|HELO) /i.test(line))socket.write('250-localhost\r\n250-8BITMIME\r\n250 SIZE 1048576\r\n');
                else if(/^MAIL FROM:/i.test(line)){receivers=[];socket.write('250 OK\r\n');}
                else if(/^RCPT TO:/i.test(line)){
                    const address=line.match(/<([^>]+)>/)?.[1]?.toLowerCase();
                    if(address?.endsWith('@aquarius.test')){receivers.push(address);socket.write('250 OK\r\n');}
                    else socket.write('550 Somente destinatarios ficticios aquarius.test\r\n');
                }else if(/^DATA$/i.test(line)){
                    if(!receivers.length)socket.write('503 Sem destinatario\r\n');
                    else{dataMode=true;lines=[];socket.write('354 Termine com ponto\r\n');}
                }else if(/^QUIT$/i.test(line)){socket.end('221 Adeus\r\n');}
                else if(/^RSET$/i.test(line)){receivers=[];lines=[];dataMode=false;socket.write('250 OK\r\n');}
                else socket.write('250 OK\r\n');
            }
        });
    });
    const mailbox=http.createServer((req,res)=>{
        res.setHeader('Content-Type','application/json');
        if(req.headers['x-test-mailbox-key']!==key){res.statusCode=403;res.end('{}');return;}
        const url=new URL(req.url,'http://127.0.0.1:8091');
        if(req.method!=='GET'||url.pathname!=='/messages'){res.statusCode=404;res.end('{}');return;}
        const recipient=(url.searchParams.get('recipient')||'').toLowerCase();
        if(!recipient.endsWith('@aquarius.test')){res.statusCode=400;res.end('{}');return;}
        res.end(JSON.stringify(messages.filter(m=>m.destinatarios.includes(recipient))));
    });
    const listen=(server,port)=>new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,'127.0.0.1',resolve);});
    try{await listen(smtp,2526);await listen(mailbox,8091);}
    catch(e){smtp.close();mailbox.close();throw e;}
    return {key,url:'http://127.0.0.1:8091',close(){for(const socket of sockets)socket.destroy();smtp.close();mailbox.close();}};
};

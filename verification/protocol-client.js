const net=require('net'),zlib=require('zlib');
function vi(n){const b=[];do{let x=n&127;n>>>=7;if(n)x|=128;b.push(x);}while(n);return Buffer.from(b);}
function read(b,o=0){let n=0,s=0,i=o;for(;i<b.length;i++){let v=b[i];n|=(v&127)<<s;if(!(v&128))return [n,i+1];s+=7;if(s>35)throw Error('bad varint');}return null;}
function str(s){const b=Buffer.from(s);return Buffer.concat([vi(b.length),b]);}
const clients=[];
function connect(name){let state='login',compression=-1,buffer=Buffer.alloc(0),done=false;const sock=net.connect(25582,'127.0.0.1');clients.push(sock);
function send(id,data=Buffer.alloc(0)){if(sock.destroyed)return;let payload=Buffer.concat([vi(id),data]);if(compression>=0)payload=Buffer.concat([vi(0),payload]);sock.write(Buffer.concat([vi(payload.length),payload]));}
sock.on('connect',()=>{send(0,Buffer.concat([vi(776),str('127.0.0.1'),Buffer.from([0x63,0xee]),vi(2)]));send(0,Buffer.concat([str(name),Buffer.alloc(16)]));});
function packet(buf){let h=read(buf);if(!h)return;const id=h[0],body=buf.subarray(h[1]);
 if(state==='login'){if(id===3)compression=read(body)[0];else if(id===2){send(3);state='config';}else if(id===0)console.log(name,'LOGIN_DISCONNECT',body.toString('utf8').slice(0,300));}
 else if(state==='config'){if(id===14)send(7,vi(0));else if(id===4)send(4,body);else if(id===5)send(5,body);else if(id===3){send(3);state='play';setTimeout(()=>send(0x2c),1000);console.log(name,'PLAY_READY');}else if(id===2)console.log(name,'CONFIG_DISCONNECT',body.toString('utf8').slice(0,300));}
 else if(state==='play'){if(id===0x2c&&body.length===8)send(0x1c,body);else if(id===0x48){let t=read(body);if(t){send(0,vi(t[0]));setTimeout(()=>send(0x2c),20);}}else if(id===0x0b){const f=Buffer.alloc(4);f.writeFloatBE(10);send(0x0b,f);}else if(id===0x44){setTimeout(()=>send(0x0c,vi(0)),500);}else if(id===0x1d)console.log(name,'PLAY_DISCONNECT',body.toString('utf8').slice(0,300));}
}
sock.on('data',chunk=>{buffer=Buffer.concat([buffer,chunk]);try{while(buffer.length){const h=read(buffer);if(!h||buffer.length<h[1]+h[0])break;let payload=buffer.subarray(h[1],h[1]+h[0]);buffer=buffer.subarray(h[1]+h[0]);if(compression>=0){const u=read(payload);payload=u[0]===0?payload.subarray(u[1]):zlib.inflateSync(payload.subarray(u[1]));}packet(payload);}}catch(e){console.log(name,'CLIENT_ERROR',e.message);sock.destroy();}});
sock.on('error',e=>console.log(name,'SOCKET_ERROR',e.message));sock.on('close',()=>console.log(name,'CLOSED'));
}
for(const [i,name] of process.argv.slice(2).entries())setTimeout(()=>connect(name),i*300);
process.stdin.setEncoding('utf8');process.stdin.on('data',s=>{for(const line of s.trim().split('\n')){if(line.startsWith('connect '))connect(line.slice(8));if(line==='exit'){for(const s of clients)s.destroy();process.exit(0);}}});setInterval(()=>{},1000);

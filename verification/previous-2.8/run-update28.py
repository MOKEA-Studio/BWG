import os,subprocess,pathlib,urllib.parse,time,shutil,sys,json,socket
b=pathlib.Path(__file__).resolve().parent.parent;r=b/(sys.argv[2] if len(sys.argv)>2 else 'integration28');java=next((b/'tools').glob('amazon-corretto-25*/bin/java'));node=shutil.which('node');proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
args=[str(java),'-Xms256M','-Xmx1200M','-XX:ActiveProcessorCount=4','-Dsun.net.client.defaultConnectTimeout=5000','-Dsun.net.client.defaultReadTimeout=5000','-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','-Dterminal.jline=false','-Dterminal.ansi=false']
if os.environ.get('SMP_TEST_PROXY_PORT'):args += ['-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort='+os.environ['SMP_TEST_PROXY_PORT'],'-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort='+os.environ['SMP_TEST_PROXY_PORT']]
elif proxy.hostname:args += [f'-Dhttps.proxyHost={proxy.hostname}',f'-Dhttps.proxyPort={proxy.port or 80}',f'-Dhttp.proxyHost={proxy.hostname}',f'-Dhttp.proxyPort={proxy.port or 80}']
def waitfor(test,timeout,proc,desc):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  if(r/'FAILED').exists():raise RuntimeError((r/'FAILED').read_text())
  if test():return
  if proc.poll() is not None:raise RuntimeError('server exited '+str(proc.returncode))
  time.sleep(.2)
 raise TimeoutError(desc)
def stage(label,names,command,marker):
 print('START',label,flush=True)
 with(r/f'{label}.log').open('w') as log,(r/f'clients-{label}.log').open('w') as cl:
  relay=None
  if os.environ.get('SMP_TEST_PROXY_PORT'):
   relaylog=(r/'proxy-prime.log').open('w');relay=subprocess.Popen([sys.executable,str(b/'tools/primed-proxy28.py')],cwd=b,stdout=relaylog,stderr=subprocess.STDOUT,text=True)
   waitfor(lambda:'READY' in(r/'proxy-prime.log').read_text(errors='replace'),65,relay,'public discovery relay ready')
  server=subprocess.Popen(args+['-jar','paper.jar','--nogui'],cwd=r,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True);clients=None
  def send(cmd):server.stdin.write(cmd+'\n');server.stdin.flush()
  def probe(cmd,mark):send('probe '+cmd);waitfor(lambda:(r/mark).exists(),100,server,mark);print('PASS',cmd,flush=True)
  try:
   waitfor(lambda:'Done (' in(r/f'{label}.log').read_text(errors='replace'),150,server,'server ready')
   if label=='old26':waitfor(lambda:(r/'plugins/BlackWikiSMP/spawn-build.yml').exists() and 'stage: DONE' in(r/'plugins/BlackWikiSMP/spawn-build.yml').read_text(),60,server,'garden complete')
   clients=subprocess.Popen([node,str(b/'tools/protocol-client28.js'),*names],cwd=r,stdin=subprocess.PIPE,stdout=cl,stderr=subprocess.STDOUT,text=True)
   waitfor(lambda:all(n+' joined the game' in(r/f'{label}.log').read_text(errors='replace') for n in names),40,server,'players joined');time.sleep(1);probe(command,marker)
   if label=='new28':
    with socket.create_connection(('127.0.0.1',19132),timeout=5) as s:s.sendall(b'GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n');response=s.recv(1024);assert response.startswith(b'HTTP/'),response
    (r/'bedrock-network.txt').write_text('PASS builtin NetherNet TCP signaling answered an HTTP request on 19132\n');print('PASS Bedrock TCP signaling',flush=True)
    clients.stdin.write('connect Fresh28\n');clients.stdin.flush();waitfor(lambda:'Fresh28 joined the game' in(r/f'{label}.log').read_text(errors='replace'),20,server,'newcomer join');probe('welcome','WELCOME_READY')
   send('stop');server.wait(timeout=80)
   if server.returncode!=0:raise RuntimeError('server failed to stop '+str(server.returncode))
  finally:
   if clients is not None and clients.poll() is None:clients.stdin.write('exit\n');clients.stdin.flush();clients.wait(timeout=10)
   if server.poll() is None:
    try:send('stop');server.wait(timeout=40)
    except subprocess.TimeoutExpired:server.terminate();server.wait(timeout=15)
   if relay is not None:
    relay.terminate();relay.wait(timeout=10);relaylog.close()
mode=sys.argv[1]
if mode=='old':stage('old26',['Alice','Bob'],'old','OLD_READY')
if mode=='new':
 for f in(r/'plugins').glob('BlackWikiSMP-*.jar'):f.unlink()
 shutil.copy2(b/'project28/target/BlackWikiSMP-2.8.jar',r/'plugins/BlackWikiSMP-2.8.jar')
 stage('new28',['Alice','Bob'],'new','NEW_READY')
if mode=='restart':stage('restart28',['Alice','Bob'],'restart','ALL_READY')
print('DONE',mode,flush=True)

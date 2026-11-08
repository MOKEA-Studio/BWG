import os,subprocess,pathlib,urllib.parse,time,shutil,sys
b=pathlib.Path(__file__).resolve().parent.parent;r=b/(sys.argv[2] if len(sys.argv)>2 else 'integration26');java=next((b/'tools').glob('amazon-corretto-25*/bin/java'));node=shutil.which('node');proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
args=[str(java),'-Xms256M','-Xmx1024M','-XX:ActiveProcessorCount=4','-Dsun.net.client.defaultConnectTimeout=5000','-Dsun.net.client.defaultReadTimeout=5000','-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','-Dterminal.jline=false','-Dterminal.ansi=false']
if proxy.hostname:args += [f'-Dhttps.proxyHost={proxy.hostname}',f'-Dhttps.proxyPort={proxy.port or 80}',f'-Dhttp.proxyHost={proxy.hostname}',f'-Dhttp.proxyPort={proxy.port or 80}']
def waitfor(test,timeout,proc,desc):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  if(r/'FAILED').exists():raise RuntimeError((r/'FAILED').read_text())
  if test():return
  if proc.poll() is not None:raise RuntimeError('server exited '+str(proc.returncode))
  time.sleep(.15)
 raise TimeoutError(desc)
def stage(label,names,commands):
 print('START',label,flush=True)
 with(r/f'{label}.log').open('w') as log,(r/f'clients-{label}.log').open('w') as cl:
  server=subprocess.Popen(args+['-jar','paper.jar','--nogui'],cwd=r,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True);clients=None
  def send(command):server.stdin.write(command+'\n');server.stdin.flush()
  def probe(command,marker):
   send('probe '+command);waitfor(lambda:(r/marker).exists(),90,server,marker);print('PASS',command,flush=True)
  try:
   waitfor(lambda:'Done (' in (r/f'{label}.log').read_text(errors='replace'),150,server,'server ready')
   if label=='old25':waitfor(lambda:(r/'plugins/BlackWikiSMP/spawn-build.yml').exists() and 'stage: DONE' in (r/'plugins/BlackWikiSMP/spawn-build.yml').read_text(),60,server,'garden complete')
   clients=subprocess.Popen([node,str(b/('tools/protocol-client26-restart.js' if r.name=='integration26-restart' else 'tools/protocol-client26.js')),*names],cwd=r,stdin=subprocess.PIPE,stdout=cl,stderr=subprocess.STDOUT,text=True)
   waitfor(lambda:all(name+' joined the game' in (r/f'{label}.log').read_text(errors='replace') for name in names),40,server,'players joined');time.sleep(1)
   for command,marker in commands:probe(command,marker)
   if label=='new26':
    clients.stdin.write('connect Fresh26\n');clients.stdin.flush();waitfor(lambda:'Fresh26 joined the game' in(r/f'{label}.log').read_text(errors='replace'),20,server,'fresh join');probe('greeting','GREETING_READY')
    clients.stdin.write('connect Second26\n');clients.stdin.flush();waitfor(lambda:'Second26 joined the game' in(r/f'{label}.log').read_text(errors='replace'),20,server,'second join');probe('second','SECOND_READY')
    # Long enough for a real ten-second deadline; no production timing is bypassed.
    start=time.monotonic()
    while time.monotonic()-start<11:time.sleep(.2)
    probe('late','LATE_READY')
   send('stop');server.wait(timeout=80)
   if server.returncode!=0:raise RuntimeError('server failed to stop '+str(server.returncode))
  finally:
   if clients is not None and clients.poll() is None:
    clients.stdin.write('exit\n');clients.stdin.flush();clients.wait(timeout=10)
   if server.poll() is None:
    try:send('stop');server.wait(timeout=40)
    except subprocess.TimeoutExpired:server.terminate();server.wait(timeout=15)

mode=sys.argv[1] if len(sys.argv)>1 else 'all'
if mode in ('old','all'):stage('old25',['Alice','Bob'],[('old','OLD_READY')])
if mode in ('new','all'):
 for p in(r/'plugins').glob('BlackWikiSMP-*.jar'):p.unlink()
 shutil.copy2(b/'project26/target/BlackWikiSMP-2.6.jar',r/'plugins/BlackWikiSMP-2.6.jar')
 stage('new26',['Alice','Bob'],[('base','BASE_READY')])
 stage('restart26',['Alice','Bob','Fresh26','Second26'],[('restart','ALL_READY')])
if mode=='restart':stage('restart26',['Alice','Bob','Fresh26','Second26'],[('restart','ALL_READY')])
print('DONE',mode,flush=True)

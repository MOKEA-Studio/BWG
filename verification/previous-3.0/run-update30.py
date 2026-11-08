#!/usr/bin/env python3
"""Local native upgrade/control test. Discord transports are mocks; Paper and persistence are real."""
import os,pathlib,subprocess,shutil,time,json,urllib.parse,zipfile,re,sys,hashlib
import yaml
b=pathlib.Path(__file__).resolve().parents[2];src=b/'project30';r=b/'integration30-certified';java=next((b/'tools').glob('amazon-corretto-25*/bin/java'));node=shutil.which('node')
args=[str(java),'-Xms192M','-Xmx768M','-XX:ActiveProcessorCount=4','-Dsun.net.client.defaultConnectTimeout=10000','-Dsun.net.client.defaultReadTimeout=10000','-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','-Dterminal.jline=false','-Dterminal.ansi=false']
p=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
if p.hostname:args += ['-Dhttps.proxyHost='+p.hostname,'-Dhttps.proxyPort='+str(p.port or 80),'-Dhttp.proxyHost='+p.hostname,'-Dhttp.proxyPort='+str(p.port or 80)]
def wait(test,seconds,proc,desc):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if (r/'FAILED').exists():raise RuntimeError((r/'FAILED').read_text())
  if test():return
  if proc and proc.poll() is not None:raise RuntimeError(desc+' process exited '+str(proc.returncode))
  time.sleep(.2)
 raise TimeoutError(desc)
def send(p,cmd):p.stdin.write(cmd+'\n');p.stdin.flush()
def text(f):return f.read_text(errors='replace') if f.exists() else ''
def edit_config(fn):
 f=r/'plugins/BlackWikiSMP/config.yml';y=yaml.safe_load(f.read_text());fn(y);f.write_text(yaml.safe_dump(y,allow_unicode=True,sort_keys=False))
def init():
 if r.exists():raise RuntimeError('choose a new integration30 folder')
 r.mkdir();(r/'plugins/BlackWikiSMP').mkdir(parents=True)
 for n in ['libraries','versions']:shutil.copytree(b/'integration'/n,r/n,copy_function=os.link)
 shutil.copytree(b/'integration/cache',r/'cache');shutil.copy2(b/'integration/paper.jar',r/'paper.jar')
 assert hashlib.sha256((r/'cache/mojang_26.2.jar').read_bytes()).hexdigest()=='cdacdfb25898de5e4b4b0e5ddcc2722f77067e46605709c2d886c000ebb63ec5'
 (r/'eula.txt').write_text('eula=true\n');(r/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25582\nonline-mode=false\nenforce-secure-profile=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\ngamemode=survival\ndifficulty=easy\nmax-tick-time=-1\npause-when-empty-seconds=-1\nallow-flight=true\nmax-players=40\n')
 with zipfile.ZipFile(src/'binary/BlackWikiSMP-2.9.jar') as z:y=yaml.safe_load(z.read('config.yml'))
 y['queue']['player-slots']=17;y['queue']['max-waiting']=11;y['discord']['bot-token']='unit-test-token-123456789';y['discord']['bot-token-env']=''
 for n in ['bedrock','backup','cleanup','monsters','discord']:y[n]['enabled']=False
 (r/'plugins/BlackWikiSMP/config.yml').write_text(yaml.safe_dump(y,allow_unicode=True,sort_keys=False));shutil.copy2(src/'binary/BlackWikiSMP-2.9.jar',r/'plugins/BlackWikiSMP.jar')
def compile_fixtures():
 cp=os.pathsep.join(str(p) for p in (r/'libraries').rglob('*.jar'));dest=r/'probe-classes';dest.mkdir(exist_ok=True);subprocess.run([str(java.with_name('javac')),'--release','25','-cp',cp,'-d',str(dest),str(src/'verification/Update30Probe.java')],check=True)
 (dest/'plugin.yml').write_text('name: Update30Probe\nversion: 1\nmain: kr.blackwiki.test.Update30Probe\napi-version: "26.2"\ndepend: [BlackWikiSMP]\ncommands:\n  probe: {}\n');subprocess.run([str(java.with_name('jar')),'cf',str(r/'plugins/Update30Probe.jar'),'-C',str(dest),'.'],check=True)
def clients(label):
 f=(r/(label+'-clients.log')).open('w');p=subprocess.Popen([node,str(src/'verification/protocol-client.js'),'Alice','Bob'],cwd=r,stdin=subprocess.PIPE,stdout=f,stderr=subprocess.STDOUT,text=True);return p,f
def closeclients(pair):
 p,f=pair
 if p.poll() is None:send(p,'exit');p.wait(timeout=10)
 f.close()
def standalone(label,phase,marker):
 log=r/(label+'.log');cl=None
 with log.open('w') as f:
  server=subprocess.Popen(args+['-jar','paper.jar','--nogui'],cwd=r,stdin=subprocess.PIPE,stdout=f,stderr=subprocess.STDOUT,text=True)
  try:
   wait(lambda:'Done (' in text(log),150,server,label+' ready');print('READY',label,flush=True)
   if phase=='old':wait(lambda:'stage: DONE' in text(r/'plugins/BlackWikiSMP/spawn-build.yml'),60,server,'garden complete')
   cl=clients(label);wait(lambda:all(n+' PLAY_READY' in text(r/(label+'-clients.log')) for n in ['Alice','Bob']),60,server,'client login');time.sleep(1);send(server,'probe '+phase);wait(lambda:(r/marker).exists(),100,server,marker);print('PASS',phase,flush=True);closeclients(cl);cl=None;send(server,'stop');server.wait(timeout=80);assert server.returncode==0
  finally:
   if cl:closeclients(cl)
   if server.poll() is None:
    send(server,'stop')
    try:server.wait(timeout=45)
    except subprocess.TimeoutExpired:server.terminate();server.wait(timeout=10)
def main():
 init();compile_fixtures();standalone('old29','old','OLD_READY');shutil.copy2(src/'target/BlackWikiSMP-3.0.jar',r/'plugins/BlackWikiSMP.jar');standalone('new30','new','NEW_READY');standalone('restart30','restart','RESTART_READY');assert not (r/'FAILED').exists();(r/'ALL_PASSED').write_text('OK');print('ALL NATIVE CHECKS PASSED',flush=True)
if __name__=='__main__':main()

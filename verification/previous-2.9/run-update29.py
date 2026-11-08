#!/usr/bin/env python3
"""Local native upgrade/control test. Discord transports are mocks; Paper and persistence are real."""
import os,pathlib,subprocess,shutil,time,json,urllib.parse,zipfile,re,sys,hashlib
import yaml
b=pathlib.Path(__file__).resolve().parents[2];src=b/'project29';r=b/'integration29-final';java=next((b/'tools').glob('amazon-corretto-25*/bin/java'));node=shutil.which('node')
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
 if r.exists():raise RuntimeError('choose a new integration29 folder')
 r.mkdir();(r/'plugins/BlackWikiSMP').mkdir(parents=True)
 for n in ['libraries','versions']:shutil.copytree(b/'integration'/n,r/n,copy_function=os.link)
 shutil.copytree(b/'integration/cache',r/'cache');shutil.copy2(b/'deps/paper-26.2-135.jar',r/'paper.jar')
 assert hashlib.sha256((r/'cache/mojang_26.2.jar').read_bytes()).hexdigest()=='cdacdfb25898de5e4b4b0e5ddcc2722f77067e46605709c2d886c000ebb63ec5'
 (r/'eula.txt').write_text('eula=true\n');(r/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25582\nonline-mode=false\nenforce-secure-profile=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\ngamemode=survival\ndifficulty=easy\nmax-tick-time=-1\npause-when-empty-seconds=-1\nallow-flight=true\nmax-players=40\n')
 with zipfile.ZipFile(src/'binary/BlackWikiSMP-2.8.jar') as z:y=yaml.safe_load(z.read('config.yml'))
 y['queue']['player-slots']=17;y['queue']['max-waiting']=11
 for n in ['bedrock','backup','cleanup','monsters','discord']:y[n]['enabled']=False
 (r/'plugins/BlackWikiSMP/config.yml').write_text(yaml.safe_dump(y,allow_unicode=True,sort_keys=False));shutil.copy2(src/'binary/BlackWikiSMP-2.8.jar',r/'plugins/BlackWikiSMP.jar')
def compile_fixtures():
 cp=os.pathsep.join(str(p) for p in (r/'libraries').rglob('*.jar'));dest=r/'probe-classes';dest.mkdir();subprocess.run([str(java.with_name('javac')),'--release','25','-cp',cp,'-d',str(dest),str(src/'verification/Update29Probe.java')],check=True)
 (dest/'plugin.yml').write_text('name: Update29Probe\nversion: 1\nmain: kr.blackwiki.test.Update29Probe\napi-version: "26.2"\ndepend: [BlackWikiSMP]\ncommands:\n  probe: {}\n');subprocess.run([str(java.with_name('jar')),'cf',str(r/'plugins/Update29Probe.jar'),'-C',str(dest),'.'],check=True)
 dest=r/'harness-classes';dest.mkdir();cp=str(src/'target/BlackWikiSMP-2.9.jar')+os.pathsep+cp;subprocess.run([str(java.with_name('javac')),'--release','25','-cp',cp,'-d',str(dest),str(src/'verification/ControllerHarness.java')],check=True);return cp+os.pathsep+str(dest)
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
   cl=clients(label);wait(lambda:'Bob PLAY_READY' in text(r/(label+'-clients.log')),30,server,'client login');time.sleep(1);send(server,'probe '+phase);wait(lambda:(r/marker).exists(),30,server,marker);print('PASS',phase,flush=True);closeclients(cl);cl=None;time.sleep(1);send(server,'stop');server.wait(timeout=80);assert server.returncode==0
  finally:
   if cl:closeclients(cl)
   if server.poll() is None:
    send(server,'stop')
    try:server.wait(timeout=45)
    except subprocess.TimeoutExpired:server.terminate();server.wait(timeout=10)
def controller(cp):
 folder=r/'blackwikismp-controller';folder.mkdir(exist_ok=True);(folder/'controller.yml').write_text(yaml.safe_dump({'server-command':args+['-jar','paper.jar','--nogui']}))
 edit_config(lambda y:y['discord'].update({'enabled':True,'gateway-owner':'controller','relay-discord-chat':True,'control':{'enabled':True,'channel-id':'222222222222222222','allowed-user-ids':['333333333333333333'],'allowed-role-ids':[],'confirmation-seconds':30,'api-port':28919}}))
 log=r/'controller.log';cl=None
 with log.open('w') as f:
  h=subprocess.Popen([str(java),'-Xmx128M','-cp',cp,'kr.blackwiki.smp.ControllerHarness',str(r)],cwd=r,stdin=subprocess.PIPE,stdout=f,stderr=subprocess.STDOUT,text=True)
  try:
   wait(lambda:'HARNESS_READY' in text(log),10,h,'controller gateway');send(h,'denied !서버켜기');wait(lambda:'권한이 없습니다' in text(log),10,h,'unauthorized start denied')
   for i in [1,2]:
    send(h,'discord !서버켜기');wait(lambda:text(log).count('[서버 시작]')>=i*2,150,h,'authenticated ready notification');print('READY controller server',i,flush=True)
    cl=clients('control'+str(i));wait(lambda:'Bob PLAY_READY' in text(r/f'control{i}-clients.log'),30,h,'controller client login');time.sleep(1);send(h,'console probe '+('controller' if i==1 else 'restart'));wait(lambda:(r/('CONTROLLER_READY' if i==1 else 'RESTART_READY')).exists(),30,h,'native persistence checks')
    if i==1:
     before=(r/'plugins/BlackWikiSMP/config.yml').read_bytes();setup=subprocess.run([sys.executable,str(src/'run_discord_controller.py'),'--server',str(r),'--java',str(java),'--setup'],capture_output=True,text=True,timeout=20);assert setup.returncode!=0;assert before==(r/'plugins/BlackWikiSMP/config.yml').read_bytes();(r/'installer29.txt').write_text('PASS setup refuses running native world without changing config\n')
     send(h,'discord 컨트롤러 한글 채팅');time.sleep(2);send(h,'console probe chat');wait(lambda:(r/'CHAT_READY').exists(),15,h,'API chat delivery');send(h,'denied !서버끄기 확인 00000000');time.sleep(.5)
    closeclients(cl);cl=None;before=text(log).count('초 안에 같은 계정');send(h,'discord !서버끄기');wait(lambda:text(log).count('초 안에 같은 계정')>before,15,h,'confirmation challenge');nonce=re.findall(r'!서버끄기 확인 ([0-9]{8})',text(log))[-1];send(h,'discord !서버끄기 확인 '+nonce);wait(lambda:text(log).count('[서버 종료]')>=i*2,90,h,'saved native shutdown');send(h,'status');wait(lambda:text(log).count('HARNESS_STATUS gateway=true child=false')>=i,10,h,'controller alive with game stopped');print('PASS actual Discord stop/start cycle',i,flush=True)
   send(h,'exit');h.wait(timeout=15);assert h.returncode==0
  finally:
   if cl:closeclients(cl)
   if h.poll() is None:send(h,'exit');h.wait(timeout=55)
 key=(r/'plugins/BlackWikiSMP/discord-control.key').read_bytes();settings=(folder/'controller.yml').read_bytes();setup=subprocess.run([sys.executable,str(src/'run_discord_controller.py'),'--server',str(r),'--java',str(java),'--setup'],capture_output=True,text=True,timeout=20);assert setup.returncode==0,setup.stdout+setup.stderr;assert settings==(folder/'controller.yml').read_bytes();assert key==(r/'plugins/BlackWikiSMP/discord-control.key').read_bytes();(r/'installer29.txt').write_text((r/'installer29.txt').read_text()+'PASS offline setup succeeds with installed Paper libraries\nPASS custom server argv preserved\nPASS private control key preserved\n')
 payload=[json.loads(x)['content'] for x in (r/'controller-outbox.jsonl').read_text().splitlines()];assert sum('[서버 시작]' in x for x in payload)==2;assert sum('[서버 종료]' in x for x in payload)==2;print('PASS unique controller lifecycle notices and offline setup',flush=True)
def main():
 init();cp=compile_fixtures();standalone('old28','old','OLD_READY');shutil.copy2(src/'target/BlackWikiSMP-2.9.jar',r/'plugins/BlackWikiSMP.jar');edit_config(lambda y:y['discord'].update({'enabled':True,'bot-token':'unit-test-token-123456789','bot-token-env':'','guild-id':'111111111111111111','channel-id':'222222222222222222','relay-discord-chat':False,'flush-interval-ms':250,'control':{'enabled':False}}));standalone('new29','new','NEW_READY')
 lines='\n'.join(json.loads(x)['content'] for x in (r/'outgoing29.jsonl').read_text().splitlines());assert lines.count('시작 완료')==1;assert lines.count('정상 종료')==1
 for n in ['Alice','Bob']:assert lines.count('[입장] '+n)==1;assert lines.count('[퇴장] '+n)==1;assert lines.index('[입장] '+n)<lines.index('[퇴장] '+n)
 print('PASS real join/quit/start/stop notice counts and order',flush=True);controller(cp);assert not (r/'FAILED').exists();print('ALL NATIVE CHECKS PASSED',flush=True)
if __name__=='__main__':main()

#!/usr/bin/env python3
"""서버를 종료한 후: python install_with_voice.py --server-dir /path/to/server"""
import argparse, datetime, hashlib, os, pathlib, re, shutil, sys, tempfile, urllib.request, zipfile
URL='https://cdn.modrinth.com/data/9eGKb6K1/versions/5OqsnSs7/voicechat-bukkit-2.6.25.jar'
SHA512='2d756e38825428e5bcb00b4dd87773bff6d07ffb4c54d33b9f34de46bf517cc1951ffa0ef3732611753f9b1f9e17add3bec1cec5c113a4e903a0fbd10be2fcd6'

def atom(path,raw):
    path.parent.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix=path.name+'.',suffix='.tmp',dir=path.parent)
    try:
        with os.fdopen(fd,'wb')as out:out.write(raw);out.flush();os.fsync(out.fileno())
        os.replace(tmp,path)
    finally:
        pathlib.Path(tmp).unlink(missing_ok=True)

def descriptor(path):
    try:
        with zipfile.ZipFile(path)as z:
            text=z.read('plugin.yml').decode('utf-8')
        m=re.search(r'^name:\s*[\'"]?([^\'"\r\n]+)',text,re.M)
        return m.group(1).strip()if m else None
    except (OSError,KeyError,ValueError,zipfile.BadZipFile):return None

def main():
    parser=argparse.ArgumentParser(description='BlackWikiSMP 3.3와 공식 음성 서버 설치. 먼저 Minecraft 서버를 완전히 종료하세요.')
    parser.add_argument('--server-dir',type=pathlib.Path,required=True,help='서버 루트 폴더')
    args=parser.parse_args();root=args.server_dir.expanduser().resolve();here=pathlib.Path(__file__).resolve().parent
    mainjar=here/'binary/BlackWikiSMP-3.3.jar'
    if not mainjar.is_file():raise RuntimeError('binary/BlackWikiSMP-3.3.jar를 찾을 수 없습니다. 소스·가이드 ZIP 전체를 풀어 주세요.')
    # Resolve network bytes and verify them before moving any existing installation.
    req=urllib.request.Request(URL,headers={'User-Agent':'BlackWikiSMP-installer/3.1'})
    with urllib.request.urlopen(req,timeout=60)as response:raw=response.read(4*1024*1024+1)
    if hashlib.sha512(raw).hexdigest()!=SHA512:raise RuntimeError('공식 음성 플러그인 파일 해시가 다릅니다. 설치를 중단합니다.')
    plugins=root/'plugins';plugins.mkdir(parents=True,exist_ok=True)
    archive=root/'disabled-plugins'/('BlackWikiSMP-3.3-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    targets={plugins/'BlackWikiSMP-3.3.jar':mainjar.read_bytes(),plugins/'voicechat-bukkit-2.6.25.jar':raw}
    for old in list(plugins.glob('*.jar')):
        if descriptor(old)in {'BlackWikiSMP','voicechat'}:
            if old in targets and old.read_bytes()==targets[old]:continue
            archive.mkdir(parents=True,exist_ok=True);shutil.move(str(old),str(archive/old.name))
    for target,body in targets.items():atom(target,body)
    voice=plugins/'voicechat';voice.mkdir(exist_ok=True)
    config=voice/'voicechat-server.properties'
    if not config.exists():atom(config,(here/'voicechat-ko/voicechat-server.properties').read_bytes())
    translation=voice/'translations.properties';template=(here/'voicechat-ko/translations.properties').read_text(encoding='ascii')
    if translation.exists():
        backup=voice/'translations.properties.before-blackwikismp-3.1.bak'
        if not backup.exists():shutil.copy2(translation,backup)
        updates={line.split('=',1)[0]:line for line in template.splitlines()if '='in line and not line.lstrip().startswith('#')}
        lines=[]
        for line in translation.read_text(encoding='utf-8').splitlines():
            key=line.split('=',1)[0].strip()
            lines.append(updates.pop(key,line))
        lines.extend(updates.values());template='\n'.join(lines)+'\n'
    atom(translation,template.encode('utf-8'))
    print('BlackWikiSMP 3.3 + 공식 Simple Voice Chat 2.6.25 설치 완료.')
    print('기존 데이터와 음성 포트 설정은 유지했습니다. 교체한 JAR는 disabled-plugins에 보관합니다.')
    print('서버 시작 전 호스팅/방화벽에서 UDP 24454를 열고, 플레이어에게 클라이언트 음성 모드를 안내하세요.')
    print('접속 후 /명령어 /음성채팅 /플러그인상태 로 확인하세요.')
if __name__=='__main__':
    try:main()
    except Exception as ex:print('설치 실패:',str(ex),file=sys.stderr);sys.exit(1)

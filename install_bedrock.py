#!/usr/bin/env python3
"""Install only while the Paper server is fully stopped; preserve data and archive old JARs."""
import argparse,hashlib,json,os,pathlib,shutil,subprocess,sys,urllib.request

def main():
    parser=argparse.ArgumentParser(description='BlackWikiSMP 3.3 휴대폰·베드락 접속 설치')
    parser.add_argument('--server',required=True,help='server.properties가 있는 서버 폴더')
    parser.add_argument('--port',type=int,help='베드락 포트. 생략하면 기존 값 또는 19132')
    parser.add_argument('--public-address',help='휴대폰에 안내할 공인 IP 또는 도메인')
    parser.add_argument('--java',default='java',help='Java 25 java 실행 파일 경로')
    args=parser.parse_args();here=pathlib.Path(__file__).resolve().parent;root=pathlib.Path(args.server).resolve()
    if not(root/'server.properties').is_file():raise RuntimeError('server.properties가 있는 서버 폴더를 지정하세요.')
    libs=sorted((root/'libraries').rglob('*.jar'))
    if not any('paper-api-' in p.name for p in libs):raise RuntimeError('Paper 26.2를 한 번 실행하고 종료하여 libraries를 만든 뒤 설치하세요.')
    mainjar=next((p for p in [here/'binary/BlackWikiSMP-3.3.jar',here/'plugins/BlackWikiSMP-3.3.jar'] if p.is_file()),None)
    if mainjar is None:raise RuntimeError('설치 꾸러미 또는 소스 ZIP 전체를 풀어서 실행하세요.')
    manifest=json.loads((here/'bedrock/dependencies.json').read_text(encoding='utf-8'))
    cache=here/'bedrock/dependencies'
    for d in manifest:
        source=next((p for p in [here/'plugins'/d['file_name'],cache/d['file_name']] if p.is_file()),None)
        if source is None:
            cache.mkdir(parents=True,exist_ok=True);source=cache/d['file_name'];request=urllib.request.Request(d['url'],headers={'User-Agent':'BlackWikiSMP-installer/3.1'})
            with urllib.request.urlopen(request,timeout=60) as response:raw=response.read()
            if hashlib.sha256(raw).hexdigest()!=d['sha256']:raise RuntimeError('공식 파일 검증 실패: '+d['file_name'])
            source.write_bytes(raw)
        if hashlib.sha256(source.read_bytes()).hexdigest()!=d['sha256']:raise RuntimeError('공식 파일 검증 실패: '+d['file_name'])
    command=[args.java,'-Xmx384M','-cp',os.pathsep.join(str(p) for p in [mainjar,*libs]),'kr.blackwiki.smp.BedrockInstaller','--server',str(root),'--bundle',str(here)]
    if args.port is not None:command+=['--port',str(args.port)]
    if args.public_address is not None:command+=['--public-address',args.public_address]
    return subprocess.run(command,check=False).returncode

if __name__=='__main__':
    try:sys.exit(main())
    except Exception as error:print('설치 중단:',error,file=sys.stderr);sys.exit(2)

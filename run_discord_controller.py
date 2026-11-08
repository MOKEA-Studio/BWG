#!/usr/bin/env python3
"""Always-on Discord controller. Uses Java 25 and the Paper libraries already on your server."""
import argparse, os, pathlib, subprocess, sys

def main():
    parser = argparse.ArgumentParser(description='BlackWikiSMP 3.3 양방향 디스코드·서버 전원 도우미')
    parser.add_argument('--server', required=True, help='server.properties가 있는 Paper 서버 폴더')
    parser.add_argument('--java', default='java', help='Java 25 실행 파일')
    parser.add_argument('--setup', action='store_true', help='서버 종료 상태에서 최초 설정')
    args = parser.parse_args()
    root = pathlib.Path(args.server).resolve()
    here = pathlib.Path(__file__).resolve().parent
    jars = [root/'plugins/BlackWikiSMP-3.3.jar', here/'binary/BlackWikiSMP-3.3.jar']
    jar = next((p for p in jars if p.is_file()), None)
    libs = sorted((root/'libraries').rglob('*.jar'))
    if jar is None or not any('paper-api-' in p.name for p in libs):
        raise RuntimeError('3.3 JAR를 설치하고 Paper 26.2를 한 번 실행·종료한 뒤 사용하세요.')
    command = [args.java, '-Xmx256M', '-cp', os.pathsep.join(str(p) for p in [jar, *libs]),
               'kr.blackwiki.smp.DiscordController', '--server', str(root)]
    if args.setup:
        command.append('--setup')
    process = subprocess.Popen(command)
    try:
        return process.wait()
    except KeyboardInterrupt:
        try:
            return process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.terminate()
            return process.wait(timeout=10)

if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print('도우미 중단:', error, file=sys.stderr)
        sys.exit(2)

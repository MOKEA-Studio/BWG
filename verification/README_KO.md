# BlackWikiSMP 3.3 검증 자료

현재 결과는 unit-tests.txt, server-checks.txt, installer-checks.txt, runtime-facts.json 및 상위 TEST_REPORT_KO.md입니다. previous-* 폴더는 과거 결과입니다.

Java 25와 Maven으로 프로젝트 루트의 `mvn clean package`를 실행하면 자동 검사를 재현합니다. 실제 서버 검사는 Update33Probe.java, protocol-client.js, run-update33.py를 사용합니다. 테스트 프로젝트가 project33 폴더이고 옆 integration 폴더에 Paper 26.2의 paper.jar·libraries·versions·cache, 소스 binary 폴더에 이전 BlackWikiSMP-3.2.jar가 있는 격리 개발 환경용입니다. 구버전 JAR는 설치 혼동을 피하려고 배포 ZIP에 넣지 않았습니다. 경로를 환경에 맞게 바꾸고 Probe를 운영 서버에 설치하지 마세요.

resume-restart33.py는 최초 재시작 준비가 대기시간을 초과했던 동일 테스트 폴더에서 재시작 단계만 다시 실행할 때 사용했습니다. 두 성공 단계와 재시작 확인을 합친 원시 결과는 server-checks.txt에 있습니다. native packet 비교는 실제 서버 패킷 생성 경로를 사용하지만 외부 X-ray 클라이언트 검사는 아닙니다.

# BlackWikiSMP 3.2 검증 자료

현재 결과는 unit-tests.txt, server-checks.txt, installer-checks.txt, runtime-facts.json과 상위 TEST_REPORT_KO.md에 있습니다. previous-*는 과거 검사 기록입니다.

Java 25와 Maven으로 프로젝트 루트의 `mvn clean package`를 실행하면 자동 검사를 재현합니다. 실제 서버 검사는 Update32Probe.java, protocol-client.js, run-update32.py를 사용합니다. 재현 스크립트는 project32 소스 폴더 옆 integration 폴더에 Paper 26.2의 paper.jar·libraries·versions·cache, 소스 binary 폴더에 원래 검사에 사용한 3.1 기준 JAR가 있는 개발 환경용입니다. 기준 JAR는 설치 혼동을 피하려고 배포 ZIP에 넣지 않았습니다. 폴더 경로를 자신의 환경에 맞게 바꾸세요. 검사 Probe를 실제 운영 서버에 설치하지 마세요.

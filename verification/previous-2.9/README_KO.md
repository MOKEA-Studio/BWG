# 2.9 검증 자료

현재 결과는 상위 TEST_REPORT_KO.md와 runtime-facts.json을 따릅니다. previous-*의 과거 결과는 현재 수에 합치지 않습니다.

자동 검사는 Java 25와 Maven에서 `mvn package`로 실행했습니다. 의존성은 Paper API 26.2.build.135-stable 및 선택 음성 API 2.6.25입니다.

Update29Probe.java와 ControllerHarness.java는 격리된 테스트 서버 전용이며 운영 plugins에 넣지 마세요. Discord 전송기만 모의 객체로 바꾸고 실제 플러그인·Gateway·컨트롤러·Paper 프로세스·플레이어 저장 자료를 검사합니다. run-update29.py는 테스트 경로의 Paper JAR·libraries·정상 캐시와 원본 2.8 JAR를 전제로 합니다. baseline JAR는 배포 설치용 ZIP에 중복 포함하지 않았습니다.

실제 서버 검사 중 각 JVM을 정상 종료하고 다음 JVM을 시작했습니다. 도우미가 시작한 서버는 인증 API를 통해 저장 종료하며, 게임이 꺼진 동안 Gateway가 살아 있음을 확인한 뒤 재시작합니다. 설정에 넣은 토큰/ID와 테스트 계정은 모의 값입니다. 실제 Discord 연결과 휴대폰 로그인은 범위 밖입니다.

spawn-plan.json과 render_spawn_preview.py는 기존 스폰 설계 자료입니다. 설치 파일에는 테스트 월드·런타임 인증 키·개인 설정·Proxy 정보·검사용 JAR를 포함하지 않습니다.

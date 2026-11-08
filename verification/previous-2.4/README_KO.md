# 2.4 검증 자료

Java 25에서 `mvn clean verify`로 단위 검사를 실행합니다. Paper API 26.2.build.135-stable과 선택 연동 Voice Chat API 2.6.25를 compile-only/provided로 사용합니다.

RuntimeProbe.java와 protocol-client.js는 격리된 loopback 테스트 서버 전용입니다. 운영 plugins에 Probe를 설치하지 마세요. 실제 마이크를 녹음하거나 테스트 플레이어에게 마이크 모드 설치를 강제하지 않습니다. TCP 테스트 클라이언트는 게임 입장·텔레포트 후 로드 완료 응답을 보냅니다.

Paper 26.2 build 135 / Java 25.0.5에서 Alice/Bob/Charlie/Newbie 네 계정을 연결했습니다. 초기 2.1 보관함 데이터와 기존 단일 홈을 준비하고, probe phase1(장비·혈청·신규·벌목·무덤) → 종료/재시작 → phase2(홈·음성·PvP·이벤트) → 종료/재시작 → phase3(영속성·백업)를 실행했습니다. 마지막 음성 팀 권한/명령 별칭 수정 후 최종 배포 JAR로 phase4(음성 권한·keepInventory), 음성 플러그인을 plugins 밖으로 옮기고 phase5(선택 의존성 부재), 다시 넣고 phase6(실제 명령어 별칭·한국어 원문 로드)를 확인했습니다.

일부 GUI 클릭·블록/피해 이벤트·거래 중단 경계·음성 패킷은 Bukkit API/공식 음성 API 프록시/리플렉션으로 주입했습니다. 최종 JAR와 4~6단계 서버 JAR는 동일합니다. 동일 메시지로 반복하는 40묶음 회수와 아이템 직렬화도 assertion 수에 포함합니다. 자세한 수는 runtime-facts.json, 결과는 server-checks.txt와 unit-tests.txt에 있습니다. previous-2.2는 과거 자료로, 이번 실행 수에 합치지 않습니다.

실제 운영 Minecraft 화면 조작, 두 마이크 사이의 음질/지연, 운영 호스팅 UDP 개방, 대규모 부하, 모든 외부 플러그인 조합, 전원 차단/디스크 장애는 별도 검증이 필요합니다. 기존 기능의 단위 검사는 모두 실행했으며 이전 버전 전체 네이티브 서버 검사를 재실행했다고 주장하지 않습니다.

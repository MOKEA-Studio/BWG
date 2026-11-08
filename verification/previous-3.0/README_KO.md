# 3.0 검증 자료

상위 TEST_REPORT_KO.md와 runtime-facts.json이 현재 결과입니다. previous-*의 과거 검사 수는 현재 수와 합치지 않습니다.

Java 25에서 Maven package로 자동 검사 155개를 실행했습니다. 실제 서버 검증은 Update30Probe.java와 run-update30.py를 사용했습니다. 이 Probe는 격리 테스트 전용이며 운영 plugins에 넣지 않습니다. 재현 도구는 원본 2.9 JAR와 Paper 26.2의 정상 JAR/라이브러리/캐시를 전제로 하며, 원본 2.9 JAR를 binary/BlackWikiSMP-2.9.jar에 준비합니다. 배포 설치용 ZIP에는 이전 JAR를 중복 포함하지 않습니다.

피해를 비교하는 동안 테스트 월드의 자연 회복을 끄고 청크 티켓으로 엔티티 틱을 보장합니다. 고정 시간만 기다리지 않고 실제 충돌 이벤트를 확인한 뒤 HP를 비교합니다. 이 설정과 테스트용 수치 주입은 운영 코드가 아닙니다.

원본 2.9 데이터 생성, 3.0 GUI/거래/발사/재장전/보호/아이템 처리, 정상 종료와 재시작 후 저장값을 검사합니다. 새 명중 피해는 실제 움직이는 Snowball과 기본 damage 이벤트 경로로 확인했습니다. 이전 Discord/Bedrock 자료는 previous-2.9와 previous-2.8에 보관합니다.

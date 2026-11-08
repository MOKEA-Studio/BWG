# 2.2 검증 자료

단위 검사: Maven `mvn clean verify`로 실행합니다. Java 25와 Paper API 26.2.build.135-stable을 사용합니다.

RuntimeProbe.java와 protocol-client.js는 loopback 전용 테스트 서버의 검사 자료입니다. 운영 서버의 plugins에 Probe를 설치하지 마세요. Probe는 배포 JAR에 포함하지 않습니다. 서버 준비 후 테스트용 Alice/Bob/Charlie TCP 클라이언트와 Probe를 연결하고 `probe phase1`, 재시작 후 `probe phase2`로 게임 동작·재시작 데이터를 검사했습니다. 일부 GUI 클릭·블록 이벤트·거래 중단 경계는 Bukkit 이벤트와 리플렉션으로 주입했습니다.

검사 항목 75종을 반복하여 195번 검증했습니다. 60마리 생성 반복의 레벨/체력 검사 120회도 이 횟수에 포함되며 서로 다른 기능 195개라는 뜻이 아닙니다. 전체 기록은 server-checks.txt에 있습니다.

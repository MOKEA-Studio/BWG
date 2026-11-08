# BlackWikiSMP 3.3 설치·사용 안내

Paper 26.2 / Java 25용입니다. 2.8의 게임 기능과 데이터를 유지하며 Discord 양방향 텍스트 채팅, 서버 시작·종료 및 플레이어 입퇴장 알림, 허용된 Discord 관리자의 서버 전원 명령을 추가합니다.

## 기존 버전에서 업데이트

1. 서버를 정상 종료합니다.
2. 이전 BlackWikiSMP JAR를 plugins 밖으로 옮기고 `BlackWikiSMP-3.3.jar` 한 개만 넣습니다.
3. **plugins/BlackWikiSMP 폴더와 월드를 그대로 유지합니다.** 기존 config.yml을 기본 파일로 덮어쓰지 않습니다.
4. Java 25로 시작하면 새 설정만 자동 추가하며 변경 전 설정은 `config.yml.before-3.1.bak`에 보관합니다.
5. `/업데이트상태`, `/플러그인상태`, `/명령어`로 확인합니다.

기존 Geyser·Floodgate·ViaVersion, Simple Voice Chat 설치는 유지합니다. 베드락 최초 설치에는 해당 공식 플러그인이 필요합니다. 40,000×40,000 경계, 10분 무덤, 2초마다 1코인, 기본템 1회 수령 기록, 홈·팀·랭킹·보관함·상점·유저상점·특별 장비·보호·스폰을 유지합니다. 무덤 만료 기준을 다시 연장하지 않습니다.

## Discord 봇 설정

[Developer Portal](https://discord.com/developers/applications)에서 봇을 만들고 **Bot → Privileged Gateway Intents → Message Content Intent**를 켭니다. 검증된 봇은 해당 Intent 승인이 필요할 수 있습니다. 봇을 Discord 서버에 초대하고 사용할 채팅·관리 채널에 **채널 보기 / 메시지 보내기** 권한을 줍니다.

Discord 개발자 모드에서 서버·채널·사용자·역할의 ID를 복사합니다. 이름 대신 숫자 ID를 쓰며 YAML에서 따옴표로 감쌉니다. 기존 config.yml의 discord 블록을 수정하세요. 같은 블록을 파일 끝에 중복 추가하지 않습니다.

```yaml
discord:
  enabled: true
  mode: bot
  gateway-owner: plugin
  guild-id: '디스코드_서버_ID'
  bot-token: ''
  bot-token-env: BLACKWIKISMP_DISCORD_BOT_TOKEN
  channel-id: '채팅_채널_ID'
  relay-chat: true
  relay-discord-chat: true
  relay-player-events: true
  relay-server-events: true
  relay-moderation: true
  control:
    enabled: false
    channel-id: '관리_채널_ID'
    allowed-user-ids: ['관리자_사용자_ID']
    allowed-role-ids: []
    confirmation-seconds: 30
    api-port: 28919
```

`bot-token`에 봇 토큰을 직접 입력하거나, 비워 두고 `bot-token-env`에 지정한 환경 변수로 전달합니다. 사용자 계정 토큰이 아닌 봇 토큰을 사용합니다. 토큰을 채팅이나 명령어에 입력하지 마세요. 환경 변수 방식에서는 도우미와 서버 모두 해당 변수를 받아야 하며, 도우미가 시작하는 서버는 도우미의 환경을 상속합니다.

`gateway-owner: plugin`은 실행 중인 서버의 플러그인이 Discord 수신을 담당합니다. 양방향 채팅과 알림에 별도 도우미가 필요하지 않습니다. `control.enabled: true`와 관리 ID를 설정하면 실행 중 서버의 상태·정상 종료도 가능합니다. **서버가 완전히 꺼지면 플러그인도 멈추므로, Discord에서 다시 켜려면 아래 도우미를 계속 실행해야 합니다.**

## 꺼진 서버 켜기: 상시 실행 도우미

도우미는 게임 서버와 같은 컴퓨터에서 실행합니다. **컴퓨터/호스트 자체가 켜져 있어야 하며 도우미를 계속 실행해야 합니다.** 컴퓨터 전원 켜기나 호스팅 사이트의 재부팅 API는 포함하지 않습니다.

Python 3.10 이상, Java 25가 필요하며 별도 pip 패키지는 필요하지 않습니다. 먼저 Paper와 3.1를 한 번 실행·정상 종료하여 서버 폴더에 libraries와 SMP 설정을 만듭니다.

1. 도우미 ZIP 전체를 풉니다. 서버가 완전히 종료된 상태에서 최초 설정합니다.

```bash
python run_discord_controller.py --server "/서버/폴더" --java "/Java25/bin/java" --setup
```

2. 기존 토큰·채널·게임 데이터는 유지하고 `gateway-owner`를 `controller`로 바꿉니다. `plugins/BlackWikiSMP/discord-control.key`와 `blackwikismp-controller/controller.yml`을 만듭니다. 실행 중인 월드가 감지되면 설정을 중단합니다.
3. SMP config.yml에서 봇 토큰·서버 ID·채팅 채널 ID와 `discord.enabled: true`를 설정합니다. 전원 명령에는 `control.enabled: true`, 관리 채널, 허용 사용자 또는 역할 ID가 필요합니다. 허용 ID가 비어 있으면 관리 명령을 거부합니다.
4. controller.yml의 고정 실행 목록을 확인합니다. 자동 설정은 서버 폴더의 Paper 실행 JAR가 한 개일 때 동작하며, 기존 실행 목록은 덮어쓰지 않습니다.

```yaml
server-command:
  - '/Java25/bin/java'
  - '-Xms512M'
  - '-Xmx2G'
  - '-jar'
  - 'paper.jar'
  - '--nogui'
```

Windows 경로는 예를 들어 `'C:\Program Files\Java\jdk-25\bin\java.exe'`처럼 작은따옴표로 감쌉니다. Java 경로, 메모리, Paper JAR 이름을 본인의 서버에 맞추세요. 실행 목록은 프로그램과 인수이며 셸 문법을 사용하지 않습니다. 작업 폴더는 지정한 서버 폴더입니다.

5. 이제 `--setup` 없이 실행하고 창을 유지합니다. 자동으로 게임 서버를 켜지는 않으며 Discord의 `!서버켜기`로 시작합니다.

```bash
python run_discord_controller.py --server "/서버/폴더" --java "/Java25/bin/java"
```

게임 서버 출력은 `blackwikismp-controller/server.log`에 누적됩니다. 도우미 모드에서는 도우미만 Discord 수신과 서버 시작·종료 알림을 담당합니다. 플러그인은 입퇴장·Minecraft 채팅을 임베드로 전송합니다. 명령어 사용은 전송하지 않습니다. 게임 서버를 꺼도 도우미는 실행되어 다시 켤 수 있습니다.

**Discord 설정 변경 후에는 도우미도 재시작합니다.** 변경 또는 설정 읽기 오류를 감지하면 오래된 권한으로 명령을 받지 않도록 연동을 일시 중지합니다. `/디스코드 끄기`도 다음 설정 확인에서 반영됩니다. 도우미는 실행 중인 게임 프로세스를 강제로 종료하지 않습니다.

로컬 API는 `127.0.0.1:28919`에만 열리며 외부 포트를 열 필요가 없습니다. 다른 프로그램이 쓰면 api-port를 변경하고 서버와 도우미를 재시작하세요. 인증 키는 삭제하지 말고 복원 시 함께 유지합니다. 동일한 폴더의 중복 도우미와 실행 중인 월드의 중복 시작을 차단합니다. API의 서버 폴더 식별값과 프로세스 ID도 확인하여 다른 서버를 잘못 제어하지 않게 합니다.

## Discord 전원 명령

설정한 서버·관리 채널에서 허용 사용자 또는 역할만 사용할 수 있습니다. Discord 관리자라는 이유만으로 자동 허용하지 않습니다. 봇·웹후크·DM 메시지는 무시합니다.

| 입력 | 동작 |
| --- | --- |
| `!서버도움말` | 관리 명령 안내 |
| `!서버상태` | 서버 상태·인원·Gateway 상태 |
| `!서버켜기` | 도우미가 고정 실행 목록으로 서버 시작 |
| `!서버끄기` | 일회용 종료 확인 번호 발급 |
| `!서버끄기 확인 12345678` | 같은 계정이 실제 발급 번호로 확인하면 저장 후 정상 종료 |

기본 확인 시간은 30초입니다. 다른 사용자, 잘못된 번호, 만료·재사용 번호로 종료하지 않습니다. 확인 시점에도 권한을 다시 검사합니다. Discord 메시지를 임의 게임·콘솔·운영체제 명령으로 실행하지 않습니다. 정상 종료는 플러그인 API로 요청하고, 연결이 끊겼다면 자신이 시작한 프로세스에 고정 `stop`을 보냅니다. 알 수 없는 프로세스를 강제로 종료하지 않습니다.

## 게임 내 OP 명령과 GUI

| 명령 | 설명 |
| --- | --- |
| `/디스코드` | 한국어 설정 GUI |
| `/디스코드 상태` | 연결 상태 |
| `/디스코드 테스트` | 봇 전송 테스트 |
| `/디스코드 켜기`, `/디스코드 끄기` | 연동 활성화 |
| `/디스코드 채널 숫자ID` | 채팅 중계 채널 변경 |
| `/디스코드 양방향 on\|off` | Discord → Minecraft 수신 |
| `/디스코드 알림 on\|off` | 입퇴장·서버 알림 |
| `/플러그인상태` | 전송·수신·도우미 연결 확인 |
| `/smp reload` | 파일에서 바꾼 설정 반영 |

전원 허용 ID와 토큰은 설정 파일에서 입력합니다. GUI에도 양방향 수신과 알림 버튼이 있습니다. 외부 도우미 설정을 바꾸면 도우미도 재시작하세요.

## 동작과 오류 처리

- 지정 채널의 Discord 텍스트는 `[Discord] 사용자: 내용`으로 보입니다. 게임 욕설 필터를 적용하고 `미친`, `존나` 허용을 유지합니다. 첨부만 있는 메시지·음성·Discord 슬래시 명령은 중계하지 않습니다.
- 수신 채팅을 Discord로 다시 보내지 않으며 중복 메시지 ID·봇·웹후크를 제외합니다. Gateway 재연결에는 세션 복구와 하트비트 응답 확인을 사용합니다.
- 실제 서버 준비 후 시작 알림을 보냅니다. 플러그인 모드의 종료 알림은 종료 직전, 도우미 모드는 프로세스 종료 감지 후 보냅니다. 입퇴장에는 이름·접속 인원을 표시하고 중복 이벤트를 걸러 냅니다. 서버 전체 종료 중에는 개별 퇴장 알림을 대량으로 보내지 않습니다.
- 메시지는 종류별 색상 임베드(채팅·입장·퇴장·서버·관리)로 보냅니다. 명령어 사용 기록은 보내지 않으며 Discord 멘션 알림 차단을 유지합니다. 수신/전송 대기열 크기를 제한하고 HTTP 429는 Discord 대기 시간을 따릅니다. 입퇴장·관리·종료 알림을 일반 채팅보다 우선합니다.
- 같은 설정의 재로드는 전송 대기 메시지를 유지합니다. 토큰/채널 변경 시 이전 메시지를 새 채널로 보내지 않습니다. 인증/권한 오류의 반복 요청을 멈추고 상태에 표시합니다.
- 장시간 네트워크 장애·대기열 초과·강제 종료·호스트 장애까지 알림 도착을 보장하지 않습니다. 상태의 실패·누락 수를 확인하세요. 종료 시 전송 마무리도 제한된 시간 안에서 처리합니다.

| 문제 | 확인 |
| --- | --- |
| 401 / 토큰 인증 실패 | 봇 토큰·환경 변수, 변경 후 재시작 |
| 4014 / Intent 필요 | Message Content Intent 활성화·승인 |
| 403 | 봇의 해당 채널 보기/보내기 권한 |
| 404 | 채널 ID, 봇 초대 |
| 도우미 연결 대기 | 도우미 실행·gateway-owner·로컬 포트·인증 키 |
| 전원 명령 거부 | control.enabled·서버 ID·관리 채널·허용 사용자/역할 |
| 서버 시작 실패 | controller.yml의 Java/Paper 경로·인수와 server.log |

검사 결과와 실제 Discord 연결 검증의 범위는 [TEST_REPORT_KO.md](TEST_REPORT_KO.md)에 기록합니다. 개인 봇 토큰이 제공되지 않아 실제 Discord 계정으로 메시지는 보내지 않았습니다. 설치 후 본인의 봇 토큰·Intent·채널 권한으로 최종 연결을 확인하세요.

공식 참고: [Gateway / Intent](https://docs.discord.com/developers/events/gateway), [이벤트](https://docs.discord.com/developers/events/gateway-events), [요청 제한](https://docs.discord.com/developers/topics/rate-limits).

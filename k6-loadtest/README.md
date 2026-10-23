# SaiBank 부하테스트 구성

이 디렉터리는 SaiBank 서버에 부하를 보내고, 부하를 발생시킨 클라이언트 지표와 서버 상태를 Grafana에서 함께 보는 도구 모음입니다. 현재는 모니터링 구성을 준비한 상태이며, 실제 요청 시나리오는 아직 없습니다.

## 먼저 알아둘 구성

| 구성 | 하는 일 | 필요한 이유 |
| --- | --- | --- |
| k6 | HTTP 요청을 발생시키고 요청 수, 실패율, 응답 시간 등을 기록합니다. | 서버에 실제 부하를 주는 실행기입니다. `scripts/`에 시나리오를 추가한 뒤 실행합니다. |
| InfluxDB | k6가 만든 시계열 지표를 저장합니다. | 부하가 진행되는 동안의 측정값을 보관해 Grafana 그래프로 볼 수 있습니다. |
| Prometheus | SaiBank의 `/actuator/prometheus`를 주기적으로 읽습니다. | 요청 부하와 같은 시간대의 JVM 및 애플리케이션 상태를 관찰할 수 있습니다. |
| Grafana | InfluxDB와 Prometheus 데이터를 대시보드로 보여줍니다. | 클라이언트 결과와 서버 상태를 한 화면에서 비교합니다. |

대시보드의 `k6` 패널은 k6 실행 후 InfluxDB에 측정값이 생겨야 표시됩니다. `JVM Micrometer` 패널은 서버에 `loadtest` 프로필이 적용되고 Prometheus가 서버에 연결된 뒤 표시됩니다.

## 서버에 Actuator 지표 켜기

`src/main/resources/application-loadtest.yml`은 Spring Boot 관리 엔드포인트를 별도 포트 `8082`에 열고 `health`와 `prometheus`만 노출합니다. 상세 헬스 정보는 외부에 보이지 않게 제한했습니다. 별도 포트를 쓰는 이유는 일반 사용자 API와 모니터링 접근을 네트워크 정책에서 분리하기 위해서입니다.

배포 환경의 활성 프로필에 `loadtest`를 더해 서버를 다시 배포해야 합니다. 예를 들어 현재 `aws` 프로필을 쓰는 경우 `SPRING_PROFILES_ACTIVE=aws,loadtest`로 설정합니다. 기존 배포 프로필은 유지하고 뒤에 `loadtest`를 추가하세요.

`docker-compose.yml`은 관리 포트도 호스트에 전달합니다. Prometheus가 다른 머신에서 실행된다면 서버 방화벽/AWS 보안 그룹에서 TCP 8082를 Prometheus가 실행되는 호스트의 IP만 접근하도록 허용해야 합니다. `SecurityConfig`는 이 두 Actuator 경로만 인증 없이 접근하도록 분리하며, 포트 자체의 네트워크 제한은 방화벽에서 해야 합니다.

Prometheus 설정은 `prometheus/prometheus.yml`에 있습니다. 현재 scrape 대상은 `192.168.0.206:8082`이므로, Prometheus 컨테이너가 그 주소로 라우팅할 수 있어야 합니다. 서버 주소가 바뀌면 이 파일의 target도 함께 바꾸세요. `192.168.x.x`는 사설 주소라 같은 사설망 또는 VPN에서만 도달할 수 있습니다.

서버 배포 후 Prometheus 머신에서 다음 주소가 응답하는지 확인합니다.

```powershell
curl.exe http://192.168.0.206:8082/actuator/health
curl.exe http://192.168.0.206:8082/actuator/prometheus
```

정상이라면 첫 주소는 상태 JSON을, 두 번째 주소는 Prometheus 형식의 메트릭 텍스트를 반환합니다.

## 모니터링 도구 실행

PowerShell에서 `sai-mock-bank/k6-loadtest` 디렉터리로 이동한 뒤 예시 환경 파일을 복사하고 실제 Grafana 비밀번호를 넣습니다.

```powershell
Copy-Item .env.loadtest.example .env.loadtest
notepad .env.loadtest
```

`BASE_URL`은 나중에 k6 시나리오가 호출할 SaiBank 기본 주소입니다. 예시 값은 실제 서버가 아니므로 사용할 서버 주소로 바꿔야 합니다. 현재 시나리오가 없으므로 이 값만으로 부하가 발생하지는 않습니다. 비밀번호 파일은 `.gitignore`에 포함되어 Git에 추가되지 않습니다.

도구만 시작할 때는 아래 명령을 실행합니다.

```powershell
docker compose --env-file .env.loadtest -f docker-compose.loadtest.yml up -d influxdb prometheus grafana
docker compose --env-file .env.loadtest -f docker-compose.loadtest.yml ps
```

`BASE_URL`이 비어 있어도 모니터링 서비스가 시작되도록 구성했습니다. Grafana의 관리자 비밀번호는 필수로 둬 초기 관리자 계정이 기본값으로 열리는 일을 막습니다. 포트는 모두 로컬호스트에만 공개되므로 브라우저에서 `http://localhost:3000`으로 접속합니다. Prometheus의 `http://localhost:9090/targets`에서 `sai-mock-bank` 대상이 `UP`인지 확인할 수 있습니다.

## 파일별 역할

- `docker-compose.loadtest.yml`: InfluxDB, Prometheus, Grafana, k6 컨테이너와 저장 볼륨을 정의합니다. k6는 필요할 때만 실행합니다.
- `prometheus/prometheus.yml`: 서버 메트릭을 읽을 주소와 주기를 정의합니다.
- `grafana/provisioning/datasources/datasource.yml`: Grafana가 InfluxDB와 Prometheus를 자동으로 데이터 소스로 등록하게 합니다. 각 컨테이너 이름을 주소로 사용하므로 Docker 네트워크 안에서 연결됩니다.
- `grafana/provisioning/dashboards/dashboards.yml`: 대시보드 JSON 파일을 시작 시 자동으로 불러오게 합니다.
- `grafana/dashboards/k6.json`: k6 부하 지표 대시보드입니다.
- `grafana/dashboards/jvm-micrometer.json`: Actuator/Micrometer가 제공하는 JVM 및 애플리케이션 메트릭 대시보드입니다.
- `scripts/`: 나중에 k6 시나리오 파일을 두는 위치입니다. 컨테이너에는 읽기 전용으로 연결됩니다.
- `.env.loadtest.example`: 환경 변수 작성 예시입니다. 복사해 만든 `.env.loadtest`에 실제 주소와 비밀번호를 저장합니다.

## 시나리오가 추가된 뒤

시나리오 파일을 `scripts/`에 추가한 다음, 예를 들어 파일 이름이 `smoke.js`라면 다음처럼 실행할 수 있습니다.

```powershell
docker compose --env-file .env.loadtest -f docker-compose.loadtest.yml run --rm k6 run /scripts/smoke.js
```

시나리오는 서버의 허용된 테스트 환경을 대상으로 하고, 사용자/계좌 데이터와 요청 속도를 명시적으로 준비해야 합니다. 현재는 파일이 없으므로 위 명령은 실행할 시나리오가 준비된 후 사용합니다.

## 종료와 데이터 보존

```powershell
docker compose --env-file .env.loadtest -f docker-compose.loadtest.yml down
```

`down`은 컨테이너를 내리고 측정 데이터 볼륨은 보존합니다. 저장된 측정 데이터까지 삭제하려는 경우에만 `down -v`를 사용하세요.

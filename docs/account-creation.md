# 계좌 생성

`POST /api/mock-bank/accounts`는 로그인 JWT와 UUID 형식의 `Idempotency-Key` 헤더를 받습니다.
본문 예: `{"bankCode":"088","accountName":"입출금통장","initialBalance":"100000"}`.
본문 생략 시 기존 동작인 신한은행 입출금통장을 생성합니다. 초기 금액은 `initialBalance`로 지정하고 예금주는 서버가 정합니다. 이전 API 호환을 위해 금액 생략은 0원입니다.

같은 사용자와 요청 키는 한 계좌만 생성합니다. 은행 코드, 직접 입력 은행명, 초기 금액, 계좌명은 앞뒤 공백을 제거한 뒤
원래 조건의 SHA-256 해시를 저장하며, 동일 키로 다른 조건을 보내면 409를 반환합니다.
나중에 계좌 잔액이나 이름이 바뀌어도 원래 요청 조건을 기준으로 재시도를 판별합니다.
재시도 응답은 기존 계좌의 현재 상세 정보이며, 최초 JSON의 바이트 단위 재생은 아닙니다.
현재 응답 상태는 신규 생성과 재시도 모두 기존 API와 동일한 201입니다.

사용자 행 잠금으로 계좌 생성 요청을 직렬화하고, `(bank_user_id, creation_request_id)`
고유 제약으로 중복 INSERT도 방어합니다. 계좌와 요청 기록은 같은 트랜잭션으로 저장합니다.
키를 임의로 만료하거나 계좌를 물리 삭제하면 과거 요청의 중복 방지가 사라질 수 있으므로
생성 기록을 유지해야 합니다. 사용자별로 키가 다르면 별개의 계좌 개설입니다.

home의 계좌 생성 버튼은 이체 버튼과 동일한 스타일입니다. 생성 창에서 조건을 선택하고,
요청 전 사용자별 sessionStorage에 키와 조건을 저장합니다. 타임아웃·새로고침 후에는
같은 요청으로 재시도하며, 성공 후 다시 생성 창을 여는 명시적인 동작으로 새 개설을 시작합니다.
탭을 완전히 닫거나 저장소를 지우면 요청 키가 사라지므로 이때는 기존 계좌를 먼저 확인해야 합니다.

## 스키마

배포 전 `src/main/resources/db/bank_account.sql`을 적용합니다.
추가 컬럼은 `creation_request_id`, `creation_request_hash`, `bank_name`이며, 재실행할 수 있습니다.
기존 요청 키가 있는 계좌는 이전 API의 고정 조건(088 / 입출금통장)으로 해시를 보강합니다.

## 검증

- Java: `./gradlew test --tests '*AccountCreation*' --tests '*BankAccountDTOServiceTest'`
- UI 로직: `node --test src/test/js/account-create.test.cjs`
- MariaDB 통합 테스트: 별도 테스트 DB의 `SAI_BANK_TEST_DB_URL`, `SAI_BANK_TEST_DB_USER`,
  `SAI_BANK_TEST_DB_PASSWORD`와 `SAI_BANK_DB_TEST=true`를 설정합니다.
  동시 요청 8건, 조건 충돌, 새 키 개설, 트랜잭션 롤백, 스키마 재실행을 검증합니다.

직접 입력 은행은 `bankCode: "CUSTOM"`, `bankName: "은행명"`으로 요청합니다.
금액은 0 이상이며 DB DECIMAL(19,2) 범위와 소수점 둘째 자리까지 허용합니다.
화면은 초기 금액을 필수로 입력받고, 생성 완료 시 입력 폼을 완료 안내와 닫기 버튼으로 바꿉니다.
기존 0원/정해진 은행의 요청 해시는 유지하여 이전 버전의 미완료 요청도 안전하게 재시도합니다.

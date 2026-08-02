INSERT INTO bank_account (
    identity_id,
    user_key,
    bank_code,
    account_number,
    account_name,
    account_holder_name,
    balance,
    status,
    created_at,
    updated_at,
    user_token
) VALUES
      (
          1,
          null,
          '088',
          '110123456789',
          '주거래 입출금 통장',
          '바가밥',
          1000000.00,
          'ACTIVE',
          NOW(),
          NOW(),
          'SAI-EYFF6XF6'
      ),
      (
          2,
          null,
          '004',
          '920123456789',
          '비상금 통장',
          '가갸갹',
          500000.00,
          'ACTIVE',
          NOW(),
          NOW(),
          'SAI-QXAEZLP9'
      );
Feature: Brute-force defenses — throttle and attempt caps
  The three guessing surfaces: passwords (throttle), verification codes and
  reset codes (5-attempt caps). All previously proven in integration tests;
  these scenarios pin the black-box HTTP contract.

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.brute.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'
    * def EmailStore = Java.type('com.fintrack.auth.testsupport.RecordingEmailSender')

  Scenario: sixth login attempt is throttled with a 429 problem — account existence irrelevant
    # throttling keys on the submitted email whether or not it has an account
    * def email = uniqueEmail()

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: 'wrong-guess-number-1' }
    When method post
    Then status 401

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: 'wrong-guess-number-2' }
    When method post
    Then status 401

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: 'wrong-guess-number-3' }
    When method post
    Then status 401

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: 'wrong-guess-number-4' }
    When method post
    Then status 401

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: 'wrong-guess-number-5' }
    When method post
    Then status 401

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: 'wrong-guess-number-6' }
    When method post
    Then status 429
    And match header Content-Type contains 'application/problem+json'
    And match response.title == 'Too many attempts'

  Scenario: five wrong verification codes kill the real one
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    * def realCode = EmailStore.lastCodeFor(email)
    * def wrongCode = realCode == '0000' || realCode == '000000' ? '1111' : '0000'

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(wrongCode)' }
    When method post
    Then status 400

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(wrongCode)' }
    When method post
    Then status 400

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(wrongCode)' }
    When method post
    Then status 400

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(wrongCode)' }
    When method post
    Then status 400

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(wrongCode)' }
    When method post
    Then status 400

    # the attempt cap has burned the genuine code too
    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(realCode)' }
    When method post
    Then status 400

    # and the account stays unverified
    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 403

  Scenario: five wrong reset codes kill the real one and the password survives
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(EmailStore.lastCodeFor(email))' }
    When method post
    Then status 204

    Given path 'api/v1/auth/forgot-password'
    And request { email: '#(email)' }
    When method post
    Then status 204

    * def realCode = EmailStore.lastResetCodeFor(email)
    * def wrongCode = realCode == '000000' ? '111111' : '000000'

    Given path 'api/v1/auth/reset-password'
    And request { email: '#(email)', code: '#(wrongCode)', newPassword: 'attacker chosen secret!' }
    When method post
    Then status 400

    Given path 'api/v1/auth/reset-password'
    And request { email: '#(email)', code: '#(wrongCode)', newPassword: 'attacker chosen secret!' }
    When method post
    Then status 400

    Given path 'api/v1/auth/reset-password'
    And request { email: '#(email)', code: '#(wrongCode)', newPassword: 'attacker chosen secret!' }
    When method post
    Then status 400

    Given path 'api/v1/auth/reset-password'
    And request { email: '#(email)', code: '#(wrongCode)', newPassword: 'attacker chosen secret!' }
    When method post
    Then status 400

    Given path 'api/v1/auth/reset-password'
    And request { email: '#(email)', code: '#(wrongCode)', newPassword: 'attacker chosen secret!' }
    When method post
    Then status 400

    Given path 'api/v1/auth/reset-password'
    And request { email: '#(email)', code: '#(realCode)', newPassword: 'attacker chosen secret!' }
    When method post
    Then status 400

    # the original password still works — the attacker changed nothing
    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200

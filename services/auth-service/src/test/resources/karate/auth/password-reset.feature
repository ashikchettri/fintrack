Feature: Password reset — ADR 005 contract

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.reset.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'
    * def newPassword = 'completely different secret'
    * def EmailStore = Java.type('com.fintrack.auth.testsupport.RecordingEmailSender')

  Scenario: forgot → emailed 6-digit code → reset → old password dead, new works
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

    * def resetCode = EmailStore.lastResetCodeFor(email)
    * match resetCode == '#regex \\d{6}'

    Given path 'api/v1/auth/reset-password'
    And request { email: '#(email)', code: '#(resetCode)', newPassword: '#(newPassword)' }
    When method post
    Then status 204

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 401

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(newPassword)' }
    When method post
    Then status 200

  Scenario: forgot-password reveals nothing about unknown emails
    * def ghost = 'ghost.reset.' + java.lang.System.nanoTime() + '@example.com'
    Given path 'api/v1/auth/forgot-password'
    And request { email: '#(ghost)' }
    When method post
    Then status 204

  Scenario: wrong reset code is a generic 400 problem
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/forgot-password'
    And request { email: '#(email)' }
    When method post
    Then status 204

    * def wrongCode = EmailStore.lastResetCodeFor(email) == '000000' ? '111111' : '000000'
    Given path 'api/v1/auth/reset-password'
    And request { email: '#(email)', code: '#(wrongCode)', newPassword: '#(newPassword)' }
    When method post
    Then status 400
    And match response.title == 'Invalid reset code'

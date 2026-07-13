Feature: Email verification — ADR 004 contract

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.verify.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'
    * def EmailStore = Java.type('com.fintrack.auth.testsupport.RecordingEmailSender')

  Scenario: unverified login is a distinct 403 problem, then verification unlocks it
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 403
    And match header Content-Type contains 'application/problem+json'
    And match response.title == 'Email not verified'

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(EmailStore.lastCodeFor(email))' }
    When method post
    Then status 204

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200

  Scenario: wrong code is a generic 400 problem
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    * def wrongCode = EmailStore.lastCodeFor(email) == '000000' ? '111111' : '000000'
    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(wrongCode)' }
    When method post
    Then status 400
    And match response.title == 'Invalid verification code'

  Scenario: resend reveals nothing about unknown emails
    * def ghost = 'ghost.' + java.lang.System.nanoTime() + '@example.com'
    Given path 'api/v1/auth/resend-verification'
    And request { email: '#(ghost)' }
    When method post
    Then status 204

Feature: Change password — authenticated account management

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.changepw.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'
    * def newPassword = 'an entirely new secret'
    * def EmailStore = Java.type('com.fintrack.auth.testsupport.RecordingEmailSender')

  Scenario: change password, then only the new password logs in
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(EmailStore.lastCodeFor(email))' }
    When method post
    Then status 204

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200
    * def token = response.accessToken

    Given path 'api/v1/users/me/password'
    And header Authorization = 'Bearer ' + token
    And request { currentPassword: '#(validPassword)', newPassword: '#(newPassword)' }
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

  Scenario: wrong current password is a 400 problem carrying a traceId
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(EmailStore.lastCodeFor(email))' }
    When method post
    Then status 204

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200
    * def token = response.accessToken

    Given path 'api/v1/users/me/password'
    And header Authorization = 'Bearer ' + token
    And request { currentPassword: 'not-the-password', newPassword: '#(newPassword)' }
    When method post
    Then status 400
    And match response.title == 'Incorrect current password'
    And match response.traceId == '#string'
    And match responseHeaders['X-Request-Id'][0] == '#string'

  Scenario: change-password requires authentication
    Given path 'api/v1/users/me/password'
    And request { currentPassword: 'x', newPassword: '#(newPassword)' }
    When method post
    Then status 401

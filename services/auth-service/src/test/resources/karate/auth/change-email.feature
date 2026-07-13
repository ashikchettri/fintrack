Feature: Change email — authenticated, new address proven by code

  Background:
    * url baseUrl
    * def uniqueEmail = function(p){ return 'karate.' + p + '.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'
    * def EmailStore = Java.type('com.fintrack.auth.testsupport.RecordingEmailSender')

  Scenario: request → confirm with the code sent to the new address → login moves
    * def oldEmail = uniqueEmail('old')
    * def newEmail = uniqueEmail('new')

    Given path 'api/v1/auth/signup'
    And request { email: '#(oldEmail)', password: '#(validPassword)' }
    When method post
    Then status 201
    Given path 'api/v1/auth/verify-email'
    And request { email: '#(oldEmail)', code: '#(EmailStore.lastCodeFor(oldEmail))' }
    When method post
    Then status 204
    Given path 'api/v1/auth/login'
    And request { email: '#(oldEmail)', password: '#(validPassword)' }
    When method post
    Then status 200
    * def token = response.accessToken

    Given path 'api/v1/users/me/email'
    And header Authorization = 'Bearer ' + token
    And request { newEmail: '#(newEmail)', currentPassword: '#(validPassword)' }
    When method post
    Then status 204

    Given path 'api/v1/users/me/email/verify'
    And header Authorization = 'Bearer ' + token
    And request { code: '#(EmailStore.lastChangeCodeFor(newEmail))' }
    When method post
    Then status 204

    Given path 'api/v1/users/me'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 200
    And match response.email == newEmail

    Given path 'api/v1/auth/login'
    And request { email: '#(newEmail)', password: '#(validPassword)' }
    When method post
    Then status 200

  Scenario: change-email requires authentication
    Given path 'api/v1/users/me/email'
    And request { newEmail: 'x@example.com', currentPassword: 'y' }
    When method post
    Then status 401

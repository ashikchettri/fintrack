Feature: /users/me and API docs

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.me.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'
    * def EmailStore = Java.type('com.fintrack.auth.testsupport.RecordingEmailSender')

  Scenario: authenticated profile round-trip
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    # verify the mailbox (ADR 004) — code captured by the test-seam email sender
    Given path 'api/v1/auth/verify-email'
    And request { email: '#(email)', code: '#(EmailStore.lastCodeFor(email))' }
    When method post
    Then status 204

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200
    * def token = response.accessToken

    Given path 'api/v1/users/me'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 200
    And match response ==
      """
      {
        userId: '#uuid',
        email: '#(email)',
        householdId: '#uuid',
        householdName: '#string',
        role: 'OWNER',
        createdAt: '#string'
      }
      """

  Scenario: /users/me without a token is 401
    Given path 'api/v1/users/me'
    When method get
    Then status 401

  Scenario: OpenAPI docs are public and describe the auth endpoints
    Given path 'v3/api-docs'
    When method get
    Then status 200
    And match response.openapi == '#string'
    And match response.paths contains { '/api/v1/auth/login': '#object' }

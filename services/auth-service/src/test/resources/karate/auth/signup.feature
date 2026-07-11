Feature: Signup — POST /api/v1/auth/signup
  Black-box API contract tests over real HTTP: status codes, response shape,
  and RFC 9457 problem bodies as an external client would see them.

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'

  Scenario: successful signup creates an OWNER in a new household
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201
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

  Scenario: duplicate email (different case) is rejected with an RFC 9457 409
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/signup'
    And request { email: '#(email.toUpperCase())', password: '#(validPassword)' }
    When method post
    Then status 409
    And match header Content-Type contains 'application/problem+json'
    And match response contains { status: 409, title: 'Email already in use' }

  Scenario: invalid email is rejected with a field-level validation error
    Given path 'api/v1/auth/signup'
    And request { email: 'not-an-email', password: '#(validPassword)' }
    When method post
    Then status 400
    And match header Content-Type contains 'application/problem+json'
    And match response.title == 'Validation error'
    And match response.errors.email == '#string'

  Scenario: password shorter than 12 characters is rejected
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: 'short' }
    When method post
    Then status 400
    And match response.errors.password contains 'between 12 and 128'

  Scenario: missing body fields are rejected
    Given path 'api/v1/auth/signup'
    And request {}
    When method post
    Then status 400
    And match response.errors.email == '#string'
    And match response.errors.password == '#string'
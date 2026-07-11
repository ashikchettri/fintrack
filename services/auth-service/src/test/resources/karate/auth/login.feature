Feature: Login — POST /api/v1/auth/login and JWKS
  Black-box contract: token issuance, generic 401s, public key discovery.

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.login.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'

  Scenario: signup then login returns bearer tokens
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200
    And match response ==
      """
      {
        accessToken: '#string',
        tokenType: 'Bearer',
        expiresInSeconds: 900
      }
      """
    # RS256 JWT: three dot-separated base64url segments
    And match response.accessToken == '#regex [A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+'
    # refresh token arrives only as an httpOnly cookie (ADR 003)
    And match responseCookies['fintrack_refresh'].value == '#string'

  Scenario: the access token opens protected routes
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200
    * def token = response.accessToken

    # without a token: 401
    Given path 'api/v1/protected-probe'
    When method get
    Then status 401

    # with the token: authenticated, 404 (no handler there yet)
    Given path 'api/v1/protected-probe'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 404

  Scenario: wrong password and unknown email get identical 401 problems
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: 'wrong-password-entirely' }
    When method post
    Then status 401
    And match header Content-Type contains 'application/problem+json'
    * def wrongPasswordBody = response

    * def ghostEmail = 'ghost.' + java.lang.System.nanoTime() + '@example.com'
    Given path 'api/v1/auth/login'
    And request { email: '#(ghostEmail)', password: 'wrong-password-entirely' }
    When method post
    Then status 401
    And match response == wrongPasswordBody

  Scenario: JWKS is public and never leaks private key material
    Given path '.well-known/jwks.json'
    When method get
    Then status 200
    And match response.keys[0] contains { kty: 'RSA', kid: '#string', n: '#string', e: '#string' }
    And match response.keys[0] !contains { d: '#notnull' }

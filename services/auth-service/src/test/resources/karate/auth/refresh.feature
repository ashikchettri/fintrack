Feature: Refresh rotation, reuse detection, logout — cookie transport (ADR 003)
  Karate keeps a cookie jar per scenario, so the fintrack_refresh cookie flows
  automatically; replay attacks are simulated by pinning an old cookie value.

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.refresh.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'

  Scenario: full lifecycle — login, rotate, replay is rejected, family is dead
    # signup + login
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200
    And match response !contains { refreshToken: '#notnull' }
    * def firstRefresh = responseCookies['fintrack_refresh'].value

    # rotate: cookie jar auto-sends the cookie; a new value comes back
    Given path 'api/v1/auth/refresh'
    When method post
    Then status 200
    And match response contains { accessToken: '#string', tokenType: 'Bearer' }
    * def secondRefresh = responseCookies['fintrack_refresh'].value
    * assert firstRefresh != secondRefresh

    # replay the OLD cookie value → 401 problem
    * cookie fintrack_refresh = firstRefresh
    Given path 'api/v1/auth/refresh'
    When method post
    Then status 401
    And match header Content-Type contains 'application/problem+json'

    # reuse detection killed the successor too
    * cookie fintrack_refresh = secondRefresh
    Given path 'api/v1/auth/refresh'
    When method post
    Then status 401

  Scenario: logout clears the cookie and revokes the token
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200
    * def refreshValue = responseCookies['fintrack_refresh'].value

    Given path 'api/v1/auth/logout'
    When method post
    Then status 204

    # even if a copy of the pre-logout cookie is replayed, it is revoked
    * cookie fintrack_refresh = refreshValue
    Given path 'api/v1/auth/refresh'
    When method post
    Then status 401

  Scenario: refresh without any cookie is a generic 401
    Given path 'api/v1/auth/refresh'
    When method post
    Then status 401
    And match response.title == 'Invalid credentials'

  Scenario: logout without a cookie is silent
    Given path 'api/v1/auth/logout'
    When method post
    Then status 204

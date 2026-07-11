Feature: Refresh rotation, reuse detection, logout

  Background:
    * url baseUrl
    * def uniqueEmail = function(){ return 'karate.refresh.' + java.lang.System.nanoTime() + '@example.com' }
    * def validPassword = 'correct horse battery staple'

  Scenario: full lifecycle — login, rotate, replay is rejected, family is dead, logout
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
    * def firstRefresh = response.refreshToken

    # rotate: new pair comes back, tokens differ
    Given path 'api/v1/auth/refresh'
    And request { refreshToken: '#(firstRefresh)' }
    When method post
    Then status 200
    And match response contains { accessToken: '#string', tokenType: 'Bearer', refreshToken: '#string' }
    * def secondRefresh = response.refreshToken
    * assert firstRefresh != secondRefresh

    # replaying the rotated token → 401 problem
    Given path 'api/v1/auth/refresh'
    And request { refreshToken: '#(firstRefresh)' }
    When method post
    Then status 401
    And match header Content-Type contains 'application/problem+json'

    # reuse detection killed the successor too
    Given path 'api/v1/auth/refresh'
    And request { refreshToken: '#(secondRefresh)' }
    When method post
    Then status 401

  Scenario: logout revokes the refresh token
    * def email = uniqueEmail()
    Given path 'api/v1/auth/signup'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 201

    Given path 'api/v1/auth/login'
    And request { email: '#(email)', password: '#(validPassword)' }
    When method post
    Then status 200
    * def refreshToken = response.refreshToken

    Given path 'api/v1/auth/logout'
    And request { refreshToken: '#(refreshToken)' }
    When method post
    Then status 204

    Given path 'api/v1/auth/refresh'
    And request { refreshToken: '#(refreshToken)' }
    When method post
    Then status 401

  Scenario: logout never reveals whether a token existed
    Given path 'api/v1/auth/logout'
    And request { refreshToken: 'completely-made-up-token' }
    When method post
    Then status 204

function fn() {
  // baseUrl is injected by KarateApiTest (random port of the Spring Boot app
  // under test); the fallback allows running features against a local bootRun
  var config = {
    baseUrl: karate.properties['app.baseUrl'] || 'http://localhost:8081'
  };
  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 10000);
  return config;
}
# Heroku Procfile - JVM AOT Deployment with Java 25 for Fast Startup
web: java -Xmx300m -Xss512k -XX:CICompilerCount=2 -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=$PORT --enable-preview -jar target/quarkus-app/quarkus-run.jar

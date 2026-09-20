FROM eclipse-temurin:17-jre
COPY target/mall-search-*.jar /app.jar
ENTRYPOINT ["java","-Xms256m","-Xmx512m","-jar","/app.jar"]

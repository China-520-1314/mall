FROM eclipse-temurin:17-jre
COPY target/mall-admin-*.jar /app.jar
ENTRYPOINT ["java","-Xms256m","-Xmx512m","-jar","/app.jar"]

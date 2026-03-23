FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/thirdparty-demo-1.0.0.jar /app/app.jar

EXPOSE 8092

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

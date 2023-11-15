FROM openjdk:17
ENV TZ=Asia/Seoul
COPY build/libs/Equus-Sidecar-Proxy-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

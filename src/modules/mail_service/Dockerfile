FROM openjdk:17
ARG JAR_FILE=./build/libs/mail-0.0.1.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
FROM gradle:jdk17 AS BUILD_IMAGE

ENV APP_HOME /app

WORKDIR $APP_HOME

RUN mkdir src gradle

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src ./src

RUN gradle clean build -x test

FROM openjdk:17.0.2-slim-bullseye AS RUN_IMAGE

ENV APP_HOME /app

WORKDIR $APP_HOME

COPY --from=BUILD_IMAGE /app/build/libs/push.jar .

ENTRYPOINT java -jar ./push.jar



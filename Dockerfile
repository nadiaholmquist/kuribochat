# https://stackoverflow.com/questions/61108021/gradle-and-docker-how-to-run-a-gradle-build-within-docker-container
# Build stage

FROM gradle:8.1 AS BUILD
WORKDIR /usr/app/
COPY . .
RUN gradle build

# Package stage

FROM openjdk:20
ENV JAR_NAME=kuribochat-1.1.jar
ENV APP_HOME=/usr/app/
WORKDIR $APP_HOME
COPY --from=BUILD $APP_HOME/build/libs/$JAR_NAME .
ENTRYPOINT exec java -jar $JAR_NAME
FROM eclipse-temurin:21-jdk-alpine AS runtime-build
RUN apk add --no-cache binutils
WORKDIR /runtime-build
RUN jlink --no-header-files --no-man-pages --compress=zip-9 --strip-debug --add-modules java.base,java.logging,jdk.unsupported,java.sql,java.desktop --output runtime

FROM clojure:temurin-21-tools-deps-alpine AS app-build
WORKDIR /app-build

ADD deps.edn deps.edn
RUN echo Downloading Clojure build deps && clojure -Srepro -Stree -T:build
RUN echo Downloading Clojure app deps && clojure -Srepro -Stree 

COPY src src/
COPY build.clj .
RUN clojure -Srepro -Sverbose -T:build ci :uber-file target/app.jar

FROM alpine:3.19.1 AS app-base
SHELL ["/bin/sh", "-o", "pipefail", "-c"]
ENV APP_USER "app"
ENV APP_DIR "/${APP_USER}"
ENV DATA_DIR "${APP_DIR}/data"

RUN adduser -s /bin/true -u 1000 -D -h $APP_DIR $APP_USER \
  && mkdir "$DATA_DIR" \
  && chown -R "$APP_USER" "$APP_DIR" \
  && chmod 700 "$APP_DIR" "$DATA_DIR"

RUN apk add --no-cache ca-certificates

WORKDIR ${APP_DIR}

ENV PLAYDOH_PORT=8090
ENV MALLOC_ARENA_MAX=2
ENV JAVA_HOME="${APP_DIR}/runtime"
ENV JDK_JAVA_OPTIONS="-XshowSettings:system -XX:+UseContainerSupport -Xmx768m -Xms128m"
ENV JAVA_OPTS="-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory"

FROM app-base AS app

COPY --link --from=runtime-build /runtime-build/runtime runtime
COPY --link --from=app-build /app-build/target/app.jar .

CMD [ "runtime/bin/java", "-jar", "app.jar", "start" ]
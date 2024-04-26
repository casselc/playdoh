FROM clojure:temurin-21-tools-deps-alpine AS clj-builder
WORKDIR /opt

ADD deps.edn deps.edn
RUN echo Downloading Clojure build deps && clojure -Srepro -Stree -T:build
RUN echo Downloading Clojure app deps && clojure -Srepro -Stree 

COPY src src/
COPY build.clj .
RUN clojure -Srepro -Sverbose -T:build ci :uber-file target/app.jar

FROM ghcr.io/graalvm/native-image-community:21-muslib AS native-builder
RUN useradd --uid 10001 --no-create-home --home "/opt" -c "" --shell "/sbin/nologin" app
WORKDIR /opt
COPY --link --from=clj-builder /opt/target/app.jar .
RUN native-image -jar app.jar -o app --features=clj_easy.graal_build_time.InitClojureClasses --no-fallback --gc=serial -R:MinHeapSize=128m -R:MaxHeapSize=768m --strict-image-heap --static --libc=musl -march=native \
    -J-Dclojure.spec.skip.macros=true -J-Dclojure.compiler.direct-linking=true -J-Dtech.v3.datatype.graal-native=true  \
    -H:+ReportExceptionStackTraces --report-unsupported-elements-at-runtime --install-exit-handlers \
    --enable-http --verbose --initialize-at-build-time=org.slf4j.jul.JDK14LoggerFactory --initialize-at-run-time=casselc.playdoh.ui --allow-incomplete-classpath  && rm app.jar

FROM scratch AS app

EXPOSE 8090

ENV PLAYDOH_PORT=8090
ENV MALLOC_ARENA_MAX=2
ENV JDK_JAVA_OPTIONS="-XshowSettings:system -XX:+UseContainerSupport -Xmx768m -Xms128m"
ENV JAVA OPTS="-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory"

COPY --link --from=native-builder /etc/passwd /etc/passwd
COPY --link --from=native-builder --chown=10001 /opt /
USER app

CMD ["/app", "start"] 
# Lab 12 - Deployment. Build from the repository root:
#
#   docker build -f labs/12-deployment/Dockerfile -t helio-assistant:local .
#
# Two stages. The build stage has the JDK and builds the jar; the runtime stage has
# only a JRE and the jar, so the compiler, Maven, and the sources are not in the
# image that runs. Nothing secret is copied or set: secrets arrive at runtime.

# ---- build stage: JDK, Maven wrapper, sources -> the jar and lib/ ---------------------------
FROM eclipse-temurin:27-jdk AS build
WORKDIR /src
COPY . .
# The tests are the gate before this point (./mvnw verify); this stage packages.
ARG BUILD_ID=local
RUN ./mvnw -B -q -pl labs/12-deployment -am package -DskipTests -Dbuild.id=${BUILD_ID}

# ---- runtime stage: JRE, the jar, a non-root user -------------------------------------------
FROM eclipse-temurin:27-jre
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /src/labs/12-deployment/target/lab-12-deployment.jar /app/lab-12-deployment.jar
COPY --from=build /src/labs/12-deployment/target/lib/ /app/lib/

# Non-secret defaults only. APP_ENV is deliberately not set: the application refuses to
# start without it, so a deployment must say which environment it is.
ENV APP_PORT=8080 \
    APP_BIND_ADDRESS=0.0.0.0
EXPOSE 8080

USER 10001:10001

# Exec form, so the JVM is process 1 and receives SIGTERM directly. A shell form would
# put a shell in front, which does not forward the signal, and shutdown would not be graceful.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/lab-12-deployment.jar"]

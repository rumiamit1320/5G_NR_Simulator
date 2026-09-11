FROM gradle:8.9-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle :web-server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /opt/5g-nr-simulator
COPY --from=build /workspace/web-server/build/install/web-server/ ./
ENV PORT=8080
EXPOSE 8080
USER 10001
ENTRYPOINT ["/opt/5g-nr-simulator/bin/web-server"]

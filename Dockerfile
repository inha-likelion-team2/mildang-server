# 밀당 백엔드 — Railway 배포용
#
# Railway의 자동 빌드(Nixpacks)에 맡기지 않고 Dockerfile을 두는 이유:
# Spring Boot 4 + Java 21 조합에서 빌더가 JDK 버전을 잘못 고르면 «되던 게 안 되는» 상태가
# 되는데, 배포 당일에 그걸 디버깅할 시간이 없다. 여기 적어두면 로컬과 같은 환경으로 돈다.

# ---------- 빌드 ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 빌더 컨테이너는 메모리가 넉넉하지 않다. 상한을 안 주면 Gradle이 힙을 크게 잡았다가
# GC로 헛돌면서 «Building the image»에서 한 시간씩 멈춘다(2026-08-19에 실제로 겪음).
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1200m -Dorg.gradle.daemon=false -Dorg.gradle.parallel=false"

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src ./src
# 테스트는 로컬·CI에서 이미 돌았고, 배포 빌드에서 다시 돌리면 시간만 배로 든다.
#
# 예전엔 여기 앞에 `./gradlew dependencies`로 캐시를 데우는 줄이 있었는데 뺐다.
# 캐시가 살아 있을 때만 이득이고, Railway가 캐시를 비운 뒤에는 «전체 의존성 해석 →
# 다시 bootJar에서 해석»으로 같은 일을 두 번 하게 만들어 오히려 느려진다.
RUN ./gradlew bootJar --no-daemon -x test --no-build-cache

# ---------- 실행 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 루트로 돌리지 않는다
RUN useradd --system --create-home --shell /usr/sbin/nologin mildang
USER mildang

COPY --from=build /app/build/libs/*.jar app.jar

# Railway는 $PORT를 주입한다. 컨테이너 메모리에 맞춰 힙을 잡게 두고(고정값 금지),
# 한국 시간대를 박아둔다 — 05:00 KST 경계 배치가 여기에 걸려 있다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]

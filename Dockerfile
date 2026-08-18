# 밀당 백엔드 — Railway 배포용
#
# Railway의 자동 빌드(Nixpacks)에 맡기지 않고 Dockerfile을 두는 이유:
# Spring Boot 4 + Java 21 조합에서 빌더가 JDK 버전을 잘못 고르면 «되던 게 안 되는» 상태가
# 되는데, 배포 당일에 그걸 디버깅할 시간이 없다. 여기 적어두면 로컬과 같은 환경으로 돈다.

# ---------- 빌드 ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 먼저 받아 캐시에 남긴다 — 소스만 바뀌면 이 레이어를 다시 쓰지 않는다
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
# 테스트는 CI에서 이미 돌았고, 배포 빌드에서 다시 돌리면 시간만 배로 든다
RUN ./gradlew bootJar --no-daemon -x test

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

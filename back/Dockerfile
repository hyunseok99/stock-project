# test용 jar만 옮긴 경우 dockerfile
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# 로컬에서 빌드해서 올린 JAR 파일을 컨테이너 내부로 복사
# (실제 파일명에 맞게 수정하거나 *.jar로 지정)
COPY *.jar app.jar

# SQLite 데이터 저장 폴더 생성
RUN mkdir -p /app/data

# 실행 (메모리 제한 512MB 권장)
ENTRYPOINT ["java", "-Xmx512m", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
# Stage 1: Build the Java application using Eclipse Temurin 17 JDK
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy SQLite JDBC library and Java source code
COPY lib/ ./lib/
COPY src/ ./src/

# Compile Java application
RUN mkdir -p out && javac -cp "lib/sqlite-jdbc.jar" -d out src/TaskRouletteServer.java

# Stage 2: Runtime environment using Eclipse Temurin 17 JRE
FROM eclipse-temurin:17-jre

WORKDIR /app

# Create writable data directory for SQLite database
RUN mkdir -p /app/data && chmod -R 777 /app/data

# Copy compiled bytecode, SQLite driver, and static web frontend assets
COPY --from=builder /app/out ./out
COPY lib/ ./lib/
COPY static/ ./static/

# Environment configurations for Render
ENV PORT=8080
ENV DB_PATH=/app/data/taskroulette.db
ENV STATIC_DIR=/app/static

EXPOSE 8080

# Execute server with Linux classpath separator ':' and native access flag for SQLite JNI
CMD ["java", "-cp", "out:lib/sqlite-jdbc.jar", "--enable-native-access=ALL-UNNAMED", "TaskRouletteServer"]

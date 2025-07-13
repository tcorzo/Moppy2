# Multi-stage build for MoppyAPI server
# Stage 1: Build the application
FROM gradle:8-jdk21-alpine AS builder

# Set working directory
WORKDIR /build

# Copy the entire Java project structure
COPY . .

# Build the MoppyAPI project with shadow JAR
WORKDIR /build/MoppyAPI
RUN gradle shadowJar --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:21

# Install necessary packages for USB device access and ARM compatibility
RUN apt-get update && apt-get install -y \
    udev \
    libc6 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user for security
RUN groupadd -r moppy && useradd -r -g moppy moppy

# Add the moppy user to the dialout group for serial port access
RUN usermod -a -G dialout moppy

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /build/MoppyAPI/build/libs/MoppyAPI-2.2.0.jar ./moppy-api.jar

# Create directories for logs and configuration
RUN mkdir -p /app/logs /app/config && \
    chown -R moppy:moppy /app

# Switch to non-root user
USER moppy

# Expose the default API port
EXPOSE 8080

# Health check to ensure the API is running
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/api/health || exit 1

# Default command with configurable port and JVM options
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "moppy-api.jar"]
CMD ["--port", "8080"]

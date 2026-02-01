# Dev Containers base (Ubuntu 24.04) with vscode user already set up
FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

ARG MAVEN_VERSION=3.9.6
ARG NODE_VERSION=24.11.1
ARG NPM_VERSION=11.9.0
ARG ANGULAR_CLI_VERSION=21.0.1

# Basic utilities
RUN apt-get update && apt-get install -y \
    ca-certificates \
    curl \
    git \
    unzip \
    zip \
    xz-utils \
  && rm -rf /var/lib/apt/lists/*

# Java 25 (Temurin JDK) - installed to /opt/java
RUN mkdir -p /opt/java && \
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" \
  -o /tmp/jdk.tar.gz && \
  tar -xzf /tmp/jdk.tar.gz -C /opt/java --strip-components=1 && \
  rm /tmp/jdk.tar.gz

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Maven 3.9.6 - install to /opt/apache-maven-3.9.6 and link mvn into /usr/local/bin
RUN curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
  -o /tmp/maven.tgz && \
  tar -xzf /tmp/maven.tgz -C /opt && \
  ln -sf "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven && \
  ln -sf "/opt/maven/bin/mvn" /usr/local/bin/mvn && \
  rm /tmp/maven.tgz

# Node 24.11.1 + npm 11.9.0
RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
  -o /tmp/node.tar.xz && \
  tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 && \
  rm /tmp/node.tar.xz && \
  npm i -g "npm@${NPM_VERSION}"

# Angular CLI 21.0.1 (global)
RUN npm i -g "@angular/cli@${ANGULAR_CLI_VERSION}"

# Optional: Postgres CLI client (psql) for debugging (nice to have)
RUN apt-get update && apt-get install -y postgresql-client && rm -rf /var/lib/apt/lists/*

# Quick sanity print (won't fail build if versions differ slightly, but helpful in logs)
RUN java -version && mvn -version && node -v && npm -v && ng version


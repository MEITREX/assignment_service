# syntax=docker/dockerfile:experimental
FROM gradle:8-jdk21 AS build

# We set the WORKDIR to /workspace/backend/assignment_service
# This ensures that ../../common points exactly to /workspace/common
WORKDIR /workspace/backend/assignment_service

# 1. Copy the common library to /workspace/common
COPY common /workspace/common

# 2. Copy the assignment service files into the current WORKDIR
COPY backend/assignment_service/build.gradle backend/assignment_service/settings.gradle ./

# 3. Pre-load dependencies
RUN gradle clean build -x test || return 0

# 4. Copy the actual source code
COPY backend/assignment_service ./

# 5. Run the build
RUN gradle clean build -x test
RUN mkdir -p build/dependency && (cd build/dependency; jar -xf ../libs/*-SNAPSHOT.jar)

FROM eclipse-temurin:21-jdk
VOLUME /tmp
# Update the ARG path to match the new WORKDIR structure
ARG DEPENDENCY=/workspace/backend/assignment_service/build/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app
ENTRYPOINT ["java","-cp","app:app/lib/*","de.unistuttgart.iste.meitrex.assignment_service.AssignmentServiceApplication"]
---
phase: quick-1
plan: 01
subsystem: config
tags: [spring-boot, datasource, profiles, application-yaml]
---

## What Was Built

Added `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` to the default section of `application.yaml`. Without this, Spring Boot had no active profile and couldn't find the datasource URL (which was only defined under the `dev` and `prod` profile sections).

## Changes

- `src/main/resources/application.yaml`: Added default profile activation

## Verification

- App starts successfully with `mvn spring-boot:run` — dev profile activates automatically
- PostgreSQL connection established, Hibernate creates tables
- Tomcat starts on port 7070

# Installation Guide

## 1. Publish Starter

### Option A: Local Maven Repository (quick start)

```bash
git clone <repo-url> auth-starter
cd auth-starter
./mvnw clean install -DskipTests
```

Artifact goes to `~/.m2/repository`. Works only on your machine.

### Option B: GitHub Packages

**1) Add `distributionManagement` to starter's `pom.xml`:**

```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/OWNER/REPO</url>
    </repository>
</distributionManagement>
```

**2) Configure credentials in `~/.m2/settings.xml`:**

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password> <!-- ghp_... с правами write:packages -->
        </server>
    </servers>
</settings>
```

**3) Publish:**

```bash
./mvnw clean deploy -DskipTests
```

### Option C: Nexus / Artifactory

**1) Add `distributionManagement` to starter's `pom.xml`:**

```xml
<distributionManagement>
    <repository>
        <id>nexus-releases</id>
        <url>https://nexus.example.com/repository/maven-releases/</url>
    </repository>
    <snapshotRepository>
        <id>nexus-snapshots</id>
        <url>https://nexus.example.com/repository/maven-snapshots/</url>
    </snapshotRepository>
</distributionManagement>
```

**2) Configure credentials in `~/.m2/settings.xml`:**

```xml
<settings>
    <servers>
        <server>
            <id>nexus-releases</id>
            <username>admin</username>
            <password>your-password</password>
        </server>
        <server>
            <id>nexus-snapshots</id>
            <username>admin</username>
            <password>your-password</password>
        </server>
    </servers>
</settings>
```

**3) Publish:**

```bash
# SNAPSHOT version (0.0.1-SNAPSHOT) -> snapshots repo
./mvnw clean deploy -DskipTests

# Release version (убрать -SNAPSHOT из pom.xml) -> releases repo
./mvnw clean deploy -DskipTests
```

---

## 2. Add Dependency in Your Project

### Maven

**pom.xml — dependency:**
```xml
<dependency>
    <groupId>kz.innlab</groupId>
    <artifactId>auth-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

**pom.xml — repository (skip if using local `~/.m2`):**
```xml
<repositories>
    <!-- GitHub Packages -->
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/OWNER/REPO</url>
    </repository>

    <!-- OR Nexus -->
    <repository>
        <id>nexus-snapshots</id>
        <url>https://nexus.example.com/repository/maven-snapshots/</url>
    </repository>
</repositories>
```

### Gradle (Kotlin DSL)

**build.gradle.kts:**
```kotlin
repositories {
    mavenCentral()
    mavenLocal() // если установлен локально

    // GitHub Packages
    maven {
        url = uri("https://maven.pkg.github.com/OWNER/REPO")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }

    // OR Nexus / Artifactory
    // maven("https://nexus.example.com/repository/maven-snapshots/")
}

dependencies {
    implementation("kz.innlab:auth-spring-boot-starter:0.0.1-SNAPSHOT")
}
```

### Gradle (Groovy DSL)

**build.gradle:**
```groovy
repositories {
    mavenCentral()
    mavenLocal()

    // GitHub Packages
    maven {
        url = uri('https://maven.pkg.github.com/OWNER/REPO')
        credentials {
            username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_USERNAME')
            password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
        }
    }

    // OR Nexus / Artifactory
    // maven { url 'https://nexus.example.com/repository/maven-snapshots/' }
}

dependencies {
    implementation 'kz.innlab:auth-spring-boot-starter:0.0.1-SNAPSHOT'
}
```

> **Gradle + GitHub Packages credentials** — добавь в `~/.gradle/gradle.properties`:
> ```properties
> gpr.user=YOUR_GITHUB_USERNAME
> gpr.token=ghp_YOUR_TOKEN
> ```

---

## 3. Configure `application.yaml`

### Minimal (Dev)

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    username: postgres
    password: postgres
  flyway:
    enabled: true
    locations: classpath:db/migration/auth
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate

app:
  auth:
    local:
      enabled: true       # email + password
    google:
      enabled: false
    apple:
      enabled: false
    phone:
      enabled: false
  firebase:
    enabled: false
  mail:
    enabled: false
  cors:
    allowed-origins:
      - http://localhost:3000
```

This gives you working JWT auth with email+password registration and login. RSA keys are generated in-memory, verification codes are logged to console (`123456`).

### Full (Production)

```yaml
server:
  port: 8080

spring:
  profiles:
    active: prod
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  flyway:
    enabled: true
    locations: classpath:db/migration/auth
    baseline-on-migrate: false
    validate-on-migrate: true
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate

app:
  # --- Auth providers ---
  auth:
    local:
      enabled: true
    google:
      enabled: true
      client-id: ${GOOGLE_CLIENT_ID}
    apple:
      enabled: true
      bundle-id: ${APPLE_BUNDLE_ID}
    phone:
      enabled: true
    refresh-token:
      expiry-days: 30

  # --- JWT keystore (required for prod) ---
  security:
    jwt:
      keystore-location: ${JWT_KEYSTORE_LOCATION}
      keystore-password: ${JWT_KEYSTORE_PASSWORD}
      key-alias: ${JWT_KEY_ALIAS:jwt}

  # --- CORS ---
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}

  # --- Firebase push notifications ---
  firebase:
    enabled: true
    # Set env: FIREBASE_CREDENTIALS_PATH=/path/to/service-account.json

  # --- Email (SMTP sending + IMAP receiving) ---
  mail:
    enabled: true
    smtp:
      host: ${SMTP_HOST}
      port: ${SMTP_PORT:587}
      username: ${SMTP_USERNAME}
      password: ${SMTP_PASSWORD}
      from: ${SMTP_FROM:noreply@example.com}
      ssl-enabled: ${SMTP_SSL_ENABLED:false}
    imap:
      host: ${IMAP_HOST}
      port: ${IMAP_PORT:993}
      username: ${IMAP_USERNAME}
      password: ${IMAP_PASSWORD}
      ssl-enabled: ${IMAP_SSL_ENABLED:true}
    retry:
      max-attempts: 3
      delay-ms: 5000

  # --- Notifications ---
  notification:
    token:
      max-per-user: 5
```

---

## 4. Property Reference

### Auth Providers

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.auth.local.enabled` | boolean | `true` | Email + password registration/login |
| `app.auth.google.enabled` | boolean | `false` | Google OAuth2 login |
| `app.auth.google.client-id` | string | — | Google OAuth2 client ID |
| `app.auth.apple.enabled` | boolean | `true` | Apple Sign In |
| `app.auth.apple.bundle-id` | string | `com.example.app` | Apple app bundle ID |
| `app.auth.phone.enabled` | boolean | `true` | Phone + SMS OTP login |
| `app.auth.refresh-token.expiry-days` | int | `30` | Refresh token TTL in days |

### JWT (Production Only)

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `app.security.jwt.keystore-location` | string | Yes | Path to PKCS12 keystore file |
| `app.security.jwt.keystore-password` | string | Yes | Keystore password |
| `app.security.jwt.key-alias` | string | No (`jwt`) | Key alias in keystore |

Generate keystore:
```bash
keytool -genkey -alias jwt -keyalg RSA -keysize 2048 \
  -keystore jwt.p12 -storetype PKCS12 -validity 3650
```

### Firebase

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.firebase.enabled` | boolean | `false` | Enable Firebase push notifications |

Set `FIREBASE_CREDENTIALS_PATH` env variable pointing to the service account JSON file.

### Email

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.mail.enabled` | boolean | `false` | Enable mail service |
| `app.mail.smtp.host` | string | — | SMTP server host |
| `app.mail.smtp.port` | int | `587` | SMTP server port |
| `app.mail.smtp.username` | string | — | SMTP username |
| `app.mail.smtp.password` | string | — | SMTP password |
| `app.mail.smtp.from` | string | `noreply@example.com` | Sender email address |
| `app.mail.smtp.ssl-enabled` | boolean | `false` | Enable SSL for SMTP |
| `app.mail.imap.host` | string | — | IMAP server host |
| `app.mail.imap.port` | int | `993` | IMAP server port |
| `app.mail.imap.username` | string | — | IMAP username |
| `app.mail.imap.password` | string | — | IMAP password |
| `app.mail.imap.ssl-enabled` | boolean | `true` | Enable SSL for IMAP |
| `app.mail.retry.max-attempts` | int | `3` | Max retry attempts for failed sends |
| `app.mail.retry.delay-ms` | long | `5000` | Delay between retries (ms) |

### CORS

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.cors.allowed-origins` | list | localhost | Allowed CORS origins |

### Notifications

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.notification.token.max-per-user` | int | `5` | Max device tokens per user |

### Dev-Only

| Property | Type | Description |
|----------|------|-------------|
| `app.auth.sms.dev-code` | string | Fixed SMS code for dev (e.g. `123456`) |
| `app.auth.verification.dev-code` | string | Fixed email verification code for dev |

---

## 5. Override Default Services

The starter provides console-logging defaults for SMS, email, and push. Override them by declaring your own beans:

```kotlin
@Configuration
class MyServiceOverrides {

    // SMS provider (e.g., Twilio)
    @Bean
    fun smsService(): SmsService = object : SmsService {
        override fun sendCode(phone: String, code: String) {
            // your implementation
        }
    }

    // Email verification sender (e.g., SendGrid)
    @Bean
    fun emailService(): EmailService = object : EmailService {
        override fun sendCode(to: String, code: String, purpose: String) {
            // your implementation
        }
    }
}
```

The default implementations use `@ConditionalOnMissingBean`, so your beans take priority automatically.

---

## 6. Database

The starter requires **PostgreSQL**. Flyway migrations are bundled and create all tables on first startup.

Make sure your `application.yaml` includes:
```yaml
spring:
  flyway:
    locations: classpath:db/migration/auth
```

Tables created: `users`, `user_providers`, `user_provider_ids`, `user_roles`, `refresh_tokens`, `sms_verifications`, `verification_codes`, `device_tokens`, `notification_history`, `notification_topics`, `notification_preferences`, `mail_history`.

---

## Quick Checklist

- [ ] Add Maven/Gradle dependency
- [ ] Configure PostgreSQL datasource
- [ ] Set `spring.flyway.locations: classpath:db/migration/auth`
- [ ] Choose which auth providers to enable (`app.auth.*`)
- [ ] Set CORS origins (`app.cors.allowed-origins`)
- [ ] (Prod) Generate JWT keystore and set `app.security.jwt.*`
- [ ] (Optional) Implement `SmsService` for real SMS delivery
- [ ] (Optional) Implement `EmailService` for real email delivery
- [ ] (Optional) Configure Firebase for push notifications
- [ ] (Optional) Configure SMTP/IMAP for email service

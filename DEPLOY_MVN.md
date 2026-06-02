# Deploy to Maven Central

Quick reference. Full background: `INSTALL_INSTRUCTION.md` §1 Option A.

## One-time setup (already done — don't repeat)

- Sonatype Central account, namespace `kz.innlab` verified via DNS TXT on `innlab.kz`
- GPG key `D613F472A9A59A52` published to keyservers
- `~/.m2/settings.xml` has:
  - `<server id="central">` with Sonatype user token
  - `<profile id="gpg">` with `gpg.keyname` + `gpg.passphrase`, `activeByDefault=true`
- `pom.xml` profile `release` configured with `central-publishing-maven-plugin` + `maven-gpg-plugin` (`--pinentry-mode loopback`)

## Per-release steps

### 1. Bump version

Edit `pom.xml`:
```xml
<version>0.0.X</version>   <!-- no -SNAPSHOT, Maven Central rejects snapshots -->
```

Bump version refs in `INSTALL_INSTRUCTION.md`:
```bash
sed -i '' 's/0\.0\.PREV/0.0.X/g' INSTALL_INSTRUCTION.md
```

### 2. Environment

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 24)"   # JDK 24 only — Kotlin 2.2.x breaks on 25/26
export GPG_TTY=$(tty)                                 # needed for gpg pinentry loopback
```

### 3. Deploy

```bash
./mvnw clean deploy -P release -DskipTests
```

Uploads signed artifacts to Sonatype staging.

### 4. Manual publish

https://central.sonatype.com/publishing/deployments

Find `kz.innlab:auth-spring-boot-starter:0.0.X`. Status flow:

- `PENDING` — Sonatype validating (POM, sigs, javadoc, sources)
- `VALIDATED` — click **Publish**
- `PUBLISHING` — Sonatype pushing to Central
- `PUBLISHED` — wait propagation
- `FAILED` — fix errors → bump patch → re-deploy (cannot re-use failed version)

### 5. Verify

```bash
# Available on Central CDN (15-30 min after PUBLISHED)
curl -I https://repo1.maven.org/maven2/kz/innlab/auth-spring-boot-starter/0.0.X/auth-spring-boot-starter-0.0.X.pom
# 200 = ready, 404 = wait

# mvnrepository.com indexes 1-24h later (third-party mirror)
open https://mvnrepository.com/artifact/kz.innlab/auth-spring-boot-starter
```

### 6. Git tag

```bash
git add pom.xml INSTALL_INSTRUCTION.md src/
git commit -m "release: 0.0.X — <short summary>"
git tag v0.0.X
git push && git push --tags
```

## Common failures

| Error | Cause | Fix |
|-------|-------|-----|
| `repository element was not specified in the POM` | Forgot `-P release` | `./mvnw clean deploy -P release -DskipTests` |
| `gpg: Note: database_open waiting for lock (held by PID)` | Stale gpg keybox lock from dead PID | `rm -f ~/.gnupg/public-keys.d/*.lock && gpgconf --kill all` |
| `gpg: signing failed: Inappropriate ioctl for device` | Pinentry can't reach TTY | `export GPG_TTY=$(tty)` before deploy |
| `Failed to deploy: version already exists` | Re-publishing same version | Bump patch — Maven Central forbids overwrite |
| `Unsupported class file major version` | JDK 25/26 with Kotlin 2.2 | `export JAVA_HOME="$(/usr/libexec/java_home -v 24)"` |
| Sonatype validation: `missing javadoc` | Plugin not bound | Verify `release` profile has `maven-javadoc-plugin` |
| Sonatype validation: `signature missing` | GPG sign step skipped | Check `maven-gpg-plugin` bound to `verify` phase in `release` profile |

## TL;DR

```bash
# 1. bump <version> in pom.xml
# 2.
export JAVA_HOME="$(/usr/libexec/java_home -v 24)"
export GPG_TTY=$(tty)
./mvnw clean deploy -P release -DskipTests
# 3. https://central.sonatype.com/publishing/deployments → click Publish
# 4. wait 15-30 min, verify with curl
# 5. git tag + push
```

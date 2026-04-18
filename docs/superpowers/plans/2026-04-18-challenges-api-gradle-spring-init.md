# Challenges API — Gradle + Spring Boot + Java 25 Init

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn this repo into a runnable **Spring Boot 4** **JSON REST API** project using **Gradle** (Groovy DSL) and a **Java 25** toolchain, without implementing challenge domain features yet—only a healthy skeleton that compiles, tests pass, and project docs list the real stack.

**Out of scope for this repo:** The **React** web UI (and any other separate SPA). That client will be built elsewhere and will call this API over HTTP. This repository is **backend only**.

Also **not** added here: server-side HTML/templates (Thymeleaf, JSP, etc.). APIs are **JSON only** via **`@RestController`**—you use **Spring Web MVC**’s mapping and dispatching without view resolution.

**Architecture:** Bootstrap from **Spring Initializr** with the **Spring Web MVC** stack (`spring-boot-starter-webmvc` in Spring Boot 4). Embedded **Tomcat** (Servlet). REST endpoints use **`@RestController`** returning JSON—no WebFlux. Package base: **`com.challenges.api`**.

**Tech Stack:** Java 25 (toolchain), Spring Boot **4.0.5**, Gradle **9.4.1** (via wrapper from Initializr), JUnit 5, `spring-boot-starter-webmvc` / `spring-boot-starter-webmvc-test`.

**Prerequisite:** JDK 25 installed locally (`java -version` shows 25.x). Spring Boot **4.0.x** requires Java 17+ and supports Java up through **26** per [system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html).

---

## File map (after completion)

| Path | Role |
|------|------|
| `build.gradle` | Spring Boot plugin, Java 25 toolchain, **Spring Web MVC** (`webmvc`) dependencies |
| `settings.gradle` | Root project name `challenges-api` |
| `gradle/wrapper/*`, `gradlew`, `gradlew.bat` | Locked Gradle version |
| `src/main/java/com/challenges/api/ChallengesApiApplication.java` | `@SpringBootApplication` entrypoint |
| `src/main/resources/application.properties` | Placeholder config |
| `src/test/java/com/challenges/api/ChallengesApiApplicationTests.java` | `contextLoads` smoke test |
| `.gitignore`, `.gitattributes` | Standard Spring Boot ignores |
| `HELP.md` | Spring Boot reference links (from Initializr) |
| `AGENTS.md` | **Modify** — fill Stack section (keep domain glossary) |

Root-level `CLAUDE.md` / `AGENTS.md` stay in place; Initializr does not overwrite them if you merge carefully (no filename collision).

---

### Task 1: Generate project from Spring Initializr (authoritative layout)

**Files:**

- Create: (entire tree from zip — see list below)
- Modify: none yet (merge next task)

**Initializr URL** (single line; all parameters required for a reproducible zip):

```
https://start.spring.io/starter.zip?type=gradle-project&language=java&bootVersion=4.0.5&javaVersion=25&groupId=com.challenges.api&artifactId=challenges-api&name=challenges-api&packageName=com.challenges.api&dependencies=web
```

- [ ] **Step 1: Download the zip**

From the **repository root** (parent of `docs/`), run:

```bash
curl -fsSL -O 'https://start.spring.io/starter.zip?type=gradle-project&language=java&bootVersion=4.0.5&javaVersion=25&groupId=com.challenges.api&artifactId=challenges-api&name=challenges-api&packageName=com.challenges.api&dependencies=web'
```

Expected: file `starter.zip` appears in the current directory; size on the order of tens of KB.

If `curl` fails (TLS, proxy), open the URL in a browser, save as `starter.zip`, and continue.

- [ ] **Step 2: Unzip without deleting existing docs**

Still at repo root:

```bash
unzip -o starter.zip
```

Expected: `build.gradle`, `settings.gradle`, `gradlew`, `gradle/`, `src/`, `HELP.md`, `.gitignore`, `.gitattributes` appear. Existing `AGENTS.md` and `CLAUDE.md` remain untouched (no overlap in filenames).

- [ ] **Step 3: Remove the archive**

```bash
rm -f starter.zip
```

- [ ] **Step 4: Confirm `build.gradle` matches Spring Boot 4 Spring Web MVC (JSON REST, no WebFlux)**

Open `build.gradle`. It must contain **Java 25** toolchain and **`spring-boot-starter-webmvc`** (Spring Boot 4 naming for the Servlet REST stack). Do **not** add `spring-boot-starter-webflux`—this project uses **Spring Web MVC** only. Reference content:

```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.0.5'
	id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.challenges.api'
version = '0.0.1-SNAPSHOT'

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
	useJUnitPlatform()
}
```

If your unzip differs only by whitespace, that is fine. If dependencies show `webflux`, replace with `webmvc` / `webmvc-test` as above.

- [ ] **Step 5: Commit**

```bash
git add build.gradle settings.gradle gradlew gradlew.bat gradle/ src/ HELP.md .gitignore .gitattributes
git commit -m "chore: initialize Spring Boot 4 Web MVC REST API with Gradle and Java 25"
```

---

### Task 2: Run the build and tests locally

**Files:** none (verification only)

- [ ] **Step 1: Run tests**

```bash
chmod +x gradlew
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`; tests include `ChallengesApiApplicationTests.contextLoads`.

- [ ] **Step 2: Run the app (optional smoke)**

```bash
./gradlew bootRun --no-daemon
```

Expected: Spring banner, embedded **Tomcat** listens on default port **8080**. Stop with **Ctrl+C**.

---

### Task 3: Align project memory with the real stack

**Files:**

- Modify: [`AGENTS.md`](../../../AGENTS.md) (path from plan file: repo root `AGENTS.md`)

- [ ] **Step 1: Replace the Stack placeholder**

In `AGENTS.md`, under **Repository and stack** → **Stack (fill in when the project is initialized)**, set concrete values, for example:

```markdown
### Stack

| Item | Value |
|------|--------|
| Language | Java **25** |
| Framework | Spring Boot **4.0.5** |
| Build | **Gradle** 9.x (wrapper **9.4.1** — see `gradle-wrapper.properties`) |
| HTTP | **Spring Web MVC** — JSON REST (`spring-boot-starter-webmvc`). **React** SPA is **out of scope** in this repo (separate client). |
| Test | JUnit 5 (`spring-boot-starter-webmvc-test`) |
| Run | `./gradlew bootRun` |
| Test cmd | `./gradlew test` |
```

Keep (or add) a **Scope** note: **React UI** and server-rendered templates are not built here; only the REST API.

Mirror the same facts in `CLAUDE.md` if that file still says "TBD" for stack.

- [ ] **Step 2: Commit**

```bash
git add AGENTS.md CLAUDE.md
git commit -m "docs: document Java 25, Spring Boot 4, and Gradle stack"
```

(If you did not edit `CLAUDE.md`, run `git add` only for files you changed.)


---

## Execution handoff

**Plan complete and saved to** [`docs/superpowers/plans/2026-04-18-challenges-api-gradle-spring-init.md`](2026-04-18-challenges-api-gradle-spring-init.md).

**Two execution options:**

1. **Subagent-driven (recommended)** — One subagent per task with review between tasks; use **superpowers:subagent-driven-development**.

2. **Inline execution** — Run tasks sequentially in this session with checkpoints; use **superpowers:executing-plans**.

**Which approach do you want?**

---

## Note: Gradle Kotlin DSL

This plan uses **Groovy** `build.gradle` from Spring Initializr. If you prefer **`build.gradle.kts`**, migrate in a follow-up: keep the same plugins, toolchain, and dependencies; delete `build.gradle` after `settings.gradle` references Kotlin DSL (see Spring Boot Gradle Plugin docs).

## Note: Spring Web MVC without a server-side UI

**Spring Web MVC** is the right stack for a classic Servlet-based **JSON REST API** (`@RestController`). The **“V”** (views) is unused: no Thymeleaf/JSP—responses are JSON. The **React** app is a separate project and **out of scope** here; it consumes this API as HTTP + JSON.

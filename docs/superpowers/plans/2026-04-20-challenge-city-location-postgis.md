# Challenge city + PostGIS location Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional **`city`** (text) and **`location`** as **`geography(Point,4326)`** on **`challenges`**, exposed on create/replace/get/list responses; enforce validation rule **C** (if any location field is present, **both** latitude and longitude must be present; **city** optional). Prepare for **nearby** queries with a **GiST** index. **PostgreSQL + PostGIS only** (H2 removed).

**Architecture:** Flyway enables **`postgis`** and adds **`city`** + **`location geography(Point,4326)`** (nullable). **Hibernate Spatial** maps **`org.locationtech.jts.geom.Point`** (SRID **4326**, **longitude/latitude** order in JTS `Coordinate(x,y)`). **ChallengeRequest** / **ChallengeResponse** use a nested optional **`location: { latitude, longitude }`** plus optional **`city`** for JSON clarity. **ChallengeService** applies rule **C** before save. **ChallengeController** uses **`@GetMapping("/{id:\\d+}")`** (and same for PUT/DELETE) so **`GET /api/challenges/nearby`** can be added later without `"nearby"` being parsed as an id. **Phase B** adds **`GET /api/challenges/nearby`** with **`ST_DWithin`** + distance sort.

**Tech stack:** Java 25, Spring Boot 4.0.x, Hibernate ORM 6 + **Hibernate Spatial**, **PostGIS** on PostgreSQL, Flyway, JUnit 5, JTS (`org.locationtech.jts`).

**Coordinate convention:** Persist and document **WGS‑84 (EPSG:4326)**. JTS **`Coordinate`**: **`x = longitude`**, **`y = latitude`**. JSON can use **`latitude` / `longitude`** names for API clarity; map to JTS when persisting.

---

## File map

| Path | Responsibility |
|------|----------------|
| `build.gradle` | Add **`org.hibernate.orm:hibernate-spatial`** (version **aligned with** resolved **`hibernate-core`** from Spring Boot BOM — see Task 1). |
| `src/main/resources/application.yml` | Set **`spring.jpa.database-platform`** (or **`hibernate.dialect`**) to **`org.hibernate.spatial.dialect.postgis.PostgisDialect`** if auto-detection fails; verify against startup logs. |
| `src/main/resources/db/migration/V15__challenge_city_location_postgis.sql` | **`CREATE EXTENSION IF NOT EXISTS postgis;`** — `ALTER TABLE challenges ADD city`, **`ADD location geography(Point,4326)`**; **`CREATE INDEX`** **`GIST`** on **`location`** **WHERE location IS NOT NULL** (partial index). |
| `src/main/java/com/challenges/api/model/Challenge.java` | **`city`** (`String`, nullable); **`location`** (`Point`, nullable) with Hibernate Spatial mapping + column definition **`geography(Point,4326)`**. |
| `src/main/java/com/challenges/api/web/dto/ChallengeLocationDto.java` | New small record: **`latitude`**, **`longitude`** (both **`Double`**) for JSON — optional nested object. |
| `src/main/java/com/challenges/api/web/dto/ChallengeRequest.java` | Optional **`city`**, optional **`ChallengeLocationDto location`**. |
| `src/main/java/com/challenges/api/web/dto/ChallengeResponse.java` | Optional **`city`**, optional **`ChallengeLocationDto location`**; extend **`from(Challenge, ...)`**. |
| `src/main/java/com/challenges/api/service/ChallengeService.java` | **`applyLocationFromRequest(Challenge, ChallengeRequest)`**: rule **C**; build JTS **`Point`** with **`GeometryFactory`** SRID 4326; clear **`city`/`location`** when all absent. |
| `src/main/java/com/challenges/api/support/ChallengeLocationMapping.java` | Optional static helpers: DTO ↔ JTS **`Point`**, validate **[-90,90]**, **[-180,180]**. |
| `src/main/java/com/challenges/api/web/ChallengeController.java` | Restrict path **`/{id}`** → **`/{id:\\d+}`** on get/put/delete; list unchanged. |
| `src/main/java/com/challenges/api/repo/ChallengeRepository.java` | **Phase B:** native **`@Query`** with **`ST_DWithin`** / **`ST_Distance`** (or Spring Data Spatial if adopted). |
| Tests under `src/test/java/...` | Update **`ChallengeControllerIT`** (or equivalent) for JSON round-trip; repository or **@DataJpaTest** for persistence of **`geography`**; adjust fixtures only if constructors change. |

---

## Phase A — Schema, mapping, API (do first)

### Task 1: Gradle + Hibernate Spatial dialect

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Resolve Hibernate ORM version**

Run (from repo root; requires working Gradle):

```bash
./gradlew dependencyInsight --dependency org.hibernate.orm:hibernate-core --configuration compileClasspath
```

Note the **selected** version (e.g. `6.6.x.Final`).

- [ ] **Step 2: Add matching `hibernate-spatial`**

In the `dependencies` block add (replace **`6.6.x.Final`** with the version from Step 1):

```gradle
implementation 'org.hibernate.orm:hibernate-spatial:6.6.x.Final'
```

`jts-core` is typically pulled transitively; if the build complains, add:

```gradle
implementation 'org.locationtech.jts:jts-core:1.20.0'
```

(Align **`jts-core`** minor with what **`hibernate-spatial`** expects — check Gradle’s dependency tree.)

- [ ] **Step 3: Configure PostGIS dialect**

In `src/main/resources/application.yml` under `spring.jpa`:

```yaml
  jpa:
    database-platform: org.hibernate.spatial.dialect.postgis.PostgisPG10Dialect
```

(Hibernate ORM 7.x: `PostgisDialect` was removed; use **`PostgisPG10Dialect`** for PostgreSQL 10+.)

If Spring Boot 4 auto-selects a dialect and startup works **without** this line, you may omit it — but PostGIS **`geography`** often **requires** the spatial dialect; keep it unless you verify clean **`ddl-auto: validate`** against **`geography`**.

- [ ] **Step 4: Commit**

```bash
git add build.gradle src/main/resources/application.yml
git commit -m "build: add Hibernate Spatial for PostGIS geography"
```

---

### Task 2: Flyway — PostGIS + columns + index

**Files:**
- Create: `src/main/resources/db/migration/V15__challenge_city_location_postgis.sql`

- [ ] **Step 1: Migration SQL**

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE challenges
    ADD COLUMN city VARCHAR(255) NULL,
    ADD COLUMN location geography(Point, 4326) NULL;

CREATE INDEX idx_challenges_location_gist ON challenges USING GIST (location)
    WHERE location IS NOT NULL;
```

**Note:** **`CREATE EXTENSION`** needs a DB role with permission (local dev + CI test DB must allow it). If production uses a restricted user, run extension creation via admin migration or platform hook.

- [ ] **Step 2: Apply on test DB**

```bash
./gradlew prepareTestDatabase test --tests 'com.challenges.api.repo.ChallengeRepositoryTest'
```

(Full suite acceptable; fix checksum issues with Flyway repair/clean as usual.)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V15__challenge_city_location_postgis.sql
git commit -m "feat(db): challenge city + geography(Point,4326) + GiST index"
```

---

### Task 3: `Challenge` entity + JTS `Point`

**Files:**
- Modify: `src/main/java/com/challenges/api/model/Challenge.java`

- [ ] **Step 1: Fields**

Add imports:

```java
import org.locationtech.jts.geom.Point;
```

Add fields (adjust annotations if your Hibernate Spatial version prefers **`@JdbcTypeCode`** — follow compile errors and [Hibernate Spatial 6 user guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html)):

```java
@Column(length = 255)
private String city;

@Column(columnDefinition = "geography(Point,4326)")
private Point location;
```

Add getters/setters **`getCity`/`setCity`**, **`getLocation`/`setLocation`**.

- [ ] **Step 2: Run Hibernate validate**

Start app or run a JPA test with **`ddl-auto: validate`** — fix mapping until the session factory starts.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/challenges/api/model/Challenge.java
git commit -m "feat(challenge): city + geography location on entity"
```

---

### Task 4: DTOs + mapping helper

**Files:**
- Create: `src/main/java/com/challenges/api/web/dto/ChallengeLocationDto.java`
- Create: `src/main/java/com/challenges/api/support/ChallengeLocationMapping.java`
- Modify: `src/main/java/com/challenges/api/web/dto/ChallengeRequest.java`
- Modify: `src/main/java/com/challenges/api/web/dto/ChallengeResponse.java`

- [ ] **Step 1: `ChallengeLocationDto`**

```java
package com.challenges.api.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ChallengeLocationDto(
		@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
		@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {}
```

**Validation:** **`latitude`** ∈ **[-90, 90]**, **`longitude`** ∈ **[-180, 180]**. The mapper uses JTS **`(x=longitude, y=latitude)`** regardless of JSON field order.

- [ ] **Step 2: `ChallengeLocationMapping`** — core logic

```java
package com.challenges.api.support;

import com.challenges.api.web.dto.ChallengeLocationDto;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class ChallengeLocationMapping {

	private static final GeometryFactory GEOMETRY_FACTORY =
			new GeometryFactory(new PrecisionModel(), 4326);

	private ChallengeLocationMapping() {}

	public static Point toPoint(ChallengeLocationDto dto) {
		double lat = dto.latitude();
		double lon = dto.longitude();
		// JTS: x = longitude, y = latitude
		return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
	}

	public static ChallengeLocationDto fromPoint(Point point) {
		if (point == null || point.isEmpty()) {
			return null;
		}
		double lon = point.getX();
		double lat = point.getY();
		return new ChallengeLocationDto(lat, lon);
	}
}
```

- [ ] **Step 3: Extend `ChallengeRequest`**

Add optional fields (use **`@Nullable`** / **`Optional`** pattern matching existing style):

```java
		@Nullable String city,
		@Valid @Nullable ChallengeLocationDto location)
```

Import **`jakarta.validation.Valid`**.

- [ ] **Step 4: Extend `ChallengeResponse`**

Add **`String city`**, **`ChallengeLocationDto location`** to the record; in **`from(Challenge c, ...)`** set **`c.getCity()`**, **`ChallengeLocationMapping.fromPoint(c.getLocation())`**.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/challenges/api/web/dto/ChallengeLocationDto.java \
  src/main/java/com/challenges/api/support/ChallengeLocationMapping.java \
  src/main/java/com/challenges/api/web/dto/ChallengeRequest.java \
  src/main/java/com/challenges/api/web/dto/ChallengeResponse.java
git commit -m "feat(api): city + location DTOs"
```

---

### Task 5: Service validation (rule **C**) + wire create/replace

**Files:**
- Modify: `src/main/java/com/challenges/api/service/ChallengeService.java`

- [ ] **Step 1: Rule C**

Before **`save`** in **`create`** and **`replace`**:

- Let **`hasCity`** = non-null non-blank **`req.city()`**.
- Let **`hasLoc`** = **`req.location()`** non-null.
- If **`hasCity` || `hasLoc`**: require **`req.location() != null`** with **both** lat/lon (DTO **`@NotNull`** already enforces if **`location`** is non-null — but **city without location** must return **400**: use **`@AssertTrue`** on the request record or explicit check: if **`hasCity` && !`hasLoc`** → **`IllegalArgumentException`** mapped to 400, or use **`@Constraint`**).

**Minimal explicit check:**

```java
private void validateLocationRuleC(ChallengeRequest req) {
	boolean hasCity = req.city() != null && !req.city().isBlank();
	boolean hasLoc = req.location() != null;
	if ((hasCity || hasLoc) && !hasLoc) {
		throw new IllegalArgumentException("location with latitude and longitude is required when city or location is set");
	}
}
```

If **`location`** is null and **`!hasCity`**, OK (no location data). If both null/blank, OK.

- [ ] **Step 2: Apply to entity**

After validation, **`ch.setCity(...)`** (null if blank), **`ch.setLocation(...)`** — **`toPoint(req.location())`** or **`null`**.

- [ ] **Step 3: `create` constructor**

Pass **`city`** and **`location`** into **`Challenge`** — extend **`Challenge`** constructors or use setters after **`new Challenge(...)`** in **`create`**:

```java
Challenge ch = new Challenge(...);
ch.setCity(normalizedCity(req.city()));
ch.setLocation(req.location() != null ? ChallengeLocationMapping.toPoint(req.location()) : null);
return challenges.save(ch);
```

- [ ] **Step 4: Map `IllegalArgumentException` to 400** if not already global — check **`GlobalExceptionHandler`**.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/challenges/api/service/ChallengeService.java \
  src/main/java/com/challenges/api/web/GlobalExceptionHandler.java
git commit -m "feat(challenge): validate and persist city/location (rule C)"
```

---

### Task 6: Controller — numeric **`id`** path + integration test

**Files:**
- Modify: `src/main/java/com/challenges/api/web/ChallengeController.java`
- Modify: `src/test/java/com/challenges/api/web/ChallengeControllerIT.java` (or create assertion file)

- [ ] **Step 1: Path constraints**

Change:

```java
@GetMapping({ "/{id}", "/{id}/" })
```

to:

```java
@GetMapping({ "/{id:\\d+}", "/{id:\\d+}/" })
```

Do the same for **`@PutMapping`** and **`@DeleteMapping`** on **`ChallengeController`**.

- [ ] **Step 2: IT — create challenge with location**

POST JSON including **`"city":"Berlin"`**, **`"location":{"latitude":52.52,"longitude":13.405}`**, GET by id, assert fields; test **city without location** → **400**.

- [ ] **Step 3: Run tests**

```bash
./gradlew test
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/challenges/api/web/ChallengeController.java \
  src/test/java/com/challenges/api/web/ChallengeControllerIT.java
git commit -m "feat(challenge): REST paths \\d+ for id; IT for city/location"
```

---

## Phase B — Nearby (later)

### Task 7: `GET /api/challenges/nearby`

**Files:**
- Modify: `src/main/java/com/challenges/api/web/ChallengeController.java`
- Modify: `src/main/java/com/challenges/api/service/ChallengeService.java`
- Modify: `src/main/java/com/challenges/api/repo/ChallengeRepository.java`

- [ ] **Step 1: Repository — native query**

Add **`Page<Challenge>`** method using **`ST_DWithin`**:

- First argument: **`challenges.location`**
- Second: **`ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography`** (center point as geography)
- Third: **`:radiusMeters`**
- **`WHERE location IS NOT NULL`** AND **`is_private = false`** (match list behavior)
- **`ORDER BY ST_Distance(location, center)`** ascending

Use **`countQuery`** for **`Page`**. Verify **parameter binding** and **index usage** (`EXPLAIN` in dev).

- [ ] **Step 2: Controller**

```java
@GetMapping({ "/nearby", "/nearby/" })
public Page<ChallengeResponse> nearby(
		@RequestParam double lat,
		@RequestParam double lng,
		@RequestParam double radiusMeters,
		@PageableDefault(size = 20) Pageable pageable) {
	// validate radius > 0, lat/lon in range
}
```

- [ ] **Step 3: Service** delegates to repository, maps to **`ChallengeResponse`**.

- [ ] **Step 4: IT** with known coordinates and seeded challenges.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(challenge): GET /api/challenges/nearby with ST_DWithin"
```

---

## Verification

| Check | Command / expectation |
|-------|-------------------------|
| PostGIS available | `\dx` in **`psql`** shows **`postgis`** |
| Flyway | **`./gradlew flywayMigrate`** (or test task) applies **V15** |
| Hibernate | App starts with **`ddl-auto: validate`** |
| Tests | **`./gradlew test`** green |

---

## Out of scope (YAGNI)

- Reverse geocoding / automatic **`city`** from coordinates  
- **`geometry`** vs **`geography`** dual storage  
- OpenAPI schema examples (optional follow-up)

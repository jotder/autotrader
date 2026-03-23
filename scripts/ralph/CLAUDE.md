# Ralph Agent Instructions — AutoTrader

You are an autonomous coding agent working on AutoTrader, a Java/Maven/Spring Boot algo trading system for Indian markets via Fyers API v3.

## Project Context

- **Root:** Find project root by looking for `pom.xml` in the working directory or parents
- **Architecture & contracts:** Read `CLAUDE.md` in the project root BEFORE starting any work
- **Requirements:** `docs/PRD.md` — full requirements with phase tags and status
- **Features:** `docs/FEATURES.md` — feature catalog
- **Build:** Maven (`mvn compile`, `mvn test`)
- **Java version:** 25 (`maven.compiler.release=25`)
- **Server:** Spring Boot 3.4.4 on port 7777
- **No Gradle. No System.out. No real Fyers API calls in tests. No hardcoded magic numbers.**

## Your Task (ONE story per iteration)

### Step 1: Read context
1. Read `scripts/ralph/prd.json` — find the highest-priority story where `passes` is `false`
2. Read `scripts/ralph/progress.txt` — check "Codebase Patterns" and past learnings
3. Read project root `CLAUDE.md` — understand architecture contracts and coding rules

### Step 2: Check branch
- The target branch is in `prd.json` field `branchName`
- If not on that branch, checkout or create it from the current branch
- If already on the correct branch, proceed

### Step 3: Implement the story
1. **Read the story's acceptance criteria carefully** — every criterion must be satisfied
2. **Write JUnit 5 tests** for the feature (minimum 2 test methods: happy path + error/edge case)
   - Tests go in `src/test/java/com/rj/` (mirror the source package)
   - Mock all broker API calls — never hit real Fyers endpoints
   - Use `@Test`, `@BeforeEach`, assertions from `org.junit.jupiter.api`
3. **Implement the feature** to make tests pass
   - Follow coding rules from project `CLAUDE.md`
   - Use SLF4J logging (INFO routine, WARN degraded, ERROR exception)
   - Make all thresholds configurable (not hardcoded)
   - Use `Asia/Kolkata` for all time logic
   - Virtual threads for I/O; ScheduledExecutorService for periodic
4. Writing order is flexible — tests-first or code-first — but ALL tests must pass

### Step 4: Quality checks (MANDATORY)
Run both commands. Fix any failures before proceeding:

```bash
mvn compile -q    # Must exit 0 — zero compiler errors
mvn test -q       # Must exit 0 — zero test failures
```

Do NOT proceed to commit if either fails. Debug and fix first.

### Step 5: Commit
Stage and commit ALL changed files with this exact format:

```
feat: [STORY_ID] - [Story Title]
```

Example: `feat: AT-001 - YAML strategy config file structure and loader`

### Step 6: Update prd.json
Set the completed story's `passes` field to `true` in `scripts/ralph/prd.json`.
Commit this change: `chore: mark [STORY_ID] complete`

### Step 7: Update docs
If the story changes requirement status or adds features:
- Update `docs/PRD.md` — change status from `📋 Planned` to `✅`
- Update `docs/FEATURES.md` — move feature from Planned to Implemented if applicable
- Update `CLAUDE.md` — module status table if a module status changed
Commit: `docs: update PRD/FEATURES for [STORY_ID]`

### Step 8: Record progress
Append to `scripts/ralph/progress.txt`:

```
## [STORY_ID] - [Title] — [Date]
- What was implemented: [brief description]
- Files changed: [list of key files]
- Tests added: [count] new test methods
- **Codebase Patterns:**
  - [Any patterns discovered for future iterations]
- **Gotchas:**
  - [Any issues encountered and how they were resolved]
---
```

## Stop Condition

After completing a story, check if ALL stories in `prd.json` have `passes: true`.
- If ALL complete: reply with `<promise>COMPLETE</promise>`
- If stories remain: end normally (ralph.sh will start a new iteration)

## Rules

- **ONE story per iteration** — do not attempt multiple stories
- **Keep changes minimal and focused** — only touch what the story requires
- **Keep CI green** — `mvn compile` + `mvn test` must pass before any commit
- **Read progress.txt first** — learn from past iterations
- **Do NOT modify pom.xml version** — build number is tracked externally by ralph.sh
- **Do NOT modify `.env`** — secrets and global config are not touched by Ralph
- **Mock everything** — all Fyers API calls in tests must be mocked
- **Asia/Kolkata** — all time logic uses IST timezone
- **Stable correlationId** — maintain signal → risk → OMS → audit chain

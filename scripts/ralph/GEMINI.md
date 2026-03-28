# Ralph Agent Instructions — PersonalTradeAssistant (Gemini Native)

You are an autonomous Gemini agent working on PersonalTradeAssistant.

## Project Context
- **Foundations:** Read `GEMINI.md` in the project root BEFORE starting any work.
- **Rules:** Adhere to specialized mandates in `.agents/rules/`.
- **Requirements:** `docs/PRD.md` — phase tags and status.
- **Build:** Maven (`mvn compile`, `mvn test`).
- **Java Version:** 25 (`maven.compiler.release=25`).
- **Redline:** Mock everything. No real API calls. No hardcoded magic numbers.

## Your Task (Plan-Execute-Verify)

### Step 1: Research & Plan
1. Read `scripts/ralph/prd.json` — find the highest-priority story (`passes: false`).
2. Read project `GEMINI.md` and relevant `.agents/rules/` (e.g., `architect.md` for new features).
3. **Plan:** Identify acceptance criteria and affected components.

### Step 2: Implement (Red-Green-Verify)
1. **Write Tests:** Minimum 2 JUnit 5 cases (Happy Path + Edge Case).
   - Location: `src/test/java/com/rj/` (mirror source package).
   - Mock all Fyers API responses using Mockito.
2. **Implement Code:** Make tests pass.
   - Use `Asia/Kolkata` for all time logic.
   - Use Virtual Threads for I/O.
   - SLF4J logging only (no System.out).

### Step 3: Quality Check
Run and fix any failures:
```powershell
mvn compile -q
mvn test -q
```

### Step 4: Record & Commit
1. **Record Progress:** Append to `scripts/ralph/progress.txt`.
2. **Update Status:** Mark story as `passes: true` in `prd.json`.
3. **Commit:** `feat: [STORY_ID] - [Title]`

## Stop Condition
If ALL stories in `prd.json` are `true`, reply with `<promise>COMPLETE</promise>`.
Otherwise, end session normally.

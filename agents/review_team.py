"""
Multi-agent review team for PersonalTradeAssistant.

Agents:
  - security-reviewer    : audits token/credential/auth handling
  - test-runner          : compiles and runs Maven/JUnit tests
  - code-quality-reviewer: reviews Java code quality and patterns
  - api-coverage-checker : checks all Fyers API classes have test coverage

Usage:
    python agents/review_team.py
    python agents/review_team.py --task security
    python agents/review_team.py --task tests
    python agents/review_team.py --task quality
    python agents/review_team.py --task coverage
"""

import asyncio
import argparse
from claude_agent_sdk import query, ClaudeAgentOptions, AgentDefinition

PROJECT_ROOT = "C:/sandbox/PersonalTradeAssistant/PersonalTradeAssistant"

AGENTS = {
    "security-reviewer": AgentDefinition(
        description=(
            "Security specialist for trading applications. "
            "Use for auditing token handling, credential storage, API key exposure, "
            "and auth flow security in Fyers API integration code."
        ),
        prompt=f"""You are a security specialist reviewing a Java trading assistant that integrates with the Fyers broker API.

Project root: {PROJECT_ROOT}

Focus your review on:
1. **Credential handling** — how ACCESS_TOKEN, REFRESH_TOKEN, FYERS_SECRET_KEY, and pin are stored, passed, and logged
2. **Token generation flow** — TokenGenerator.java: is the auth code and refresh token handled safely?
3. **ConfigManager.java** — does updateEnvProperty() expose secrets in logs or intermediate state?
4. **.env file** — is it gitignored? Are secrets ever logged?
5. **API calls** — are tokens sent over HTTPS only? Any hardcoded credentials?
6. **Error messages** — do stack traces or error logs leak sensitive values?

Key files to review:
- {PROJECT_ROOT}/src/main/java/com/rj/fyers/TokenGenerator.java
- {PROJECT_ROOT}/src/main/java/com/rj/config/ConfigManager.java
- {PROJECT_ROOT}/src/main/java/com/rj/fyers/FyersClientFactory.java
- {PROJECT_ROOT}/src/main/java/com/rj/fyers/FyersBrokerConfig.java
- {PROJECT_ROOT}/.gitignore
- {PROJECT_ROOT}/.env (if accessible — check keys present, not values)

Output a structured report:
- CRITICAL issues (immediate risk)
- WARNINGS (should fix)
- SUGGESTIONS (nice to have)
""",
        tools=["Read", "Grep", "Glob"],
        model="sonnet",
    ),

    "test-runner": AgentDefinition(
        description=(
            "Maven/JUnit test runner. "
            "Use for compiling the project, running JUnit 5 tests, and reporting failures."
        ),
        prompt=f"""You are a test execution specialist for a Java Maven project.

Project root: {PROJECT_ROOT}

Tasks:
1. Run all tests: `mvn test -f {PROJECT_ROOT}/pom.xml`
2. If there are failures, read the relevant test files and source files to diagnose the root cause
3. Report: total tests, passed, failed, skipped, and a summary of any failures with likely causes

Test files are in: {PROJECT_ROOT}/src/test/java/com/rj/

Note: Some tests may require live Fyers API credentials (.env) — if tests fail due to missing env vars or auth errors, note that separately from genuine code bugs.
""",
        tools=["Bash", "Read", "Grep"],
        model="sonnet",
    ),

    "code-quality-reviewer": AgentDefinition(
        description=(
            "Java code quality reviewer. "
            "Use for reviewing Java patterns, error handling, resource management, and best practices "
            "in the Fyers API integration classes."
        ),
        prompt=f"""You are a Java code quality expert reviewing a trading assistant application.

Project root: {PROJECT_ROOT}

Review the main source files in:
- {PROJECT_ROOT}/src/main/java/com/rj/fyers/
- {PROJECT_ROOT}/src/main/java/com/rj/config/
- {PROJECT_ROOT}/src/main/java/com/rj/model/

Focus on:
1. **Resource management** — are HttpClient, Scanner, streams properly closed?
2. **Error handling** — are exceptions caught too broadly? Silent failures?
3. **Thread safety** — ConfigManager is a singleton; is it safe for concurrent access?
4. **Null safety** — are API responses validated before field access?
5. **Code duplication** — repeated patterns across Fyers* classes that could be extracted
6. **Logging** — appropriate use of SLF4J vs System.err.println?

Output findings grouped by file, with severity (HIGH / MEDIUM / LOW) and a short fix suggestion for each.
""",
        tools=["Read", "Grep", "Glob"],
        model="sonnet",
    ),

    "api-coverage-checker": AgentDefinition(
        description=(
            "Test coverage analyst for Fyers API classes. "
            "Use to check which Fyers API wrapper classes have corresponding JUnit tests "
            "and identify gaps in test coverage."
        ),
        prompt=f"""You are a test coverage analyst for a Java project.

Project root: {PROJECT_ROOT}

Tasks:
1. List all Fyers API wrapper classes in: {PROJECT_ROOT}/src/main/java/com/rj/fyers/
2. List all test classes in: {PROJECT_ROOT}/src/test/java/com/rj/
3. For each Fyers class, determine if a corresponding test exists
4. For classes that DO have tests, briefly check if the test methods cover the main public methods
5. Produce a coverage matrix:

| Fyers Class         | Test Class           | Status        | Missing Coverage         |
|---------------------|----------------------|---------------|--------------------------|
| TokenGenerator      | TokenGenTest         | Partial       | generateTokenFromRefresh |
| ...                 | ...                  | ...           | ...                      |

End with a prioritized list of which tests to write first (based on criticality to trading operations).
""",
        tools=["Read", "Grep", "Glob"],
        model="haiku",
    ),
}

TASK_PROMPTS = {
    "security": (
        "Use the security-reviewer agent to perform a full security audit of the "
        "PersonalTradeAssistant project, focusing on the Fyers API token handling, "
        "credential storage in .env, and ConfigManager. Report all findings."
    ),
    "tests": (
        "Use the test-runner agent to compile and run all Maven/JUnit tests in the "
        "PersonalTradeAssistant project. Report results including any failures and their causes."
    ),
    "quality": (
        "Use the code-quality-reviewer agent to review all Java source files in the "
        "PersonalTradeAssistant project for code quality issues, focusing on the "
        "Fyers API integration classes and config management."
    ),
    "coverage": (
        "Use the api-coverage-checker agent to produce a test coverage matrix for all "
        "Fyers API wrapper classes in the PersonalTradeAssistant project."
    ),
    "all": (
        "Perform a comprehensive review of the PersonalTradeAssistant project using all available agents:\n"
        "1. Use the security-reviewer agent to audit credential and token handling\n"
        "2. Use the test-runner agent to run all JUnit tests\n"
        "3. Use the code-quality-reviewer agent to review Java code quality\n"
        "4. Use the api-coverage-checker agent to check test coverage gaps\n"
        "Compile a final summary with the most important findings from each agent."
    ),
}


async def run_review(task: str = "all"):
    prompt = TASK_PROMPTS.get(task, TASK_PROMPTS["all"])
    print(f"\n{'='*60}")
    print(f"Running task: {task}")
    print(f"{'='*60}\n")

    async for message in query(
        prompt=prompt,
        options=ClaudeAgentOptions(
            allowed_tools=["Read", "Grep", "Glob", "Bash", "Agent"],
            agents=AGENTS,
            cwd=PROJECT_ROOT,
        ),
    ):
        if hasattr(message, "result"):
            print(message.result)
        elif hasattr(message, "content"):
            # Stream intermediate output if available
            for block in message.content:
                if hasattr(block, "text"):
                    print(block.text, end="", flush=True)


def main():
    parser = argparse.ArgumentParser(description="PersonalTradeAssistant review team")
    parser.add_argument(
        "--task",
        choices=["security", "tests", "quality", "coverage", "all"],
        default="all",
        help="Which review task to run (default: all)",
    )
    args = parser.parse_args()
    asyncio.run(run_review(args.task))


if __name__ == "__main__":
    main()
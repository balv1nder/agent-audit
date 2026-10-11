# AgentAudit

A prototype exploring one question: **when an AI agent answers a financial
question, what do you need to keep in order to defend that answer later?**

A financial analyst asks "which portfolio carries more risk?". An LLM agent
decides which calculations to run, Java runs them, and the agent writes an
answer. Months later a regulator asks how that conclusion was reached. The
final answer alone cannot settle it — it is fluent prose, and prose is not
evidence.

AgentAudit keeps a durable, queryable record of what the agent actually did:
every tool it invoked, the arguments it supplied, the values that came back,
and how the run ended.

This is a small prototype built over a weekend. It is not a production system.
What is simplified, and what would have to change, is set out at the bottom.

---

## The claim, demonstrated

Ask about a portfolio that does not exist:

```bash
curl -X POST localhost:8080/api/agent/ask -H 'Content-Type: application/json' \
  -d '{"sessionId":"demo","question":"What was the VaR of Portfolio Z on 2026-10-06?"}'
```

```json
{
  "auditSessionId": "0efeeafd-0cad-4748-8d21-8610e3ca0abf",
  "answer": "No VaR data found for portfolio Portfolio Z on 2026-10-06."
}
```

**HTTP 200.** The request succeeded. The failure exists only inside a sentence.

Now the record:

```
seq  type                tool      payload
 0   USER_REQUEST                  {"question":"What was the VaR of Portfolio Z on 2026-10-06?"}
 1   LLM_REQUEST
 2   TOOL_CALL           getVaR    {"portfolio":"Portfolio Z","date":"2026-10-06"}
 3   TOOL_ERROR          getVaR    {"exception":"IllegalArgumentException",
                                    "message":"No VaR data found for portfolio Portfolio Z on 2026-10-06"}
 4   LLM_RESPONSE                  {"answer":"No VaR data found..."}
 5   SESSION_COMPLETED
```

The status code said nothing. The record says exactly what happened.

---

## Running it

Requires Java 25, PostgreSQL and Ollama.

```bash
# 1. the model (no API key, no cost, runs offline once pulled)
brew install ollama
ollama serve &
ollama pull llama3.2:3b

# 2. the audit store
brew install postgresql@17 && brew services start postgresql@17
psql -d postgres -c "CREATE ROLE agentaudit WITH LOGIN PASSWORD 'agentaudit'"
psql -d postgres -c "CREATE DATABASE agentaudit OWNER agentaudit"

# 3. the application
./mvnw spring-boot:run
```

The application **refuses to start** if Ollama is unreachable or the model is
missing, with an instruction that says how to fix it.

### Ask it something

```bash
curl -X POST localhost:8080/api/agent/ask -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","question":"Compare the VaR of Portfolio A and Portfolio B on 2026-10-06. Which is higher?"}'
```

### Read the record

```bash
curl localhost:8080/api/audit/sessions/<auditSessionId>
```

Or query it directly — `docs/demo-queries.sql` holds the questions a reviewer
actually asks.

---

## Architecture

```
POST /api/agent/ask
      │
      ▼
 AgentController ──────────────────────────────► AuditController (read-only)
      │                                                   │
      ▼                                                   │
   LlmAgent ── opens session, records the model boundary   │
      │                                                   │
      ▼                                                   ▼
  ChatClient (Spring AI) ◄──► Ollama            PostgreSQL
      │   model chooses a tool                   agent_session
      ▼                                          audit_event
 FinancialTools  ── records TOOL_CALL / TOOL_RESULT / TOOL_ERROR
      │
      ▼
 FinancialService ── every figure is computed here, in ordinary Java
      │
      ▼
 MockFinancialData ── the replaceable data source
```

| Package | Responsibility |
|---|---|
| `finance` | VaR data and calculations. Knows nothing about AI. |
| `tools` | `@Tool` wrappers — the model-facing adapter over `finance`. |
| `agent` | The `Agent` interface and its LLM implementation. |
| `audit` | Entities, capture, and read models for the record. |
| `controller` | HTTP boundary: ask, and read the record. |
| `config` | Chat client, tracing, startup checks. |

**The invariant the whole design protects: every financial figure is computed
in deterministic Java. The model selects tools and phrases answers. It never
calculates.**

---

## How the model reaches the data

The model cannot execute anything. It emits a *request*:

1. Spring AI sends the question plus a JSON schema for each `@Tool` method —
   name, parameters, and the description text.
2. The model replies with structured JSON: `{"tool":"getVaR","args":{...}}`.
3. Spring AI resolves that name against methods registered at startup and
   invokes the Java method **by reflection**.
4. The return value is appended to the conversation.
5. The model is called again, now holding a real figure, and writes prose.

Steps 2 and 5 are **two separate model calls** for one question.

The `@Tool` description is not documentation. It is the only thing the model
reads when choosing, which makes it an API contract whose consumer happens to
be a language model.

---

## Three identifiers, deliberately distinct

| | What it identifies | Lifetime |
|---|---|---|
| `clientSessionId` | the caller's own label — may be reused across runs | caller's choice |
| `auditSessionId` | **one execution**, generated per request | forever |
| `traceId` | one OpenTelemetry trace | sampled, expires |

A caller may send `"s1"` fifty times. An audit record has to point at exactly
one execution, so the session id is generated and the caller's label is kept
beside it for correlation.

---

## How events are captured

`LlmAgent` opens the session and records the model boundary. The tool events
are harder, and the reason is worth stating:

**A tool method has no call site.** Spring AI resolves it by name and invokes
it reflectively, so there is nowhere to pass a session id. Adding a parameter
would not work either — every tool parameter appears in the schema the model
sees, so the model would invent session ids.

Tool execution does happen on the request thread. So the session id travels on
a `ThreadLocal` (`AuditContext`), which reaches the tools without touching
their signatures. It is cleared in a `finally` block: threads are pooled, and
a context left behind would attach the next request's events to this session.

The limitation is real — this holds only while tool execution stays on the
calling thread. Asynchronous or parallel tools would need explicit propagation.

Every audit write commits in **its own transaction** (`REQUIRES_NEW`). If the
record shared the request's transaction, a failure would roll it back, and the
runs most worth reviewing would be exactly the ones leaving no trace.

Ordering comes from a `sequence_no` column, not timestamps — several events
routinely land in the same millisecond, and the order is the point.

---

## Why not just use OpenTelemetry?

The application is traced, and every audit event carries the `traceId` and
`spanId` it happened in. The two records answer different questions:

| | Audit record | Trace |
|---|---|---|
| What arguments was the tool given? | yes | no |
| What did it return? | yes | no |
| How long did the model take? | no | yes |
| Token usage? | no | yes |
| Recorded for *every* request? | yes | no — sampled |
| Still there in two years? | yes | no |

`management.tracing.sampling.probability=1.0` here, but production systems
sample a small fraction for cost. A regulator asking about one specific request
is likely asking about a trace that was never kept. Traces are also exported
asynchronously in batches and can be lost on shutdown; the audit write is
committed synchronously before the request proceeds.

The ids are the bridge: while the telemetry exists, a reviewer can move from
*what the agent did* into *how it performed*; once it expires, the record still
stands alone.

This is not a claim that OpenTelemetry **cannot** be retained long-term. It can.
The distinction is that it is not a purpose-built, self-contained record of
inputs and outputs, and it is configured for operational cost rather than for
evidence.

---

## What went wrong while building this

The most useful results came from failures, and they are the point of the
project rather than an embarrassment.

**The model claimed EUR 12M was higher than EUR 14M.** Reproducibly, at
temperature 0. The audit record showed the tools had returned both figures
correctly — the error was in the narration, not the data. The same model
compares those numbers correctly when asked plainly; it fails inside the full
tool-calling context, where the second model call still carries every tool
schema and a small model stays in "select a tool" mode.

The fix was not a better prompt. Comparison moved into Java
(`FinancialService.compareVaR` returns the winner as an explicit field) and the
tool is annotated `returnDirect = true`, so its result is returned without a
second model pass. The model still chooses the tool and the arguments; it does
not get to restate a conclusion it has been shown to get wrong.

`returnDirect` ends the tool-calling loop, which suits a terminal operation
like this one and would be wrong for a comparison used as an intermediate step.

**Prompting was tried three times and measured.** Two arms, three runs each,
with `returnDirect` disabled:

| | Baseline prompt | With an anti-hallucination rule |
|---|---|---|
| Correct figures | no | yes |
| Answers the question | no | no |
| Tool rounds | 1 | **2** |

The added rule caused an *extra* tool call — it listed the tool names, and the
model latched onto `listAvailableData` and paraphrased that instead of the
comparison it had already computed. The HTTP responses gave no hint of this;
only the audit trail showed the extra call.

That is the sharpest result in the project: **the audit layer proved its worth
on an engineering decision, not on a hypothetical regulator's question.**

**A general principle fell out of it:** every piece of reasoning moved into a
tool is a piece the model cannot get wrong. For a financial figure that is not
an optimisation, it is a requirement.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/agent/ask` | ask a question; returns the answer and its `auditSessionId` |
| `GET` | `/api/audit/sessions/{auditSessionId}` | one session and its ordered events |
| `GET` | `/api/audit/sessions` | the 20 most recent runs |
| `GET` | `/api/audit/clients/{clientSessionId}/sessions` | every run under a caller label |

The audit API is **read-only**. There is no endpoint that writes or amends a
session; the record is produced by the run and nothing else.

---

## Tests

`./mvnw test` — 23 tests, no running service required. Tests use in-memory H2
and a stub chat model.

Two are worth singling out:

- `AuditFailureCaptureTest` holds the project to its own claim: a throwing tool
  is recorded with its exception, a failed session keeps its reason and no
  answer, an unfinished run stays `IN_PROGRESS` with its events intact, and
  sequence numbers stay contiguous across a failure.
- `FinancialToolsAnnotationTest` guards `returnDirect` and the result converter
  by reflection. Tool behaviour depends on annotations with no call site, so a
  misplaced one changes behaviour with no compiler error. That happened twice
  during development; the test caught a third occurrence.

---

## Simplified here, and what production would need

| Here | Production |
|---|---|
| `MockFinancialData` — four hardcoded VaR records | a real risk engine behind the same `FinancialService` seam |
| `ddl-auto=update` | Flyway or Liquibase, so schema changes to an audit table are versioned and reviewable |
| H2 in tests, PostgreSQL at runtime | Testcontainers, to close the dialect gap |
| Spans printed to the console | OTLP export to a collector (Jaeger, Tempo) |
| `llama3.2:3b` locally | a larger model, or a hosted one, with the same tool boundary |
| No authentication | access control on the audit API, which is sensitive by nature |
| Append-only by convention | tamper evidence — hash chaining makes modification *detectable*, though not impossible; real protection also needs access control, key management and a protected verifying record |
| Session status tracks execution | `COMPLETED` means "ran without error", not "answered correctly". Validating the final answer against the recorded tool results would catch the EUR 12M > EUR 14M case automatically — the record is what makes that check possible |

### Replacing the mock data with a real risk engine

`FinancialService` depends on `MockFinancialData` and nothing else. A real
implementation — an in-memory analytics engine, a risk warehouse, a pricing
service — would satisfy the same interface, and nothing above it would change:
not the tools, not the agent, not the audit layer, not the API.

The audit payloads would then record the real query parameters sent to that
engine and the figures it returned, which is exactly the evidence a reviewer
needs. The record does not care where the numbers came from, only that they
came from somewhere deterministic and were preserved.

---

## Stack

Java 25 · Spring Boot 4.1.1 · Spring AI 2.0.1 · Ollama (`llama3.2:3b`) ·
PostgreSQL 17 · OpenTelemetry via Micrometer Tracing · Maven

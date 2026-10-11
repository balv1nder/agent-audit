-- AgentAudit -- the questions a reviewer actually asks.
--
-- Run against the audit database (DataGrip, or psql):
--   psql -h localhost -U agentaudit -d agentaudit
--
-- payload is jsonb, so these read inside the recorded arguments and results
-- rather than searching text.


-- 1. Recent runs: what was asked, what came back, how it ended.
select s.started_at,
       s.client_session_id,
       s.status,
       s.user_question,
       s.final_answer
from agent_session s
order by s.started_at desc
limit 20;


-- 2. The full timeline of one run. Replace the id with an auditSessionId
--    returned by POST /api/agent/ask.
select e.sequence_no,
       e.event_type,
       e.detail as tool,
       e.timestamp,
       e.payload
from audit_event e
where e.session_id = '00000000-0000-0000-0000-000000000000'
order by e.sequence_no;


-- 3. Every tool call that touched a given portfolio, whatever the question was.
--    This is the query that text storage could not answer cleanly.
select s.started_at,
       s.user_question,
       e.detail as tool,
       e.payload
from audit_event e
join agent_session s on s.id = e.session_id
where e.event_type = 'TOOL_CALL'
  and 'Portfolio A' in (
        coalesce(e.payload->>'portfolio', ''),
        coalesce(e.payload->>'firstPortfolio', ''),
        coalesce(e.payload->>'secondPortfolio', ''))
order by s.started_at desc;


-- 4. Every failure: a tool that threw, with the exception it threw.
--    Note these runs often returned HTTP 200 -- the agent reported the failure
--    in prose, so the status code says nothing.
select s.started_at,
       s.client_session_id,
       e.detail as tool,
       e.payload->>'exception' as exception,
       e.payload->>'message'   as message,
       s.final_answer
from audit_event e
join agent_session s on s.id = e.session_id
where e.event_type = 'TOOL_ERROR'
order by s.started_at desc;


-- 5. Runs that started and never finished. An empty result is the reassuring
--    answer; rows here are runs that died mid-execution, and the evidence of
--    them exists only because the session is written before any work begins.
select s.id,
       s.started_at,
       s.user_question,
       count(e.id) as events_recorded
from agent_session s
left join audit_event e on e.session_id = s.id
where s.status = 'IN_PROGRESS'
group by s.id, s.started_at, s.user_question
order by s.started_at desc;


-- 6. The answer next to the tool result that produced it.
--    This is the query that catches a model misreporting its own evidence --
--    during development it claimed EUR 12M was higher than EUR 14M while the
--    tool had returned the correct ranking.
select s.client_session_id,
       e.detail                as tool,
       e.payload->>'result'    as tool_returned,
       s.final_answer          as agent_said
from audit_event e
join agent_session s on s.id = e.session_id
where e.event_type = 'TOOL_RESULT'
order by s.started_at desc
limit 20;


-- 7. From the record into the telemetry: the trace a run belongs to.
--    Paste the trace id into Jaeger/Tempo while the trace still exists.
--    Several span ids per trace means the work was broken into steps.
select s.client_session_id,
       e.trace_id,
       count(distinct e.span_id) as spans_touched,
       count(*)                  as events
from audit_event e
join agent_session s on s.id = e.session_id
where e.trace_id is not null
group by s.client_session_id, e.trace_id
order by max(e.timestamp) desc
limit 10;


-- 8. How often each tool is used, and how often it fails.
select e.detail as tool,
       count(*) filter (where e.event_type = 'TOOL_CALL')   as calls,
       count(*) filter (where e.event_type = 'TOOL_ERROR')  as errors
from audit_event e
where e.detail is not null
group by e.detail
order by calls desc;

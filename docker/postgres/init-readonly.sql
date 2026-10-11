-- Read-only role for the PostgreSQL MCP server (the agent can inspect schema and data, but never change them).
create role mcp_readonly login password 'mcp_readonly';
grant connect on database expense_limiter to mcp_readonly;
grant usage on schema public to mcp_readonly;
-- tables are created later by Flyway under the "expense" user
alter default privileges for role expense in schema public grant select on tables to mcp_readonly;
alter default privileges for role expense in schema public grant select on sequences to mcp_readonly;

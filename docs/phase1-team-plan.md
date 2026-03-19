# Teacher Internship Platform Phase 1 Team Plan

## 1. Delivery Scope

Strictly align with section 13.1 of the requirement document.

- Login authentication and RBAC
- Student, teacher, and college base management
- Internship unit library
- Guidance relationship setup and internship application
- Core teaching and head-teacher form workflows
- Teacher review and college archive
- Basic statistics and message reminders

## 2. Team Structure

### 2.1 Product and Business

- Product owner: freeze scope and acceptance
- Business analyst: extract workflow and state machines
- Project manager: schedule, dependency, risk

### 2.2 Engineering

- Solution architect: domain model, APIs, state rules, audit strategy
- Backend engineer A: auth, RBAC, messages, statistics, audit
- Backend engineer B: student/teacher/unit management, guidance, internship application
- Backend engineer C: form model, teacher review, college archive
- Frontend engineer A: login shell and shared layout
- Frontend engineer B: student and teacher pages
- Frontend engineer C: admin and super-admin pages

### 2.3 Quality

- QA engineer: role, permission, workflow, negative-path tests
- Data engineer: constraints and archive semantics
- Ops engineer: startup, runtime logging, handoff

## 3. Delivery Priorities

1. Auth and permission model
2. College, student, teacher master data
3. Internship unit library
4. Guidance request and approval flow
5. Internship application approval flow
6. Form instance workflow
7. Message center and dashboards
8. Audit trail

## 4. Implementation Strategy

- Close the primary business loop before adding configurability
- Use one generic form instance model, then map teaching/head-teacher variants
- Keep review flow and message flow generic
- Log every critical mutation
- Keep layers replaceable for a later Vue 3 + Spring Boot migration

## 5. Current Environment Decision

This workspace contains only the requirement document and no existing frontend/backend project.
Java is available, but Maven/Spring Boot scaffolding is not.
Phase 1 will therefore be delivered first as a runnable no-dependency Node prototype while preserving the business structure required for later migration to the recommended production stack.

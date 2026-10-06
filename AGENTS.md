# Crop feature ownership

- Default to read-only assistance for all source code, including the crop folder and tests. Do not apply code changes directly; show the exact file path and complete replacement code or clearly identified blocks for the user to copy and paste.
- A request to fix, add, or refactor code means propose the changes for manual application. Only edit code directly if the user explicitly overrides this rule for the current task. Do not switch branches, stage, commit, or merge code on the user's behalf without an explicit request.
- The user's responsibility is the crop backend. Keep implementations simple, well indented, and readable.
- Propose production code changes only in `backend/src/main/java/com/csd/farm/crop/` by default. If direct editing is explicitly authorized, this folder boundary still applies unless the user also authorizes an exception.
- Read other project files as needed to understand interfaces, but do not modify, delete, rename, or move them without explicit user authorization for that task.
- In particular, treat `environment/`, `auth/`, `config/`, `api/`, frontend files, database migrations, and build configuration as read-only. Adapt crop code to teammates' existing interfaces rather than changing their code.
- Crop tests belong in `backend/src/test/java/com/csd/farm/crop/`. Existing crop tests are `CropDatabaseTest.java`, `CropHealthServiceTest.java`, `CropWeatherServiceTest.java`, and `LiveWeatherIntegrationTest.java` under `backend/src/test/java/com/csd/farm/`. Confirm permission before editing tests outside the production crop folder when it has not already been given in the current task. Shared tests such as `FarmIntegrationTest.java` and environment tests always require explicit authorization.
- An authorized one-time restoration of a teammate's files does not authorize further edits to those files.
- When an out-of-scope change is necessary, identify the exact files and why they need changing, then ask permission. Continue read-only investigation and prepare copy-and-paste proposals inside the permitted scope.
- Run relevant checks when authorized. Build-generated output is not source code: do not hand-edit or commit generated files under `backend/target/` or dependency caches. Report build or test blockers honestly; do not disable tests to hide failures.
- Before reporting completion, check the changed-file list for out-of-scope edits and explain the source changes and verification results.
- This file is an instruction boundary, not an operating-system filesystem restriction. Change these rules only when the user requests it.

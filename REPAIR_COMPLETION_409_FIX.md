# Repair completion: HTTP 409 fix

## What happened

The backend rejects a transition to `COMPLETED` unless:
1. The repair is currently `IN_PROGRESS`.
2. A technician is assigned.
3. `diagnosis` and `workPerformed` have both been saved on the repair.

The old Angular screen offered `COMPLETED` but did not explain these conditions. It also didn't save an edited technical report before submitting the status change.

## What this update changes

- `frontend/src/app/repair-workspace.page.ts`
  - Shows an inline prerequisite explanation when `COMPLETED` is selected.
  - Saves any updated diagnosis/work report first, then submits the status change.
  - Shows a meaningful error for HTTP 409 instead of always suggesting a refresh.
- `src/main/java/com/repairmanagement/repairproject/common/ApiErrorHandler.java`
  - Sends safe, specific business-rule messages in `{ "error": "...", "message": "..." }` JSON.
- `src/test/java/com/repairmanagement/repairproject/repair/RepairServiceTest.java`
  - Adds completion-precondition tests.

No database migration is required. Existing repair records remain unchanged.

## Install

1. Back up your existing files.
2. Extract the patch ZIP into the project root, preserving directories. The ZIP root is `repair_management_stage7`; if your project folder has a different name, copy its `frontend/` and `src/` subdirectories into your own project root instead.
3. If you kept your Java package named `com.example.repairProject`, do **not** copy the Java files into an unrelated `com.repairmanagement...` package. Apply just the Angular file, or merge the backend error handler method and tests into your matching Java packages, adjusting package declarations and imports.
4. Restart the backend and frontend.

```sh
mvn test
mvn spring-boot:run
```

```sh
cd frontend
npm start
```

## Test

Open Repair Workspace, select an `IN_PROGRESS` repair assigned to a technician, fill in both fields in **Technical diagnosis & work report**, select `COMPLETED`, and click **Save report & complete repair**. You should receive a successful response and see the status change to `COMPLETED`.

Try an unassigned repair or a blank diagnosis: the frontend now shows a specific message rather than a repeated silent 409.

If you still see HTTP 409, check **DevTools > Network > failed status POST > Response** to read the backend's error message. A different backend copy running on port 8081 may not have the update yet.

## Verification

Source files were inspected and TypeScript syntax parsed. The full Maven test suite, Angular build, and integration against your local MySQL server have not been executed in this environment.

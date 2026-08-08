# Implementation Plan - Fix Compilation Errors

The user reported a compilation error in `AdminPanelScreen.kt` regarding a missing `role` parameter. Additionally, a full build revealed several other compilation errors in `HistoryScreen.kt`, `FullDayCalculatorScreen.kt`, and other files. This plan aims to resolve these issues.

## User Review Required

> [!IMPORTANT]
> The errors in `FullDayCalculatorScreen.kt` and `HistoryScreen.kt` suggest that some ViewModel methods or DAO queries are missing or have different signatures than expected. I will need to update both the UI calls and the underlying database/repository layers.

## Proposed Changes

### Component: Admin Panel
#### [MODIFY] [AdminPanelScreen.kt](file:///C:/Users/mysel/Downloads/pumpmanager/app/src/main/java/com/example/AdminPanelScreen.kt)
- Fix the `FirestoreUserManager.addStaffMembership` call. Although `read_file` suggests it might be correct, I will ensure it matches the 5-parameter signature: `staffPhone`, `adminPhone`, `staffName`, `pumpName`, `role`.

### Component: History
#### [MODIFY] [HistoryScreen.kt](file:///C:/Users/mysel/Downloads/pumpmanager/app/src/main/java/com/example/HistoryScreen.kt)
- Update `viewModel.deleteAudit(audit.id, adminPhone)` to pass the correct parameters: `adminPhone`, `date`, `caName`, `timestamp`.

### Component: Full Day Calculator
#### [MODIFY] [FullDayCalculatorScreen.kt](file:///C:/Users/mysel/Downloads/pumpmanager/app/src/main/java/com/example/FullDayCalculatorScreen.kt)
- Resolve `Unresolved reference 'getMsReadingsByDate'` and `getHsdReadingsByDate`.
- Fix `it` and destructuring errors at line 127 and 147.

### Component: Database / ViewModels
#### [MODIFY] [SavedAuditDao.kt](file:///C:/Users/mysel/Downloads/pumpmanager/app/src/main/java/com/example/database/SavedAuditDao.kt)
- (If needed) add missing queries.
#### [MODIFY] [HistoryViewModel.kt](file:///C:/Users/mysel/Downloads/pumpmanager/app/src/main/java/com/example/database/HistoryViewModel.kt)
- Ensure `deleteAudit` and other methods match the UI requirements.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure all reported compilation errors are resolved.

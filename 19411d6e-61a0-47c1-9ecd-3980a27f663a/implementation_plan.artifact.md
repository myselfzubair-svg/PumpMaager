# Implementation Plan - Multi-Role Selection at Sign-In

The goal is to modify the sign-in process so that if a phone number is registered as both an Admin (Pump Owner) and a Staff Member, the user is prompted to choose which role they want to log in with.

## Proposed Changes

### [Component Name] Login Screen

#### [MODIFY] [LoginScreen.kt](file:///C:/Users/mysel/Downloads/pumpmanager/app/src/main/java/com/example/LoginScreen.kt)
- Add state variables to track if the role selection dialog should be shown and to store the pending login data.
    - `var showRoleSelectionDialog by remember { mutableStateOf(false) }`
    - `var pendingAdminInfo by remember { mutableStateOf<LoginInfo?>(null) }`
    - `var pendingStaffInfo by remember { mutableStateOf<Staff?>(null) }`
    - `var pendingFormattedMobile by remember { mutableStateOf("") }`
- Modify `completeLoginFlow` lambda:
    - Change the logic to check both `LoginInfo` (Admin) and `Staff` tables concurrently using a coroutine scope.
    - If a number exists in **both** tables, show the `showRoleSelectionDialog`.
    - If it exists in only one, proceed with the existing login logic for that role.
- Implement the `RoleSelectionDialog`:
    - A Material 3 `AlertDialog` with two primary options: "Admin / Pump Owner" and "Staff Member".
    - Each option will trigger the respective login completion logic (setting up session and calling `onLoginSuccess`).

### [Component Name] Database Sync (Optional/Future)
- Ensure that `FirestoreUserManager` and `FirestoreSyncManager` also respect this dual-role possibility if cloud sync is active. (Out of scope for immediate UI fix but worth noting).

## Verification Plan

### Automated Tests
- Build the project using `./gradlew :app:compileDebugKotlin`.

### Manual Verification
1.  **Scenario: Admin only**: Sign in with an Admin phone number. Verify it logs in as Admin immediately.
2.  **Scenario: Staff only**: Sign in with a Staff phone number. Verify it logs in as Staff immediately.
3.  **Scenario: Both**:
    - Register a number as Admin.
    - Go to Admin Panel and add the same number as Staff.
    - Log out and sign in again.
    - Verify the "Role Selection" dialog appears.
    - Select "Admin" and verify Admin dashboard access.
    - Log out and sign in again, select "Staff" and verify Staff module access.

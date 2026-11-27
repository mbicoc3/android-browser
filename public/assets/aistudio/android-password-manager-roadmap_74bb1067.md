# Android Browser Password Manager --- Development Roadmap

**Platform:** Android\
**Primary stack:** Kotlin, Jetpack Compose, Android System WebView,
AndroidX WebKit\
**Architecture:** Local-first, provider-based, offline-capable\
**Document status:** Initial implementation plan

------------------------------------------------------------------------

## 1. Project Goal

Build a password manager inside the Android browser with two independent
credential sources:

1.  **Local Vault** --- the browser's own encrypted, offline password
    manager.
2.  **Proton Pass** --- an external provider, integrated through
    supported Proton and Android capabilities.

Both sources should be presented through a consistent browser experience
where technically possible. The Local Vault must remain fully usable
without an internet connection or a Proton account.

### Core principles

-   Local credentials are stored and encrypted on the device.
-   Local Vault data is never automatically uploaded to Proton Pass.
-   Proton credentials remain managed by Proton Pass.
-   Provider capabilities must be explicit; do not pretend every
    provider supports every operation.
-   Autofill must be restricted to the intended website origin and
    require user interaction where appropriate.
-   Never reverse-engineer private Proton APIs or store Proton account
    passwords/tokens in plaintext.

------------------------------------------------------------------------

## 2. Android Technology Stack

  -----------------------------------------------------------------------
  Area                    Technology              Purpose
  ----------------------- ----------------------- -----------------------
  Language                Kotlin                  Application and domain
                                                  logic

  UI                      Jetpack Compose         Vault, settings and
                                                  prompts

  Browser                 Android System WebView  Website rendering

  Web integration         AndroidX WebKit         Supported WebView
                                                  capabilities

  Local database          Room + SQLite           Credential metadata and
                                                  encrypted records

  Key protection          Android Keystore        Protect device-bound
                                                  key material

  Encryption              JCA / Android           AES-GCM encryption
                          cryptography APIs       

  Key derivation          Argon2id from a         Derive a key from the
                          reviewed implementation master password

  Async work              Coroutines + Flow       Database and provider
                                                  operations

  Preferences             DataStore               Non-secret settings

  Biometric unlock        AndroidX Biometric      Optional vault unlock

  Autofill service        Android Autofill        Optional system-level
                          Framework               Autofill provider

  Testing                 JUnit, AndroidX Test    Unit and device tests
  -----------------------------------------------------------------------

Use maintained, reviewed libraries. Confirm Android API-level
compatibility and cryptographic provider support before selecting exact
dependency versions.

------------------------------------------------------------------------

## 3. High-Level Architecture

``` text
Android Browser
│
├── Password Manager UI
│   ├── All Credentials
│   ├── Local Vault
│   ├── Proton Pass status / entry point
│   ├── Credential Details
│   ├── Password Generator
│   └── Security Settings
│
├── Credential Coordinator
│   ├── Domain Matcher
│   ├── Provider Registry
│   ├── Credential Search
│   └── User Selection
│
├── Providers
│   ├── Local Vault Provider
│   │   ├── Room Repository
│   │   ├── Encryption Service
│   │   └── Key Manager
│   │
│   └── Proton Pass Integration
│       ├── Supported Android Autofill hand-off
│       └── Future supported integration capabilities
│
├── Browser Integration
│   ├── Login Form Detection
│   ├── Registration / Change Form Detection
│   ├── Autofill UI
│   └── Save / Update Prompts
│
└── Security
    ├── Vault Lock
    ├── Biometric Gate
    ├── Clipboard Protection
    └── Backup / Restore
```

Keep the Local Vault independent from Proton-specific code. The
browser's coordinator may query multiple providers, but each provider
owns its own data and operations.

------------------------------------------------------------------------

## 4. Phase 1 --- Local Vault and Cryptographic Foundation

**Goal:** Establish a secure, offline-capable vault before building
browser autofill.

### 4.1 Vault lifecycle

Implement:

-   First-run vault setup
-   Master password creation
-   Master password verification
-   Unlock and lock
-   Inactivity timeout
-   App background lock policy
-   Optional biometric unlock
-   Safe handling of forgotten master passwords

A forgotten master password should not silently bypass encryption.
Explain recovery limitations and encourage encrypted backups.

### 4.2 Encryption design

Use envelope encryption:

1.  Generate a random Data Encryption Key (DEK).
2.  Encrypt credential secrets using AES-256-GCM with a unique nonce per
    encryption.
3.  Derive a Key Encryption Key (KEK) from the master password using
    Argon2id and a unique random salt.
4.  Use the KEK to protect the DEK.
5.  Store the encrypted DEK, salt, KDF parameters and versioned
    metadata.
6.  Use Android Keystore to protect suitable device-bound key material
    where appropriate.

Important implementation rules:

-   Never store the master password.
-   Never hard-code encryption keys.
-   Never reuse an AES-GCM nonce with the same key.
-   Authenticate relevant record metadata as Additional Authenticated
    Data (AAD), where appropriate.
-   Version the encryption envelope so parameters can be upgraded.
-   Avoid logging passwords, decrypted records, keys or sensitive form
    values.
-   Clear sensitive buffers/references as far as practical; understand
    that managed runtimes cannot guarantee perfect memory zeroization.

**Design review required:** Choose and document how the password-derived
KEK and Android Keystore protection work together. Do not assume a
Keystore key can be recreated after reinstall or device migration.

### 4.3 Suggested database model

Keep searchable, non-secret metadata separate from encrypted secrets.

``` kotlin
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val id: String,
    val title: String,
    val normalizedDomain: String,
    val usernameHint: String?,
    val folderId: String?,
    val isFavorite: Boolean,
    val encryptedPayload: ByteArray,
    val encryptionVersion: Int,
    val createdAt: Long,
    val updatedAt: Long
)
```

The encrypted payload can contain the username, password, full URL,
notes and other sensitive fields. Decide carefully whether even the
username should be encrypted; prefer minimizing plaintext metadata.

Other tables may include:

-   `vault_metadata`
-   `folders`
-   `password_history` (encrypted values only)
-   `backup_metadata`

Add migrations and database integrity tests from the beginning.

**Deliverable:** A user can create, unlock and lock a local encrypted
vault while offline.

------------------------------------------------------------------------

## 5. Phase 2 --- Credential Management

**Goal:** Build the core password-manager features.

### Features

-   Add login
-   View credential details after vault unlock
-   Edit title, username, password, URL and notes
-   Delete credential with confirmation
-   Favorite / unfavorite
-   Search and folder organization
-   Copy username or password
-   Recently updated list
-   Multiple accounts for the same website
-   Password generator
-   Import/export planning

### Suggested domain model

``` kotlin
data class Credential(
    val id: String,
    val title: String,
    val username: String?,
    val password: String,
    val websiteUrl: String,
    val notes: String?,
    val folderId: String?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
```

This is a decrypted in-memory domain model, not a database
representation. Keep its lifetime short and do not persist it in logs,
analytics, saved UI state or crash reports.

### Password generator

Support:

-   Configurable length
-   Uppercase and lowercase letters
-   Numbers
-   Symbols
-   Excluding ambiguous characters
-   Cryptographically secure random generation
-   Copy and save generated password

**Deliverable:** Full Local Vault CRUD and password generation.

------------------------------------------------------------------------

## 6. Phase 3 --- Browser Autofill

**Goal:** Let users select Local Vault credentials from the browser when
signing in.

### 6.1 Form detection

Use a carefully constrained WebView integration to identify likely:

-   Username fields
-   Email fields
-   Password fields
-   Login forms
-   Registration forms
-   Password-change forms

Websites vary widely. Support common HTML patterns first, then add
compatibility improvements based on testing.

### 6.2 Domain matching

Implement a dedicated domain-matching component.

Rules:

-   Normalize scheme, host and internationalized domain names safely.
-   Match exact registrable domains using a maintained Public Suffix
    List implementation.
-   Treat subdomain matching as an explicit policy.
-   Never match unrelated lookalike domains.
-   Do not autofill on insecure or suspicious origins without an
    explicit user decision.
-   Do not rely on page-provided labels alone to establish website
    identity.

### 6.3 Autofill flow

``` text
User focuses a login field
        ↓
Browser validates current page origin
        ↓
Detect likely login form
        ↓
Search unlocked Local Vault
        ↓
Show matching credential suggestions
        ↓
User selects a credential
        ↓
Fill the intended fields
        ↓
User reviews and submits the form
```

The user should remain in control. Do not silently submit login forms.

### 6.4 Android Autofill Framework

There are two different integration surfaces:

1.  **Browser-owned WebView autofill:** The browser can offer its own
    Local Vault suggestions inside its own UI.
2.  **System Autofill Service:** An Android app can implement an
    Autofill Service so other apps can request credentials from it,
    subject to Android's Autofill framework and user settings.

Android generally has one selected system Autofill Service at a time.
Therefore, do not assume the browser can merge Proton Pass and its own
service into one system-wide provider. For the browser's own WebView,
build a browser-side coordinator and investigate supported WebView
Autofill behavior separately.

**Deliverable:** Local Vault suggestions and user-approved autofill work
on common login forms in the browser.

------------------------------------------------------------------------

## 7. Phase 4 --- Save and Update Passwords

**Goal:** Offer to save new credentials and update existing ones after a
password change.

### 7.1 Save detection

-   Detect likely registration or login submissions.
-   Identify candidate username and password values.
-   Wait for a plausible success signal or subsequent navigation.
-   Compare with existing credentials for the same origin and username.
-   Show a save prompt only when appropriate.
-   Save only after user confirmation.

### 7.2 Update detection

-   Match the current origin and account against existing credentials.
-   Detect a likely password-change form or a new password submitted
    during login.
-   Do not assume that a form submission means the password change
    succeeded.
-   Use navigation and other non-invasive signals as supporting
    evidence.
-   Show an update prompt after a likely successful change.
-   Require user confirmation before replacing the saved password.

### 7.3 Prompt actions

-   Update existing credential
-   Save as a new login
-   Not now
-   Never save for this site (optional setting)

Avoid capturing passwords from arbitrary page activity. Limit detection
to recognized credential forms and keep page-to-native communication
narrow and origin-validated.

**Deliverable:** Save and update prompts work reliably on a tested set
of websites, with safe fallback when detection is uncertain.

------------------------------------------------------------------------

## 8. Phase 5 --- Security and Password Health

Implement after core storage and autofill are stable.

  -----------------------------------------------------------------------
  Feature                             Implementation notes
  ----------------------------------- -----------------------------------
  Password strength                   Local estimation; do not send
                                      passwords to a server

  Reused passwords                    Compare in memory after vault
                                      unlock

  Weak password report                Local analysis

  Password history                    Encrypted previous values;
                                      user-configurable retention

  Clipboard clear                     Best-effort delayed clearing; do
                                      not promise guaranteed clearing

  Auto-lock                           Lock after inactivity or configured
                                      app background event

  Biometric unlock                    AndroidX Biometric with a carefully
                                      designed Keystore flow

  Breach check                        Optional Pwned Passwords
                                      k-anonymity range protocol

  Security dashboard                  Summaries without exposing secrets
  -----------------------------------------------------------------------

For breach checks, send only the required hash prefix using the
k-anonymity protocol. Never transmit the full password. Make online
checking optional and explain that it requires network access.

------------------------------------------------------------------------

## 9. Phase 6 --- Proton Pass Integration

**Goal:** Add Proton Pass without making the Local Vault dependent on
it.

### Important capability boundary

Do not assume Proton Pass provides a general public REST API or Android
SDK for arbitrary third-party browser integration. Use only documented
and supported integration routes.

Potential Android approach:

-   Detect whether Proton Pass is installed and available as an Android
    Autofill provider.
-   Provide a clear action to open Android Autofill settings so the user
    can select the preferred system provider.
-   Where supported, allow the user to invoke Proton Pass through
    Android's Autofill experience.
-   Keep Local Vault suggestions available in the browser's own UI.
-   Do not attempt to silently read Proton Pass's private storage.

### Combined browser experience

The browser may present:

-   Local Vault credentials from its own provider.
-   Proton Pass credentials through a supported Android hand-off or
    other documented integration.

If the Android platform does not expose Proton credentials to the
browser as data, show a Proton Pass hand-off/open action rather than
pretending those credentials were merged into the browser's list.

### Provider capabilities

Model provider operations explicitly:

``` kotlin
data class ProviderCapabilities(
    val canListCredentials: Boolean,
    val canReadCredential: Boolean,
    val canCreateCredential: Boolean,
    val canUpdateCredential: Boolean,
    val canDeleteCredential: Boolean,
    val canGeneratePassword: Boolean
)
```

These are capability flags, not guarantees. Populate them only from
verified support.

Do not store Proton account passwords, session cookies or access tokens
in the Local Vault. Do not reverse-engineer private APIs.

**Deliverable:** A safe Proton Pass entry point and a documented account
of which operations are actually supported on Android.

------------------------------------------------------------------------

## 10. Phase 7 --- Unified Password Manager UI

Suggested screens:

1.  Password Manager Home
2.  All Credentials
3.  Local Vault
4.  Proton Pass connection / hand-off
5.  Credential Details
6.  Add / Edit Credential
7.  Password Generator
8.  Security Dashboard
9.  Password History
10. Import / Export
11. Vault and Autofill Settings

Show a clear source label on each item:

-   Local Vault
-   Proton Pass

Do not combine or duplicate records invisibly. If the same login exists
in both providers, show both sources and let the user choose.

------------------------------------------------------------------------

## 11. Phase 8 --- Backup, Restore and Migration

Implement:

-   Encrypted vault backup
-   Restore with integrity verification
-   Backup format versioning
-   Import from supported CSV formats
-   Explicit warning before plaintext export
-   Secure temporary-file handling
-   Duplicate detection during import
-   Clear failure and recovery messages

An encrypted backup should be restorable on another device using a
user-controlled recovery design. Do not make a device-only Keystore key
the sole way to decrypt a portable backup.

Never upload a backup automatically.

------------------------------------------------------------------------

## 12. Phase 9 --- Testing and Security Review

### Unit tests

-   Encryption/decryption round trip
-   Wrong master password
-   Tampered ciphertext
-   Nonce uniqueness strategy
-   Key rotation and format migration
-   Domain normalization and matching
-   Duplicate credential detection
-   Password generator randomness and policy
-   Repository and database migrations

### Android integration tests

-   Create and unlock vault
-   Lock on timeout
-   Background/foreground behavior
-   Biometric unlock
-   WebView login form detection
-   Autofill selection
-   Save prompt
-   Update prompt
-   Offline operation
-   Backup and restore
-   Proton Pass hand-off behavior

### Security tests

-   No secrets in logs or analytics
-   No plaintext credentials in database fields
-   No unrestricted JavaScript bridge
-   Origin validation before autofill
-   Phishing and lookalike-domain tests
-   Screenshot / recent-app privacy policy
-   Clipboard behavior
-   App backup and device-transfer policy
-   Dependency and cryptographic implementation review

Do not release until the vault, key lifecycle, origin checks and
backup/restore behavior have been reviewed.

------------------------------------------------------------------------

## 13. Recommended Implementation Order

    Order Milestone                                   Priority
  ------- ------------------------------------------- ----------
        1 Vault setup, encryption and locking         Critical
        2 Room schema and credential CRUD             Critical
        3 Password generator and folders              High
        4 Domain matcher and WebView form detection   Critical
        5 Local Vault autofill UI                     Critical
        6 Save and update detection                   High
        7 Password health and history                 Medium
        8 Proton Pass supported integration           Medium
        9 Backup, restore and migration               High
       10 Security audit and release                  Critical

------------------------------------------------------------------------

## 14. First Development Sprint

Start with a narrow, testable foundation. Do not begin with Proton
integration or automatic password-change detection.

### Sprint 1 tasks

-   [ ] Create the password-manager package/module.
-   [ ] Add Room and define the initial entities.
-   [ ] Design the versioned encrypted payload format.
-   [ ] Implement master-password setup and verification.
-   [ ] Implement DEK generation and protection.
-   [ ] Implement AES-GCM encrypt/decrypt.
-   [ ] Implement vault lock/unlock state.
-   [ ] Add tests for wrong passwords and tampered encrypted data.
-   [ ] Confirm no credential secrets appear in logs or database
    plaintext columns.

### Sprint 1 completion criteria

A user can create a vault, add a test credential, close the app, reopen
it, unlock the vault and retrieve the credential. The same test must
work offline. Incorrect passwords and modified ciphertext must fail
safely.

------------------------------------------------------------------------

## 15. Official Documentation

-   [Android Autofill
    Framework](https://developer.android.com/guide/topics/text/autofill)
-   [Autofill
    Services](https://developer.android.com/guide/topics/text/autofill-services)
-   [Android
    Keystore](https://developer.android.com/privacy-and-security/keystore)
-   [AndroidX
    Biometric](https://developer.android.com/jetpack/androidx/releases/biometric)
-   [Room Persistence
    Library](https://developer.android.com/training/data-storage/room)
-   [AndroidX
    WebKit](https://developer.android.com/jetpack/androidx/releases/webkit)
-   [Android
    WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
-   [OWASP Mobile Application Security](https://mas.owasp.org/)
-   [OWASP Cryptographic Storage Cheat
    Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
-   [Have I Been Pwned --- Pwned
    Passwords](https://haveibeenpwned.com/API/v3#PwnedPasswords)
-   [Proton Pass CLI
    documentation](https://protonpass.github.io/pass-cli/)

------------------------------------------------------------------------

## Final Recommendation

Build the Android Local Vault first and make it genuinely secure and
useful offline. Add browser autofill next, then save/update prompts.
Treat Proton Pass as an independent integration with verified
capabilities---not as a dependency of the Local Vault.

The first release should prioritize a small number of reliable features
over a large feature list. Encryption, key recovery design, domain
validation and safe autofill are foundational requirements, not optional
polish.

# Creating an unsigned release

    mvn clean package

This command will create the release repository ZIP file in
`io.github.nbauma109.decompiler.assembly/target/enhanced-class-decompiler-<version>.zip`

# GitHub release signing

Public build instructions remain unsigned.
Signed release builds are intended to run from GitHub Actions on tagged releases.

The release workflow signs plugin and feature JARs only when the repository signing secrets are configured.
If no signing secrets are configured, it falls back to an unsigned release.
If only part of the signing configuration is present, the workflow fails fast.

Configure these GitHub repository secrets:

1. `PAT_TOKEN`: personal access token used by the release workflow so the created release can trigger downstream workflows.
2. `ECD_SIGNING_KEYSTORE_BASE64`: Base64-encoded contents of the keystore file.
3. `ECD_SIGNING_STORETYPE`: `PKCS12`.
4. `ECD_SIGNING_ALIAS`: Alias of the signing key inside the keystore.
5. `ECD_SIGNING_STOREPASS`: Keystore password.

Examples for `ECD_SIGNING_KEYSTORE_BASE64`:

Linux / macOS:

    base64 -w 0 path/to/ecd-signing.p12

Windows PowerShell:

    [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\ecd-signing.p12"))

I’ll inspect the repository structure and security-sensitive code paths.











































# Security Analysis: `patrickfav/bcrypt` (v0.10.2)

## Overall Verdict

**No critical security vulnerabilities or external data leaks were found in the core [bcrypt](cci:9://github.com/patrickfav/bcrypt:0:0-0:0) library.** It is safe to use for password hashing in an OAuth authentication server if configured correctly. The only notable leak vector is in the separate CLI tool (not the library artifact), and the only data-exposure paths are through [toString()](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt-cli/src/main/java/at/favre/lib/crypto/bcrypt/cli/Arg.java:54:4-62:5) methods if an application logs them.

## Security Strengths

- **Random salt generation** uses `java.security.SecureRandom` by default, or a caller-provided CSPRNG.
  - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:64`
  - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:229`
- **Constant-time verification** compares hashes with `Bytes.wrap(...).equalsConstantTime(...)`, mitigating timing attacks.
  - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:627`
- **Memory wiping** of intermediate password bytes, hash fragments, and encoded arrays is done with `secureWipe()`.
  - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:231`
  - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:317`
  - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCryptFormatter.java:85-87`
- **Sensitive API** supports `char[]`/`byte[]` to avoid immutable `String` retention.
  - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:185-206`
- **No network, logging, or telemetry** in the core library.
- **Clean dependency tree**: the only production dependency is `at.favre.lib:bytes:1.6.1` (verified via `mvn dependency:tree`). All other dependencies are test-only.
- **No hardcoded secrets** or static secret caches in the source.

## Potential Issues / Caveats

1. **CLI tool leaks passwords through command-line arguments.** The [bcrypt-cli](cci:9://github.com/patrickfav/bcrypt/modules/bcrypt-cli:0:0-0:0) module reads the password from `commandLine.getArgs()[0]`, which exposes it to shell history, process lists (`ps`), and system logs. Do not use the CLI tool for production secrets.
   - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt-cli/src/main/java/at/favre/lib/crypto/bcrypt/cli/CLIParser.java:45`

2. **[toString()](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt-cli/src/main/java/at/favre/lib/crypto/bcrypt/cli/Arg.java:54:4-62:5) methods expose sensitive data if logged.**
   - [Arg.toString()](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt-cli/src/main/java/at/favre/lib/crypto/bcrypt/cli/Arg.java:54:4-62:5) prints the raw password char array.
   - [BCrypt.HashData.toString()](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:383:8-391:9) and [BCrypt.Result.toString()](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:685:8-693:9) expose the raw salt and hash as hex.
   - These are **not** external leaks, but avoid logging these objects.
   - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt-cli/src/main/java/at/favre/lib/crypto/bcrypt/cli/Arg.java:56-62`
   - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:385-391`
   - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:686-693`

3. **Low cost factors are allowed.** The library accepts cost factors 4–31. For an OAuth server, use at least 12 and benchmark latency on your hardware.

4. **Long password behavior.** Default [LongPasswordStrategies.strict()](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/LongPasswordStrategies.java:32:4-40:5) throws for passwords over 72 bytes. If you use a custom strategy (e.g., truncate or SHA-512), you must use the same strategy when verifying.
   - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/LongPasswordStrategies.java:39-41`

5. **72-byte password compatibility.** v0.10.0 changed whether the null terminator counts toward the 72-byte limit. This is a compatibility note, not a security flaw.

6. **Parser edge case.** Very short malformed hashes can throw `BufferUnderflowException` instead of `IllegalBCryptFormatException` in some paths. This is a robustness issue, not a vulnerability.
   - `@/shared2/abstratium/github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCryptParser.java:49-103`

7. **Java memory model limitations.** `secureWipe()` is best-effort; GC copies and JVM heap operations may retain password material. This is a general Java limitation.

## Known CVEs / Dependencies

- Previous test-only CVEs (`CVE-2020-15522` in Bouncy Castle 1.60, `CVE-2020-15250` in JUnit 4.13) were resolved in v0.10.0. They were never in production scope.
- `at.favre.lib:bytes:1.6.1` (production dependency) has no publicly known CVEs.
- `commons-cli:1.6.0` (CLI-only) has no known CVEs at this time.

## Data Leak Assessment

- **External leaks:** None. The core library makes no network calls, writes no logs, and sends no telemetry.
- **Internal leaks:** Possible if you log [BCrypt.Result](cci:2://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:633:4-694:5), [BCrypt.HashData](cci:2://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:324:4-392:5), or CLI [Arg](cci:2://github.com/patrickfav/bcrypt/modules/bcrypt-cli/src/main/java/at/favre/lib/crypto/bcrypt/cli/Arg.java:8:0-66:1) objects. The bcrypt hash string itself is designed to be stored, so the [toString()](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt-cli/src/main/java/at/favre/lib/crypto/bcrypt/cli/Arg.java:54:4-62:5) exposure is equivalent to printing the stored hash.
- **Concrete leak vector:** The CLI tool's use of command-line arguments for passwords.

## Recommendation for Your OAuth Server

Use the core `at.favre.lib:bcrypt` artifact (not [bcrypt-cli](cci:9://github.com/patrickfav/bcrypt/modules/bcrypt-cli:0:0-0:0)), prefer [BCrypt.withDefaults()](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:56:4-64:5) or [BCrypt.with(BCrypt.Version.VERSION_2B)](cci:1://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:66:4-75:5), use cost factor 12 or higher benchmarked on your hardware, and use the `char[]` API. Do not log [BCrypt.Result](cci:2://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:633:4-694:5) or [BCrypt.HashData](cci:2://github.com/patrickfav/bcrypt/modules/bcrypt/src/main/java/at/favre/lib/crypto/bcrypt/BCrypt.java:324:4-392:5) objects. If you need modern memory-hard password hashing, consider migrating to Argon2id.
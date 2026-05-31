#!/usr/bin/env python3
"""
Run Java tests via Maven (./mvnw verify) and extract key information for LLM consumption.
This script ONLY runs Java backend tests (surefire + failsafe). Angular frontend tests
are handled by run-ng-tests.py.
"""

import subprocess
import re
import os
import sys
from datetime import datetime
from pathlib import Path

# Configuration
PROJECT_ROOT = Path(__file__).parent.parent.resolve()
TMP_DIR = PROJECT_ROOT / "tmp"
MAX_TMP_FILES = 10

ANSI_ESCAPE_PATTERN = re.compile(r'\x1b\[[0-9;]*[a-zA-Z]')

# Maven compiler error pattern (standard javac):
# [ERROR] /path/to/File.java:[line,col] error message
COMPILER_ERROR_PATTERN = re.compile(
    r'^\[ERROR\]\s+(/[^\[]+\.java):\[(\d+),(\d+)\]\s+(.*)'
)

# Quarkus ECJ / dev-mode compiler error pattern.
# These appear in two forms in the output:
#
# Form 1 - JSON stack field (most complete, single line):
#   "stack": "java.lang.Error: Unresolved compilation problem: \n\t<message>\n\n\tat pkg.Class.method(File.java:34)\n..."
# Form 2 - Log body (truncated log line + next indented line for message, then stack frames):
#   <long log line ending in Unresolved compilation problem:>
#   \t<message>\n
#   (next lines: \tat dev.pkg.Class.method(File.java:34)...)

# Matches the JSON stack field form:
QUARKUS_JSON_STACK_PATTERN = re.compile(
    r'"stack":\s*"java\.lang\.Error: Unresolved compilation problem:\s*\\n\\t([^\\]+)'
    r'.*?\\n\\tat ([\w$.]+)\.\w+\(([^:)]+\.java):(\d+)\)',
    re.DOTALL
)

# Matches the indented error message line in log form (\t<message>)
# These lines start with a literal tab and contain no brackets or colons (i.e. not stack frames)
QUARKUS_LOG_MSG_PATTERN = re.compile(
    r'^\t([A-Z][^\[\n]+)$'
)
# Stack frame in log form: indented "\tat ..."
QUARKUS_STACK_FRAME_PATTERN = re.compile(
    r'^\tat ([\w$.]+)\.([\w$]+)\(([^:)]+\.java):(\d+)\)'
)

# Maven test failure pattern:
# [ERROR] Tests run: N, Failures: N, Errors: N, Skipped: N, Time elapsed: ... <<< FAILURE! - in ClassName
TESTS_RUN_FAILURE_PATTERN = re.compile(
    r'\[ERROR\]\s+Tests run:.*<<<\s*(FAILURE|ERROR)!'
)

# Individual test failure/error lines
# [ERROR] methodName  Time elapsed: 0.123 s  <<< FAILURE!
# [ERROR] methodName  Time elapsed: 0.123 s  <<< ERROR!
TEST_METHOD_FAILURE_PATTERN = re.compile(
    r'^\[ERROR\]\s+(\S+)\s+Time elapsed:.*<<<\s*(FAILURE|ERROR)!'
)

# BUILD SUCCESS / FAILURE
BUILD_SUCCESS_PATTERN = re.compile(r'^\[INFO\]\s+BUILD SUCCESS')
BUILD_FAILURE_PATTERN = re.compile(r'^\[INFO\]\s+BUILD FAILURE')

# Tests run summary line from surefire.
# Per-class lines use [INFO], grand total when failures exist uses [ERROR]:
# [INFO] Tests run: N, Failures: N, Errors: N, Skipped: N, Time elapsed: ...
# [ERROR] Tests run: N, Failures: N, Errors: N, Skipped: N
TESTS_RUN_SUMMARY_PATTERN = re.compile(
    r'(?:\[INFO\]|\[ERROR\])\s+Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)'
)
# Grand total line is the one WITHOUT a Time elapsed suffix — use it for the overall counters
TESTS_GRAND_TOTAL_PATTERN = re.compile(
    r'^\[ERROR\]\s+Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)\s*$'
)

# [INFO] Results: (surefire overall results header)
RESULTS_HEADER_PATTERN = re.compile(r'^\[INFO\]\s+Results:')

# Failure detail lines inside surefire output (indented under a test class)
# e.g.   SomeTestClass.someMethod:42  expected: <...> but was: <...>
FAILURE_DETAIL_PATTERN = re.compile(r'^\[ERROR\]\s+\s+(.+)')

# java.lang.AssertionError or similar exception lines inside failure output
EXCEPTION_PATTERN = re.compile(r'(java\.\w+\.(?:Exception|Error|Throwable)\S*)')


def cleanup_old_tmp_files():
    """Remove oldest files if there are more than MAX_TMP_FILES in tmp directory."""
    if not TMP_DIR.exists():
        TMP_DIR.mkdir(parents=True, exist_ok=True)
        return

    files = sorted(TMP_DIR.glob("java-test-*.txt"), key=lambda p: p.stat().st_mtime, reverse=True)
    if len(files) > MAX_TMP_FILES:
        for old_file in files[MAX_TMP_FILES:]:
            try:
                old_file.unlink()
                print(f"[cleanup] Removed old file: {old_file.name}")
            except OSError as e:
                print(f"[cleanup] Error removing {old_file}: {e}")


def run_mvn_test():
    """Run mvn test and capture all output."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_file = TMP_DIR / f"java-test-{timestamp}.txt"

    TMP_DIR.mkdir(parents=True, exist_ok=True)

    # exec-maven-plugin runs Angular tests during the test phase,
    # so we skip it to keep this script Java-backend only.
    # exec.skip skips the exec-maven-plugin, used to add angular tests to the build
    cmd = ["./mvnw", "verify", "-B", "-Dexec.skip=true"]

    print(f"[run] Executing: {' '.join(cmd)}")
    print(f"[run] Working directory: {PROJECT_ROOT}")
    print(f"[run] Full output will be saved to: {output_file}")
    print()

    try:
        with open(output_file, "w") as f:
            process = subprocess.Popen(
                cmd,
                cwd=PROJECT_ROOT,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                universal_newlines=True
            )

            for line in process.stdout:
                f.write(line)
                f.flush()

            process.wait()
            return output_file, process.returncode

    except Exception as e:
        print(f"[error] Failed to run mvn test: {e}")
        sys.exit(1)


def strip_ansi(text):
    """Remove ANSI escape codes from text."""
    return ANSI_ESCAPE_PATTERN.sub('', text)


def parse_output(output_file):
    """Parse the mvn test output file and extract relevant information."""
    compiler_errors = []
    quarkus_compile_errors = []  # Keyed by (file, line) to deduplicate
    quarkus_compile_error_set = set()  # (file, line, message) tuples seen
    test_failures = []
    build_success = False
    build_failure = False
    total_run = 0
    total_failures = 0
    total_errors = 0
    total_skipped = 0

    with open(output_file, "r") as f:
        raw_lines = f.readlines()

    lines = [strip_ansi(line) for line in raw_lines]

    i = 0
    in_failure_detail = False
    in_quarkus_log_error = False
    current_quarkus_log_error = None

    while i < len(lines):
        line = lines[i]

        # Build outcome
        if BUILD_SUCCESS_PATTERN.search(line):
            build_success = True
            build_failure = False
            i += 1
            continue

        if BUILD_FAILURE_PATTERN.search(line):
            build_failure = True
            build_success = False
            i += 1
            continue

        # Standard javac compiler errors
        comp_match = COMPILER_ERROR_PATTERN.match(line)
        if comp_match:
            compiler_errors.append({
                "file": comp_match.group(1),
                "line": int(comp_match.group(2)),
                "col": int(comp_match.group(3)),
                "message": comp_match.group(4).strip(),
                "context_lines": []
            })
            j = i + 1
            context_count = 0
            while j < len(lines) and context_count < 3:
                ctx = lines[j].rstrip()
                if ctx.startswith("[ERROR]") or ctx.startswith("[INFO]") or ctx.startswith("[WARNING]"):
                    break
                if ctx.strip():
                    compiler_errors[-1]["context_lines"].append(ctx)
                    context_count += 1
                j += 1
            i += 1
            continue

        # Quarkus ECJ: Form 1 - JSON stack field (most complete, no line-truncation issues)
        # "stack": "java.lang.Error: Unresolved compilation problem: \n\t<msg>\n\n\tat pkg.Cls.m(F.java:N)"
        json_stack_match = QUARKUS_JSON_STACK_PATTERN.search(line)
        if json_stack_match:
            msg = json_stack_match.group(1).strip()
            class_fqn = json_stack_match.group(2)
            file_name = json_stack_match.group(3)
            line_num = int(json_stack_match.group(4))
            # Skip _Subclass frames — they shouldn't appear first, but guard anyway
            if "_Subclass" not in class_fqn and "_Bean" not in class_fqn:
                key = (file_name, line_num, msg)
                if key not in quarkus_compile_error_set:
                    quarkus_compile_error_set.add(key)
                    quarkus_compile_errors.append({
                        "message": msg,
                        "file": file_name,
                        "line": line_num,
                        "class": class_fqn,
                    })
            i += 1
            continue

        # Quarkus ECJ: Form 2 - log body (tab-indented message line followed by stack frames)
        # The preceding long log line was truncated so we detect by indented message + stack pattern
        log_msg_match = QUARKUS_LOG_MSG_PATTERN.match(line)
        if log_msg_match and not in_quarkus_log_error:
            # Check if next non-empty line is a stack frame
            peek = i + 1
            while peek < len(lines) and not lines[peek].strip():
                peek += 1
            if peek < len(lines) and QUARKUS_STACK_FRAME_PATTERN.match(lines[peek]):
                current_quarkus_log_error = {"message": log_msg_match.group(1).strip(), "file": "", "line": 0, "class": ""}
                in_quarkus_log_error = True
                i += 1
                continue

        if in_quarkus_log_error and current_quarkus_log_error is not None:
            frame_match = QUARKUS_STACK_FRAME_PATTERN.match(line)
            if frame_match:
                class_fqn = frame_match.group(1)
                file_name = frame_match.group(3)
                line_num = int(frame_match.group(4))
                if "_Subclass" not in class_fqn and "_Bean" not in class_fqn and not current_quarkus_log_error["file"]:
                    current_quarkus_log_error["file"] = file_name
                    current_quarkus_log_error["line"] = line_num
                    current_quarkus_log_error["class"] = class_fqn
                    key = (file_name, line_num, current_quarkus_log_error["message"])
                    if key not in quarkus_compile_error_set:
                        quarkus_compile_error_set.add(key)
                        quarkus_compile_errors.append(dict(current_quarkus_log_error))
                    in_quarkus_log_error = False
                    current_quarkus_log_error = None
            elif not line.startswith("\t") and line.strip():
                in_quarkus_log_error = False
                current_quarkus_log_error = None
            i += 1
            continue

        # Grand total line: [ERROR] Tests run: N, Failures: N, Errors: N, Skipped: N
        grand_total_match = TESTS_GRAND_TOTAL_PATTERN.match(line)
        if grand_total_match:
            total_run = int(grand_total_match.group(1))
            total_failures = int(grand_total_match.group(2))
            total_errors = int(grand_total_match.group(3))
            total_skipped = int(grand_total_match.group(4))
            i += 1
            continue

        # Per-class [INFO] Tests run summary (only accumulate when no grand total yet)
        if line.startswith("[INFO]"):
            summary_match = TESTS_RUN_SUMMARY_PATTERN.search(line)
            if summary_match:
                total_run += int(summary_match.group(1))
                total_failures += int(summary_match.group(2))
                total_errors += int(summary_match.group(3))
                total_skipped += int(summary_match.group(4))
                i += 1
                continue

        # Test failure markers (class-level: <<< FAILURE! - in ClassName)
        class_fail_match = TESTS_RUN_FAILURE_PATTERN.search(line)
        if class_fail_match:
            test_failures.append({
                "class_line": line.strip(),
                "details": []
            })
            in_failure_detail = True
            i += 1
            continue

        # Collect detail lines following a failure marker (skip stack frames)
        if in_failure_detail and test_failures:
            stripped = line.strip()
            if stripped.startswith("[INFO]") or stripped.startswith("[WARNING]"):
                in_failure_detail = False
            elif stripped and not stripped.startswith("at ") and not stripped.startswith("... "):
                test_failures[-1]["details"].append(stripped)
            i += 1
            continue

        i += 1

    return {
        "compiler_errors": compiler_errors,
        "quarkus_compile_errors": quarkus_compile_errors,
        "test_failures": test_failures,
        "build_success": build_success,
        "build_failure": build_failure,
        "total_run": total_run,
        "total_failures": total_failures,
        "total_errors": total_errors,
        "total_skipped": total_skipped,
    }


def show_error_grep(output_file):
    """Show grep-like output for ERROR/FAILED lines from the raw output file."""
    print("-" * 40)
    print("ERROR/FAILURE LINES (grep-like, with context):")
    print("-" * 40)
    try:
        with open(output_file, "r") as f:
            raw_lines = f.readlines()

        lines = [strip_ansi(line) for line in raw_lines]

        # Patterns that are useful signal
        signal_pattern = re.compile(
            r'\[ERROR\]|\bFAILED\b|\bFAILURE\b|\bERROR\b|'
            r'cannot find symbol|symbol:|location:|error:|'
            r'AssertionError|NullPointerException|Exception',
            re.IGNORECASE
        )
        # Patterns that are noise (duplicate output from maven boilerplate)
        noise_pattern = re.compile(
            r'^\[INFO\]\s*$|'
            r'^\[INFO\]\s*-{10,}|'
            r'^\[INFO\]\s*BUILD|'
            r'^\[INFO\] Total time|'
            r'^\[INFO\] Finished at|'
            r'^\[INFO\] Final Memory'
        )

        printed_lines = set()
        for idx, line in enumerate(lines):
            if noise_pattern.search(line):
                continue
            if signal_pattern.search(line):
                # Print up to 2 lines before and after for context
                start = max(0, idx - 2)
                end = min(len(lines), idx + 3)
                for ctx_idx in range(start, end):
                    key = ctx_idx
                    if key not in printed_lines:
                        marker = ">" if ctx_idx == idx else " "
                        print(f"  {ctx_idx+1}{marker} {lines[ctx_idx].rstrip()[:150]}")
                        printed_lines.add(key)
                print()
    except Exception as e:
        print(f"  (Could not read error details: {e})")
    print()


def print_summary(results, output_file):
    """Print a concise summary of mvn test results."""
    print("=" * 60)
    print("JAVA TEST RESULTS")
    print("=" * 60)
    print()

    # Standard javac compiler errors (most critical - shown first)
    if results["compiler_errors"]:
        print("-" * 40)
        print(f"COMPILER ERRORS ({len(results['compiler_errors'])}):")
        print("-" * 40)
        for err in results["compiler_errors"]:
            file_path = err["file"]
            try:
                file_path = str(Path(err["file"]).relative_to(PROJECT_ROOT))
            except ValueError:
                pass
            print(f"\n  [{err['line']}:{err['col']}] {file_path}")
            print(f"  ERROR: {err['message']}")
            if err["context_lines"]:
                for ctx in err["context_lines"]:
                    print(f"    {ctx}")
        print()

    # Quarkus ECJ / unresolved compilation problems
    if results["quarkus_compile_errors"]:
        print("-" * 40)
        print(f"QUARKUS COMPILATION ERRORS ({len(results['quarkus_compile_errors'])}):")
        print("-" * 40)
        for err in results["quarkus_compile_errors"]:
            print(f"\n  {err['message']}")
            if err["file"] and err["line"]:
                print(f"  Location: {err['class']} ({err['file']}:{err['line']})")
        print()

    # Test failures
    if results["test_failures"]:
        print("-" * 40)
        print(f"TEST FAILURES ({len(results['test_failures'])}):")
        print("-" * 40)
        for failure in results["test_failures"]:
            print(f"\n  {failure['class_line']}")
            # Show first 10 detail lines (error messages, not full stack)
            shown = 0
            for detail in failure["details"]:
                if shown >= 10:
                    print("    ... (more details in full output)")
                    break
                print(f"    {detail[:150]}")
                shown += 1
        print()

    # Overall status
    print("=" * 60)
    if results["build_success"]:
        print("[STATUS] BUILD SUCCESS")
    elif results["build_failure"]:
        print("[STATUS] BUILD FAILURE")
    else:
        print("[STATUS] UNKNOWN (check full output)")

    if results["total_run"] > 0:
        print(
            f"[SUMMARY] Tests run: {results['total_run']}, "
            f"Failures: {results['total_failures']}, "
            f"Errors: {results['total_errors']}, "
            f"Skipped: {results['total_skipped']}"
        )

    print()

    # If there are any errors, show the grep-style section
    if results["compiler_errors"] or results["quarkus_compile_errors"] or results["test_failures"] or results["build_failure"]:
        show_error_grep(output_file)

    print(f"Full output saved to: {output_file}")
    print("=" * 60)


def main():
    cleanup_old_tmp_files()
    output_file, return_code = run_mvn_test()
    results = parse_output(output_file)
    print_summary(results, output_file)
    sys.exit(return_code)


if __name__ == "__main__":
    main()

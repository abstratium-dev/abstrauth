#!/usr/bin/env python3
"""
Run Angular tests via npm and extract key information for LLM consumption.
Mimics how Maven runs tests: sources nvm, uses node v24.11.1, runs ChromeHeadless with coverage.
"""

import subprocess
import re
import os
import sys
import glob
from datetime import datetime
from pathlib import Path

# Configuration
PROJECT_ROOT = Path(__file__).parent.parent.resolve()
WEBUI_DIR = PROJECT_ROOT / "src" / "main" / "webui"
TMP_DIR = PROJECT_ROOT / "tmp"
MAX_TMP_FILES = 10

# Patterns to skip (noise reduction)
SKIP_PATTERNS = [
    re.compile(r'^\s*chunk-.*\.js\s*\|'),  # Chunk files table
    re.compile(r'^\s*polyfills\.js\s*\|'),
    re.compile(r'^\s*main\.js\s*\|'),
    re.compile(r'^\s*styles\.css\s*\|'),
    re.compile(r'^\s*-\s*\|.*\|.*\|'),  # Separator lines in bundle table
    re.compile(r'^\s*Initial chunk files\s*\|'),
    re.compile(r'^\s*Initial total\s*\|'),
    re.compile(r'^\s*Application bundle generation complete\.'),
    re.compile(r'^\d{2}\s+\d{2}\s+\d{4}\s+\d{2}:\d{2}:\d{2}\.\d+:(WARN|INFO)\s+\[karma'),  # Karma server logs
    re.compile(r'^\s*DEBUG:\s*\'\[AUTH\]'),  # Auth debug messages
    re.compile(r'^\s*Chrome\s+\d+\.\d+\.\d+\.\d+.*: Executed \d+ of \d+ SUCCESS'),  # Successful tests
    re.compile(r'^\s*ERROR:\s*\'\[AUTH\]'),  # Auth error logs (usually expected in tests)
    re.compile(r'^\s*DEBUG:\s*\'Token expires in\''),  # Token expiration debug messages
    re.compile(r'^\s*$'),  # Empty lines
]

# Patterns that indicate coverage summary
COVERAGE_PATTERNS = [
    re.compile(r'Code coverage\s*[:\s]*', re.IGNORECASE),
    re.compile(r'Statements\s*[:\s]*\d+', re.IGNORECASE),
    re.compile(r'Branches\s*[:\s]*\d+', re.IGNORECASE),
    re.compile(r'Functions\s*[:\s]*\d+', re.IGNORECASE),
    re.compile(r'Lines\s*[:\s]*\d+', re.IGNORECASE),
    re.compile(r'Filename\s*\|\s*%\s*Stmts', re.IGNORECASE),
    re.compile(r'[-]+\|[-]+'),  # Coverage table separator
]

# Pattern for FAILED tests - captures the test name
# Format: "Chrome Headless 148.0.0.0 (Linux 0.0.0) Test Name Here FAILED [error message]"
FAILED_TEST_PATTERN = re.compile(r'^(Chrome(?:\s+Headless)?\s+\S+\s+\([^)]+\))\s+(.+?)\s+FAILED')

# Pattern for stack trace frames - keep only source code frames (not node_modules)
SOURCE_CODE_FRAME_PATTERN = re.compile(r'^\s+at\s+.*\(src/.*:\d+:\d+\)')
NODE_MODULES_FRAME_PATTERN = re.compile(r'^\s+at\s+.*\(node_modules/')
GENERIC_FRAME_PATTERN = re.compile(r'^\s+at\s+')

# Pattern for overall result
OVERALL_SUCCESS_PATTERN = re.compile(r'TOTAL:\s+(\d+)\s+SUCCESS')
OVERALL_FAILURE_PATTERN = re.compile(r'TOTAL:\s+(\d+)\s+FAILED')

# Pattern for karma progress lines (e.g., "Executed 235 of 258 (55 FAILED)")
KARMA_PROGRESS_PATTERN = re.compile(r'^\s*Executed\s+\d+\s+of\s+\d+\s+\(\d+\s+FAILED\)')

# Pattern for TypeScript compilation errors
TSC_ERROR_START_PATTERN = re.compile(r'^\s*[✘]?\s*\[ERROR\]\s+(TS\d+):\s*(.*)')
TSC_ERROR_LOCATION_PATTERN = re.compile(r'^\s*src/.*\.ts:\d+:\d+:$')


def cleanup_old_tmp_files():
    """Remove oldest files if there are more than MAX_TMP_FILES in tmp directory."""
    if not TMP_DIR.exists():
        TMP_DIR.mkdir(parents=True, exist_ok=True)
        return

    files = sorted(TMP_DIR.glob("*.txt"), key=lambda p: p.stat().st_mtime, reverse=True)
    if len(files) > MAX_TMP_FILES:
        for old_file in files[MAX_TMP_FILES:]:
            try:
                old_file.unlink()
                print(f"[cleanup] Removed old file: {old_file.name}")
            except OSError as e:
                print(f"[cleanup] Error removing {old_file}: {e}")


def run_tests():
    """Run npm test and capture all output."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_file = TMP_DIR / f"ng-test-{timestamp}.txt"

    # Ensure tmp directory exists
    TMP_DIR.mkdir(parents=True, exist_ok=True)

    # Command to run - same as Maven: source nvm, use node v24.11.1, run npm test
    cmd = [
        "bash", "-c",
        "source ~/.nvm/nvm.sh && nvm use v24.11.1 && npm test -- --browsers=ChromeHeadless --code-coverage --watch=false"
    ]

    print(f"[run] Executing: {' '.join(cmd)}")
    print(f"[run] Working directory: {WEBUI_DIR}")
    print(f"[run] Full output will be saved to: {output_file}")
    print()

    try:
        with open(output_file, "w") as f:
            process = subprocess.Popen(
                cmd,
                cwd=WEBUI_DIR,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                universal_newlines=True
            )

            # Write all output to file in real-time
            for line in process.stdout:
                f.write(line)
                f.flush()

            process.wait()
            return output_file, process.returncode

    except Exception as e:
        print(f"[error] Failed to run tests: {e}")
        sys.exit(1)


def should_skip_line(line):
    """Check if a line should be skipped (noise reduction)."""
    for pattern in SKIP_PATTERNS:
        if pattern.match(line):
            return True
    return False


def is_coverage_line(line):
    """Check if a line is part of coverage summary."""
    for pattern in COVERAGE_PATTERNS:
        if pattern.match(line):
            return True
    return False


def is_source_code_frame(line):
    """Check if a stack trace frame is from source code (not node_modules)."""
    return SOURCE_CODE_FRAME_PATTERN.match(line) is not None


def is_node_modules_frame(line):
    """Check if a stack trace frame is from node_modules."""
    return NODE_MODULES_FRAME_PATTERN.match(line) is not None


def is_any_frame(line):
    """Check if line is any stack frame."""
    return GENERIC_FRAME_PATTERN.match(line) is not None


ANSI_ESCAPE_PATTERN = re.compile(r'\x1b\[[0-9;]*[a-zA-Z]')


def strip_ansi(text):
    """Remove ANSI escape codes from text."""
    return ANSI_ESCAPE_PATTERN.sub('', text)


def combine_wrapped_lines(lines):
    """Combine lines that are wrapped due to terminal width.

    When output is wrapped, FAILED test lines look like:
      Chrome Headless 148.0.0.0 (Linux 0.0.0) RulesComponent should
       show empty message when no rules FAILED
    This function combines them into single lines.
    """
    combined = []
    i = 0
    while i < len(lines):
        line = lines[i]
        clean_line = strip_ansi(line)
        # Check if this line starts with Chrome but doesn't have FAILED/SUCCESS (might be wrapped)
        if (re.match(r'^Chrome(?:\s+Headless)?\s+\S+\s+\([^)]+\)', clean_line) and
            'FAILED' not in clean_line and 'SUCCESS' not in clean_line):
            # Look ahead to find the continuation (next line starts with whitespace)
            buffered = clean_line
            j = i + 1
            while j < len(lines) and 'FAILED' not in buffered:
                next_line = strip_ansi(lines[j])
                # Only combine if next line starts with whitespace (continuation)
                if not next_line.startswith(' ') and not next_line.startswith('\t'):
                    break
                buffered += " " + next_line.strip()
                j += 1
                if 'FAILED' in buffered:
                    break
            combined.append(buffered)
            i = j
        else:
            combined.append(clean_line)
            i += 1
    return combined


def parse_output(output_file):
    """Parse the test output file and extract relevant information."""
    failed_tests = []
    coverage_lines = []
    tsc_errors = []  # TypeScript compilation errors
    current_failed_test = None
    in_failed_test = False
    in_tsc_error = False
    current_tsc_error = None
    total_tests = 0
    success_tests = 0
    failed_count = 0
    overall_success = False

    with open(output_file, "r") as f:
        raw_lines = f.readlines()

    # First, combine wrapped lines to handle terminal width wrapping
    lines = combine_wrapped_lines(raw_lines)

    i = 0
    while i < len(lines):
        line = lines[i]

        # Check for overall result
        success_match = OVERALL_SUCCESS_PATTERN.search(line)
        if success_match:
            overall_success = True
            total_tests = int(success_match.group(1))
            success_tests = total_tests
            i += 1
            continue

        failure_match = OVERALL_FAILURE_PATTERN.search(line)
        if failure_match:
            overall_success = False
            failed_count = int(failure_match.group(1))
            i += 1
            continue

        # Check for failed test start
        failed_match = FAILED_TEST_PATTERN.match(line)
        if failed_match:
            if current_failed_test:
                failed_tests.append(current_failed_test)
            test_name = f"{failed_match.group(1).strip()} {failed_match.group(2).strip()}"
            current_failed_test = {
                "name": test_name,
                "error": "",
                "stack_frames": [],
                "raw_lines": []  # First few lines of raw output for context
            }
            in_failed_test = True
            i += 1
            continue

        # If in failed test section
        if in_failed_test:
            # Check for next failed test or TOTAL line that ends this test
            if FAILED_TEST_PATTERN.match(line) or OVERALL_SUCCESS_PATTERN.search(line) or OVERALL_FAILURE_PATTERN.search(line):
                if current_failed_test:
                    failed_tests.append(current_failed_test)
                    current_failed_test = None
                in_failed_test = False
                continue

            # Store first 5 raw lines for context (skip karma progress lines)
            if len(current_failed_test["raw_lines"]) < 5 and not KARMA_PROGRESS_PATTERN.match(line):
                current_failed_test["raw_lines"].append(line.rstrip())

            # Check if this is an error message line (starts with "Error:")
            if line.strip().startswith("Error:") or line.strip().startswith("Expected"):
                current_failed_test["error"] += line.strip() + "\n"
                i += 1
                continue

            # Check for stack frames - keep only source code frames
            if is_source_code_frame(line):
                frame = line.strip()
                # Clean up the frame to be more readable
                frame = re.sub(r'^\s+at\s+', '  at ', frame)
                current_failed_test["stack_frames"].append(frame)
            # Skip node_modules frames silently
            elif is_node_modules_frame(line):
                pass
            # Include other error context lines
            elif line.strip() and not is_any_frame(line):
                current_failed_test["error"] += line.strip() + "\n"

            i += 1
            continue

        # Check for TypeScript compilation errors
        tsc_match = TSC_ERROR_START_PATTERN.match(line)
        if tsc_match:
            if current_tsc_error:
                tsc_errors.append(current_tsc_error)
            current_tsc_error = {
                "code": tsc_match.group(1),
                "message": tsc_match.group(2),
                "location": "",
                "code_snippet": ""
            }
            in_tsc_error = True
            i += 1
            continue

        if in_tsc_error:
            # Check if this is a location line (e.g., "src/app/...ts:45:53:")
            if re.match(r'^\s*src/.*\.ts:\d+:\d+:', line):
                current_tsc_error["location"] = line.strip()
                i += 1
                continue
            # Check if this is a code snippet line (starts with digits and │)
            if re.match(r'^\s*\d+\s*[│╵]', line):
                current_tsc_error["code_snippet"] += line
                i += 1
                continue
            # Check if this is a property declaration line (e.g., "'aiEnabled' is declared here")
            if re.match(r'^\s*[│╵]?\s*\w+.*is declared here', line) or line.strip().startswith("[ERROR]"):
                if line.strip().startswith("[ERROR]"):
                    # New error starts
                    tsc_errors.append(current_tsc_error)
                    current_tsc_error = None
                    in_tsc_error = False
                    continue
                current_tsc_error["code_snippet"] += line
                i += 1
                continue
            # End of TypeScript error block
            if current_tsc_error:
                tsc_errors.append(current_tsc_error)
                current_tsc_error = None
            in_tsc_error = False
            continue

        # Check for coverage lines
        if is_coverage_line(line):
            coverage_lines.append(line.rstrip())
            i += 1
            continue

        i += 1

    # Don't forget the last failed test
    if current_failed_test:
        failed_tests.append(current_failed_test)

    # Don't forget the last TypeScript error
    if current_tsc_error:
        tsc_errors.append(current_tsc_error)

    return {
        "failed_tests": failed_tests,
        "tsc_errors": tsc_errors,
        "coverage": coverage_lines,
        "overall_success": overall_success,
        "total_tests": total_tests,
        "success_tests": success_tests,
        "failed_count": failed_count
    }


def print_summary(results, output_file):
    """Print a concise summary of test results."""
    print("=" * 60)
    print("ANGULAR TEST RESULTS")
    print("=" * 60)
    print()

    # TypeScript compilation errors (at the top, most critical)
    if results["tsc_errors"]:
        print("-" * 40)
        print(f"TYPESCRIPT ERRORS ({len(results['tsc_errors'])}):")
        print("-" * 40)
        for err in results["tsc_errors"]:
            print(f"\n[{err['code']}] {err['message']}")
            if err["location"]:
                print(f"  Location: {err['location']}")
            if err["code_snippet"]:
                # Indent the code snippet
                for snippet_line in err["code_snippet"].strip().split('\n')[:5]:  # Limit to 5 lines
                    print(f"  {snippet_line}")
        print()

    # Coverage info
    if results["coverage"]:
        print("-" * 40)
        print("CODE COVERAGE:")
        print("-" * 40)
        for line in results["coverage"]:
            print(line)
        print()

    # Failed tests with error deduplication across tests
    if results["failed_tests"]:
        print("-" * 40)
        print(f"FAILED TESTS ({len(results['failed_tests'])}):")
        print("-" * 40)

        # Cache of error signatures seen across all tests
        error_cache = {}

        for test in results["failed_tests"]:
            print(f"\n[FAILED] {test['name']}")

            # Build error signature from first error line
            error_sig = None
            if test["error"]:
                first_error = test["error"].strip().split('\n')[0]
                if first_error:
                    error_sig = first_error[:100]  # Use first 100 chars as signature

            # Check if we've seen this error before
            if error_sig and error_sig in error_cache:
                print(f"  (same error as: {error_cache[error_sig]})")
            else:
                # Show full details for new errors
                # Show first few raw lines for immediate context
                if test["raw_lines"]:
                    print("  Raw output:")
                    for raw_line in test["raw_lines"][:3]:  # Limit to first 3 lines
                        if raw_line.strip():  # Skip empty lines
                            print(f"    {raw_line}")

                if test["error"]:
                    # Deduplicate error messages within this test
                    error_lines = [l for l in test["error"].strip().split('\n') if l]
                    seen = set()
                    for err_line in error_lines[:3]:  # Limit to first 3 error lines
                        if err_line not in seen:
                            print(f"  Error: {err_line}")
                            seen.add(err_line)

                if test["stack_frames"]:
                    print("  Stack trace (source code only):")
                    for frame in test["stack_frames"][:2]:  # Limit to first 2 frames
                        print(f"    {frame}")

                # Add to cache
                if error_sig:
                    error_cache[error_sig] = test["name"]
        print()

    # Overall status at the end
    print("=" * 60)
    if results["overall_success"] and not results["failed_tests"]:
        print("[STATUS] ALL TESTS PASSED")
    elif results["overall_success"] and results["failed_tests"]:
        print("[STATUS] PARTIAL SUCCESS - Some tests passed but failures detected")
    else:
        print("[STATUS] TESTS FAILED")

    if results["total_tests"] > 0:
        print(f"[SUMMARY] {results['success_tests']}/{results['total_tests']} tests passed")
    if results["failed_count"] > 0:
        print(f"[SUMMARY] {results['failed_count']} tests failed")

    print()

    # If there are errors, show a grep-like output of ERROR/FAILED lines
    if results["failed_tests"] or results["tsc_errors"] or results["failed_count"] > 0:
        show_error_grep(output_file)

    print(f"Full output saved to: {output_file}")
    print("=" * 60)


def show_error_grep(output_file):
    """Show grep-like output for ERROR/FAILED/Error lines from the output file."""
    print("-" * 40)
    print("ERROR LINES (grep-like output):")
    print("-" * 40)
    try:
        with open(output_file, "r") as f:
            raw_lines = f.readlines()

        # Strip ANSI codes from all lines
        lines = [strip_ansi(line) for line in raw_lines]

        error_pattern = re.compile(r'ERROR|FAILED|Error', re.IGNORECASE)
        for i, line in enumerate(lines, 1):
            if error_pattern.search(line):
                # Show line with context (1 line before and after)
                context = []
                if i > 1:
                    prev_line = lines[i - 2].rstrip()
                    if prev_line:
                        context.append(f"{i-1}-{prev_line[:100]}")
                context.append(f"{i}:{line.rstrip()[:100]}")
                if i < len(lines):
                    next_line = lines[i].rstrip()
                    if next_line:
                        context.append(f"{i+1}-{next_line[:100]}")
                for ctx_line in context:
                    print(f"  {ctx_line}")
                print()
    except Exception as e:
        print(f"  (Could not read error details: {e})")
    print()


def main():
    cleanup_old_tmp_files()
    output_file, return_code = run_tests()
    results = parse_output(output_file)
    print_summary(results, output_file)

    # Exit with appropriate code
    sys.exit(return_code)


if __name__ == "__main__":
    main()

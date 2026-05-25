#!/usr/bin/env python3

# https://docs.windsurf.com/windsurf/cascade/hooks#blocking-dangerous-commands

import sys
import json
import os
import re
import shlex

# Get the current working directory - this is the allowed prefix
ALLOWED_PREFIX = os.getcwd()
print(f"[block_dangerous_commands] Allowed prefix: {ALLOWED_PREFIX}", file=sys.stderr)

# Denied prefixes - directories that should never be accessed
DENIED_PREFIXES = [
    "/etc",
    "/var",
    "/sys",
    "/proc",
    "/dev",
    "/boot",
    "/root",
    "/tmp",
    "/temp",
    "/p/",
    "/r/",
    "/t/",
    "/w/",
    "/z/",
    "/usr/bin",
    "/usr/sbin",
    "/bin",
    "/sbin",
    "/lib",
    "/lib64",
    "/home",
    os.path.expanduser("~/.ssh"),
    os.path.expanduser("~/.aws"),
    os.path.expanduser("~/.config"),
]

# Commands that commonly take file paths as arguments
PATH_TAKING_COMMANDS = [
    "cat", "less", "more", "head", "tail", "grep", "find", "awk", "sed",
    "cp", "mv", "rm", "touch", "ls", "dir", "cd", "nano", "vim", "vi",
    "python", "python3", "java", "node", "npm", "mvn", "gradle",
    "chmod", "chown", "tar", "zip", "unzip", "gzip", "gunzip",
    "curl", "wget", "ssh", "scp", "rsync",
    ">>", ">", "<", "|",
]

# Regex to detect potential paths in command strings
# Matches absolute paths, relative paths with ./ or ../, and bare relative paths
PATH_PATTERN = re.compile(r'(?:^|\s)(/?(?:[\w.-]+/)*\.\.?/?(?:[\w.-]+/)*[\w.-]*|/[\w/.-]+)')


def normalize_path(path):
    """Normalize a path to its absolute form."""
    if not path:
        return None
    
    # Handle tilde expansion
    if path.startswith("~"):
        path = os.path.expanduser(path)
    
    # Convert to absolute path
    # If relative, it's relative to current working directory
    try:
        return os.path.abspath(path)
    except Exception:
        return None


def is_path_allowed(normalized_path):
    """Check if a path is within allowed prefix and not in denied prefixes."""
    if not normalized_path:
        return True  # Can't determine, assume safe
    
    # Check against denied prefixes
    for denied in DENIED_PREFIXES:
        if normalized_path.startswith(denied):
            return False, f"Access to '{denied}' directory is denied"
    
    # Check if within allowed prefix
    if not normalized_path.startswith(ALLOWED_PREFIX):
        return False, f"Access outside project directory '{ALLOWED_PREFIX}' is denied"
    
    return True, None


def is_hooks_config_file(path):
    """Check if a path is a hooks configuration file."""
    if not path:
        return False
    
    # Normalize to basename for checking
    basename = os.path.basename(path)
    
    # Check for hooks.json, hook.json, or similar variations
    hooks_patterns = ["hooks.json", "hook.json", "hooks.jsonl"]
    for pattern in hooks_patterns:
        if pattern in path.lower() or basename.lower().endswith(pattern):
            return True
    
    return False


def is_write_operation(command):
    """Check if a command appears to be a write operation."""
    # Redirections that write to files
    write_redirections = [">", ">>", "| tee", "|tee"]
    for redir in write_redirections:
        if redir in command:
            return True
    
    # Commands that write files
    write_commands = ["cat", "echo", "printf", "sed -i", "sed --in-place", 
                      "tee", "cp", "mv", "touch", "chmod", "chown"]
    cmd_lower = command.lower()
    for write_cmd in write_commands:
        if cmd_lower.startswith(write_cmd + " ") or " " + write_cmd + " " in cmd_lower:
            return True
    
    return False


def extract_paths_from_command(command):
    """Extract potential file paths from a command string."""
    paths = []
    
    # Try to parse with shlex to handle quoting
    try:
        args = shlex.split(command)
    except ValueError:
        args = command.split()
    
    for arg in args:
        # Skip if it looks like an option flag
        if arg.startswith("-"):
            continue
        
        # Skip common non-path strings
        if arg in ["&&", "||", ";", "|", "<", ">", ">>", "&"]:
            continue
        
        # Check if it looks like a path
        if arg.startswith("/") or arg.startswith("~") or arg.startswith("./") or arg.startswith("../"):
            paths.append(arg)
        elif "/" in arg and not arg.startswith("http") and not arg.startswith("-"):
            # Might be a relative path
            paths.append(arg)
    
    return paths


def main():
    # Read the JSON data from stdin
    input_data = sys.stdin.read()

    print(f"CURRENT WORKING DIRECTORY: {ALLOWED_PREFIX}", file=sys.stdout)

    # Parse the JSON
    try:
        data = json.loads(input_data)

        if data.get("agent_action_name") == "pre_run_command":
            tool_info = data.get("tool_info", {})
            command = tool_info.get("command_line", "")
            
            # Check for dangerous commands
            dangerous_patterns = ["rm -rf", "sudo rm", "format", "del /f", "mkfs", "dd if="]
            for pattern in dangerous_patterns:
                if pattern in command.lower():
                    print(f"Command blocked: '{pattern}' is not allowed for safety reasons.", file=sys.stderr)
                    sys.exit(2)
            
            # Extract and check paths
            paths = extract_paths_from_command(command)
            
            for path in paths:
                normalized = normalize_path(path)
                allowed, reason = is_path_allowed(normalized)
                
                if not allowed:
                    print(f"Command blocked: '{command}'", file=sys.stderr)
                    print(f"  Path '{path}' -> '{normalized}' is not allowed.", file=sys.stderr)
                    print(f"  Reason: {reason}", file=sys.stderr)
                    sys.exit(2)
                
                # Check for attempts to modify hooks configuration files
                if is_hooks_config_file(normalized) and is_write_operation(command):
                    print(f"Command blocked: '{command}'", file=sys.stderr)
                    print(f"  Writing to hooks configuration file '{path}' is not allowed.", file=sys.stderr)
                    print(f"  This prevents the LLM from modifying its own safety rules.", file=sys.stderr)
                    sys.exit(2)
            
            print(f"Command approved: {command}", file=sys.stdout)

    except json.JSONDecodeError as e:
        print(f"Error parsing JSON: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
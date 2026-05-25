#!/usr/bin/env python3

# Blocks file read/write access outside the workspace and protects hooks configuration files

import sys
import json
import os

# Get the current working directory where Windsurf was launched
ALLOWED_PREFIX = os.getcwd()
print(f"[block_file_access] Current working directory (allowed prefix): {ALLOWED_PREFIX}", file=sys.stderr)


def is_hooks_config_file(path):
    """Check if a path is a hooks configuration file."""
    if not path:
        return False
    
    basename = os.path.basename(path)
    hooks_patterns = ["hooks.json", "hook.json", "hooks.jsonl"]
    
    for pattern in hooks_patterns:
        if pattern in path.lower() or basename.lower().endswith(pattern):
            return True
    
    return False


def main():
    # Read the JSON data from stdin
    input_data = sys.stdin.read()

    # Parse the JSON
    try:
        data = json.loads(input_data)
        
        agent_action_name = data.get("agent_action_name", "")
        
        # Handle both pre_read_code and pre_write_code
        if agent_action_name in ("pre_read_code", "pre_write_code"):
            tool_info = data.get("tool_info", {})
            file_path = tool_info.get("file_path", "")
            
            # Check if file is outside allowed directory
            if not file_path.startswith(ALLOWED_PREFIX):
                action = "reading" if agent_action_name == "pre_read_code" else "writing"
                print(f"Access denied: Cascade is only allowed {action} files under {ALLOWED_PREFIX}", file=sys.stderr)
                sys.exit(2)  # Exit code 2 blocks the action
            
            # Check for attempts to modify hooks configuration files
            if agent_action_name == "pre_write_code" and is_hooks_config_file(file_path):
                print(f"Access denied: Writing to hooks configuration file '{file_path}' is not allowed.", file=sys.stderr)
                print(f"This prevents the LLM from modifying its own safety rules.", file=sys.stderr)
                sys.exit(2)
            
            action_past = "read" if agent_action_name == "pre_read_code" else "written"
            print(f"Access granted: {file_path} can be {action_past}", file=sys.stdout)

    except json.JSONDecodeError as e:
        print(f"Error parsing JSON: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()

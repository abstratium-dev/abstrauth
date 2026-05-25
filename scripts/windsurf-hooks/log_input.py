#!/usr/bin/env python3

import sys
import json

FILE_LOCATION = "/temp/windsurf_input.txt"

def main():
    # Read the JSON data from stdin
    input_data = sys.stdin.read()
    
    # Parse the JSON
    try:
        data = json.loads(input_data)
        
        # Write formatted JSON to file
        with open(FILE_LOCATION, "a") as f:
            f.write('\n' + '='*80 + '\n')
            f.write(json.dumps(data, indent=2, separators=(',', ': ')))
            f.write('\n')
    
        print(json.dumps(data, indent=2))
    except json.JSONDecodeError as e:
        print(f"Error parsing JSON: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
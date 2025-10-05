#!/bin/bash

# The name for the final combined file
output_file="delme-combined-code-only.txt"
# New log file for files that were skipped
skipped_log_file="delme-skipped-files.log"

# Create or clear the output files before starting
> "$output_file"
> "$skipped_log_file"

# --- Argument arrays for the find command ---
declare -a find_cmd
declare -a prune_args
declare -a include_args

# --- Build the Pruning Logic ---

# 1. Start with names of directories to prune wherever they are found.
#    This correctly handles module-level build dirs like 'app/build'.
prune_dir_names=('build' '.gradle' '.idea' '.git')
prune_args+=('(' '-type' 'd' '(' '-name' "${prune_dir_names[0]}")
for ((i=1; i<${#prune_dir_names[@]}; i++)); do
    prune_args+=('-o' '-name' "${prune_dir_names[i]}")
done
prune_args+=(')' ')')

# 2. Add specific files to prune.
prune_file_names=(
    "$output_file"
    "$skipped_log_file"
    'gradlew'
    'gradlew.bat'
    'gradle-wrapper.jar'
)
for name in "${prune_file_names[@]}"; do
    prune_args+=('-o' '-name' "$name")
done

# 3. Read .gitignore and add its patterns to the ignore list.
if [ -f ".gitignore" ]; then
    while IFS= read -r pattern; do
        pattern=$(echo "$pattern" | xargs)
        if [ -z "$pattern" ]; then continue; fi

        if [[ "$pattern" == */ ]]; then
            pattern="${pattern%/}"
            prune_args+=('-o' '-path' "./$pattern/*")
        else
            prune_args+=('-o' '-name' "$pattern")
        fi
    done < <(grep -vE '^\s*#|^\s*$' .gitignore)
fi

# --- Populate Include Arguments ---
include_args=(
    '-name' '*.java'
    '-o' '-name' '*.kt'
    '-o' '-name' '*.xml'
    '-o' '-name' 'AndroidManifest.xml'
    '-o' '-name' '*.gradle'
    '-o' '-name' '*.gradle.kts'
    '-o' '-name' 'gradle.properties'
    '-o' '-name' 'gradle-wrapper.properties'
    '-o' '-name' 'settings.gradle'
    '-o' '-name' '*.pro'
    '-o' '-name' '*.c'
    '-o' '-name' '*.cpp'
    '-o' '-name' '*.h'
    '-o' '-name' '*.glsl'
    '-o' '-name' '*.toml'
    '-o' '-name' '*.js'
)

# --- Execute the find Command ---
# This structure correctly prunes entire directories by name first,
# then proceeds to check the remaining files.
find . \
    '(' "${prune_args[@]}" ')' -prune \
-o \
    -type f '(' \
        '(' "${include_args[@]}" ')' -print0 \
        -o -fprintf "$skipped_log_file" "%p\n" \
    ')' \
| while IFS= read -r -d $'\0' file; do
    
    if [[ "$(file -b --mime-type "$file")" == text/* ]]; then
        echo "==================== FILE: $file ====================" >> "$output_file"
        cat "$file" >> "$output_file"
        echo "" >> "$output_file"
    else
        echo "INFO: Skipped binary file from include list: $file" >> "$skipped_log_file"
    fi
done

# Refined echo message for clarity
echo "All relevant Android project code files have been combined into $output_file"
echo "Files that were not ignored by project rules but didn't match source patterns were logged to $skipped_log_file"

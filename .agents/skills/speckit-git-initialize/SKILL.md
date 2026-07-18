---
name: speckit-git-initialize
description: Initialize a Git repository with an initial commit
compatibility: Requires spec-kit project structure with .specify/ directory
metadata:
  author: github-spec-kit
  source: git:commands/speckit.git.initialize.md
---

# Initialize Git Repository

Initialize a Git repository in the current project directory if one does not already exist.

## Execution

Run the appropriate script from the project root:

- **Bash**: `.specify/extensions/git/scripts/bash/initialize-repo.sh`
- **PowerShell**: `.specify/extensions/git/scripts/powershell/initialize-repo.ps1`

If the extension scripts are not found, fall back to:
- **Bash**: initialize, then `git add -- <reviewed-paths>` and commit
- **PowerShell**: initialize, then `git add -- <reviewed-paths>` and commit

The script handles all checks internally:
- Skips if Git is not available
- Skips if already inside a Git repository
- Inventories the initial files, excludes secrets/generated artifacts, requires
  explicit reviewed pathspecs, then runs `git init`, scoped staging, and commit

## Customization

Replace the script to add project-specific Git initialization steps:
- Custom `.gitignore` templates
- Default branch naming (`git config init.defaultBranch`)
- Git LFS setup
- Git hooks installation
- Commit signing configuration
- Git Flow initialization

## Output

On success:
- `✓ Git repository initialized`

## Graceful Degradation

If Git is not installed:
- Warn the user
- Skip repository initialization
- The project continues to function without Git (specs can still be created under `specs/`)

If Git is installed but initialization, scoped staging, or commit fails:
- Surface the error to the user
- Stop this command rather than continuing with a partially initialized repository

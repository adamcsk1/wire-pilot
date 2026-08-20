# Wire Pilot

OpenCode should use `.opencode/project-instructions.md` and `.opencode/review-instructions.md` as the source of truth for project rules and review rules.

Project-specific OpenCode agents live in `.opencode/agents/`:

- `coder` - general implementation work
- `architect` - planning and Android boundary decisions
- `code-reviewer` - read-only change review
- `testing` - JVM unit tests and JaCoCo coverage
- `commit` - conventional commit preparation

Always read relevant source files before editing. Prefer small, direct changes that match existing patterns.

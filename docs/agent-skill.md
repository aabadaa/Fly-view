# Using Fly-view with a coding agent

[`skills/fly-view/SKILL.md`](../skills/fly-view/SKILL.md) is an [Agent Skill](https://code.claude.com/docs/en/skills) that teaches an agent how to use the **latest** Fly-view (v2): setup, the contribute/start flow, windows, dismissal, popups and the text toolbar, the service, and the mistakes to avoid. It does not cover the removed v1 API.

The agent loads it by itself when the task is about floating windows or overlays on top of other apps.

## Claude Code

For one project (commit it, the whole team gets it):

```bash
mkdir -p .claude/skills/fly-view
curl -L https://raw.githubusercontent.com/aabadaa/Fly-view/main/skills/fly-view/SKILL.md -o .claude/skills/fly-view/SKILL.md
```

For all your projects:

```bash
mkdir -p ~/.claude/skills/fly-view
curl -L https://raw.githubusercontent.com/aabadaa/Fly-view/main/skills/fly-view/SKILL.md -o ~/.claude/skills/fly-view/SKILL.md
```

Restart the session, then ask for a floating window, or run `/fly-view`.

## Other agents

Any agent that reads a skills folder works the same way: put the `fly-view` folder in its skills directory. For agents that only read a rules file (`AGENTS.md`, `.cursorrules`, `GEMINI.md`...), paste the body of `SKILL.md` (everything below the front matter) into it.

## Keeping it current

The skill follows the latest tag. After you update Fly-view, download the file again. Before a release, change `SKILL.md` together with the API (it is checked by hand, like the README).

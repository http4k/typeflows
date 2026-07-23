# Auto Release (auto-release.yml)

```mermaid
%%{init: {"flowchart": {"curve": "basis"}}}%%
flowchart TD
    schedule(["⏰ schedule<br/>0 12 * * *"])
    workflowdispatch(["👤 workflow_dispatch"])
    subgraph autoreleaseyml["Auto Release"]
        autoreleaseyml_metadata[["🔧 Workflow Config<br/>🔐 custom permissions"]]
        autoreleaseyml_autorelease["auto-release<br/>🐧 ubuntu-latest"]
    end
    schedule --> autoreleaseyml_autorelease
    workflowdispatch --> autoreleaseyml_autorelease
```

## Job: auto-release

| Job | OS | Dependencies | Config |
|-----|----|--------------|---------| 
| `auto-release` | 🐧 ubuntu-latest | - | 🔐 perms |

### Steps

```mermaid
%%{init: {"flowchart": {"curve": "basis"}}}%%
flowchart TD
    step1["Step 1: Checkout repository"]
    style step1 fill:#f8f9fa,stroke:#495057
    action1["🎬 actions<br/>checkout<br/><br/>📝 Inputs:<br/>• token: ${{ secrets.TOOLBOX_REPO_TOKEN...<br/>• fetch-depth: 0"]
    style action1 fill:#e1f5fe,stroke:#0277bd
    step1 -.-> action1
    step2["Step 2: Set up JDK"]
    style step2 fill:#f8f9fa,stroke:#495057
    action2["🎬 actions<br/>setup-java<br/><br/>📝 Inputs:<br/>• java-version: 21<br/>• distribution: temurin"]
    style action2 fill:#e1f5fe,stroke:#0277bd
    step2 -.-> action2
    step1 --> step2
    step3["Step 3: Setup Gradle"]
    style step3 fill:#f8f9fa,stroke:#495057
    action3["🎬 gradle<br/>actions/setup-gradle"]
    style action3 fill:#e1f5fe,stroke:#0277bd
    step3 -.-> action3
    step2 --> step3
    step4["Step 4: Update library versions<br/>💻 bash"]
    style step4 fill:#f3e5f5,stroke:#7b1fa2
    step3 --> step4
    step5["Step 5: Update Gradle wrapper<br/>💻 bash"]
    style step5 fill:#f3e5f5,stroke:#7b1fa2
    step4 --> step5
    step6["Step 6: Sync wrapper into project standards resources<br/>💻 bash"]
    style step6 fill:#f3e5f5,stroke:#7b1fa2
    step5 --> step6
    step7["Step 7: Check for changes<br/>💻 bash"]
    style step7 fill:#f3e5f5,stroke:#7b1fa2
    step6 --> step7
    step8["Step 8: Verify build still passes<br/>🔐 if: steps.changes.outputs.has_changes == 'true'<br/>💻 bash"]
    style step8 fill:#f3e5f5,stroke:#7b1fa2
    step7 --> step8
    step9["Step 9: Compute next version<br/>🔐 if: steps.changes.outputs.has_changes == 'true'<br/>💻 bash"]
    style step9 fill:#f3e5f5,stroke:#7b1fa2
    step8 --> step9
    step10["Step 10: Rewrite version references<br/>🔐 if: steps.changes.outputs.has_changes == 'true'<br/>💻 bash"]
    style step10 fill:#f3e5f5,stroke:#7b1fa2
    step9 --> step10
    step11["Step 11: Prepend CHANGELOG entry<br/>🔐 if: steps.changes.outputs.has_changes == 'true'<br/>💻 bash"]
    style step11 fill:#f3e5f5,stroke:#7b1fa2
    step10 --> step11
    step12["Step 12: Commit and push release<br/>🔐 if: steps.changes.outputs.has_changes == 'true'<br/>💻 bash"]
    style step12 fill:#f3e5f5,stroke:#7b1fa2
    step11 --> step12
```

**Step Types Legend:**
- 🔘 **Step Nodes** (Gray): Workflow step execution
- 🔵 **Action Blocks** (Blue): External GitHub Actions
- 🔷 **Action Blocks** (Light Blue): Local repository actions
- 🟣 **Script Nodes** (Purple): Run commands/scripts
- **Solid arrows** (→): Step execution flow
- **Dotted arrows** (-.->): Action usage with inputs
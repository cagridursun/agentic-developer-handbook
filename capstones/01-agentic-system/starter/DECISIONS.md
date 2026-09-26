# Architecture decisions — fill this out BEFORE coding

The point of this capstone is the decision, not the code. Complete the table
and the questions below first; implement second. There is no requirement to
use every capability — the best answer uses the smallest set that solves the
problem.

| Capability | Use? | Why / why not? | Simpler alternative considered |
| --- | --- | --- | --- |
| Model | | | |
| Structured Output | | | |
| Tools | | | |
| Knowledge / RAG | | | |
| Memory | | | |
| Skills | | | |
| Agent Runtime | | | |

MCP is not in the table: it has not been taught yet (Milestone 8), and every
capability in this capstone is local Java.

## Questions to answer before implementing

1. What makes this problem require model reasoning at all?
2. If you use an agent runtime, why is fixed orchestration insufficient?
3. Which capability was hardest to reject?
4. What is the maximum number of agent steps, and why that number?
5. What ends the run?
6. Which data is authoritative (may be stated as fact)?
7. Which statements may only be described as hypotheses?
8. What would change if one tool could modify production?
9. What would you remove first if the system had to become simpler?
10. What decisions may the model influence?
11. What decisions must remain deterministic or application-controlled?

Questions 10 and 11 are the [LLM vs Decision Authority](../../../docs/model-vs-decision-authority.md) boundary applied to your design: model influence is not decision authority.

# Glossary

Short working definitions for this handbook. Where two terms are often mixed up, the comparison follows the definitions.

## Definitions

**LLM.** A model trained to continue text. In this handbook, an LLM call means one inference request and its response.

**Model.** The component that performs inference. It does not choose tools, store memory, or decide when the application should stop.

**Foundation model.** A model trained broadly enough to be used for many tasks. A hosted chat model is usually a foundation model, sometimes with extra training or a vendor-specific system prompt.

**Inference.** Running a trained model to produce an output. Training changes weights. Inference uses them.

**Inference provider.** The service or process that hosts a model and accepts API calls. A provider is not an agent. The same application can target more than one provider later.

**Prompt.** The input for one inference call: instructions, conversation, and any context the application attached.

**Context.** Everything the model can see for that one call. The context window is finite. Information left outside it is invisible unless the application retrieves it and places it in the prompt.

**Structured output.** A response constrained to a schema, such as JSON that maps to a Java type. The application validates the response. A request to "reply in JSON" without validation is not structured output.

**Tool.** A capability the application can execute, such as a database query or an HTTP call. The tool is ordinary code. The model does not run it.

**Tool calling.** The exchange in which the model returns a structured request to use a tool, the application executes it, and the result is sent back to the model.

**Agent.** An application that uses a model inside a loop: observe a goal, take a step, look at the result, and stop under an explicit condition. One LLM request is not an agent.

**Agent runtime.** The code that owns that loop, the tools it may use, the limits, and the stop condition.

**Agent loop.** One cycle of model call, optional tool execution, and feeding the result back, repeated until the runtime stops.

**Knowledge.** Information the application can look up and place into context. It lives outside the model weights.

**RAG.** Retrieval-augmented generation. The application searches a knowledge source, adds the selected passages to the prompt, then calls the model. RAG does not modify the model.

**Embedding.** A vector that represents a piece of text so that similar texts can be compared. Embeddings are one retrieval technique. They are not required for every lookup.

**Vector database.** A store built for similarity search over embeddings. Use it for that search problem. A lookup by id, key, or exact filter belongs in an ordinary database or API.

**Memory.** State the application deliberately retains so a later turn can use it — selected, scoped, updatable, and forgettable. Carrying raw session history forward is a crude workaround, not memory design. Memory is not the context window, and it is not the knowledge corpus.

**Skill.** Instructions for how the agent should perform a kind of task. A skill changes procedure. It does not by itself add a new tool or a new fact. In this handbook, skills are one building block, not the product. See [ADR 0004](adr/0004-skills-are-a-building-block.md).

**MCP.** Model Context Protocol. A standard way for an application to use tools and resources exposed by an external server.

**A2A.** Agent-to-agent protocol. A way for independent agents to communicate with each other.

**Multi-agent system.** Several agents, each with its own instructions and usually its own loop, working on related parts of a task.

**Orchestration.** Application code that sequences steps. A fixed pipeline is orchestration. An agent loop is orchestration in which the model influences the next step.

**Fine-tuning.** Further training that changes model weights. It teaches a relatively stable behavior. It is a poor place to store facts that change every day.

**Evaluation.** A check that the system does what was intended: tests, fixed examples, and human review. A single impressive demo is not an evaluation.

**Observability.** Evidence from a running system: logs, metrics, and traces for model calls, tool calls, latency, and failures.

**Guardrail.** A check the application enforces on input or output. Schema validation, allow-lists, and human approval are guardrails. An instruction in the prompt is not a guardrail, and it is not authorization.

## Model vs agent

A model generates a response for one input. An agent is the application around a model: a runtime, a loop, and a stop condition. If the program cannot take a second step based on the first result, it is not an agent yet.

## Tool vs skill

A tool is what the agent can do: `getOrder`, `sendEmail`, `queryAccount`. A skill is how the agent should approach a task: which steps to follow, which checks to make, which tool to prefer. Adding a skill does not create a new capability. Adding a tool does not tell the agent how to do the whole job.

## Knowledge vs memory

Knowledge is information the application looks up, such as documentation or records that exist whether or not this conversation happened. Memory is information the application keeps because this user, session, or task produced it. A help article is knowledge. "The customer already gave the order id" is memory.

## MCP vs A2A

MCP is how one agent reaches tools and data outside its process. A2A is how one agent talks to another agent. Use MCP when the missing piece is a capability. Use A2A when the missing piece is another agent with its own runtime. A method call inside the same application is neither.

## RAG vs fine-tuning

RAG puts current information into the prompt at request time. Fine-tuning changes the model so that a behavior is more likely even without that information in the prompt. Choose RAG when the facts change or must be cited. Choose fine-tuning only when you need a stable change in behavior and you can pay the training and evaluation cost. Do not fine-tune a model to memorize this week's catalog.

The relationships between these pieces are drawn in [mental-model.md](mental-model.md).

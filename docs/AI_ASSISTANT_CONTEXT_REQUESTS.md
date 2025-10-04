# AI Assistant Context Requests - Understanding Multiple API Calls

## 🎯 Overview

When using JetBrains AI Assistant with the OpenRouter plugin, you may notice **significantly more API calls** in your OpenRouter analytics than you expect. This is **normal behavior** and is caused by AI Assistant's internal context management system.

This document explains why a single user question can result in **8-15+ API calls** to OpenRouter.

---

## 📊 What You'll See in OpenRouter Analytics

### Example Scenario:

**User Action:** Types "Hello" in AI Assistant chat

**Expected API Calls:** 1 request

**Actual API Calls:** 10-15 requests

**Why?** AI Assistant makes multiple internal "meta" requests to determine context relevance, generate search queries, and process your codebase before answering.

---

## 🔍 Types of AI Assistant Internal Requests

### 1. **Context Relevance Checking**

AI Assistant sends requests to determine if specific context is relevant to your question.

#### Example Request:
```json
{
  "model": "x-ai/grok-4-fast:free",
  "messages": [
    {
      "role": "user",
      "content": "Determine if the following context is required to solve the task in the user's input in the chat session: \"Hello\"\nContext:\nProject View: \n```\nProject View content:\n```\nopenrouter-intellij-plugin\n .github\n .gradle\n .idea\n build\n config\n docs\n  images\n  AI_ASSISTANT_SETUP.md\n  AI_ASSISTANT_TECHNICAL_GUIDE.md\n  DUPLICATE_REQUEST_ANALYSIS.md\n  MODEL_UNAVAILABILITY_HANDLING.md\n  PRODUCTION_LOGGING.md\n  TEST_RESULTS_DUPLICATE_ANALYSIS.md\n gradle\n..."
    }
  ]
}
```

**Purpose:** AI Assistant asks the model: "Is the project structure relevant to answering 'Hello'?"

**Result:** The model responds with yes/no, and AI Assistant decides whether to include this context in the final request.

---

### 2. **File Content Relevance Checking**

AI Assistant checks if specific files are relevant to your question.

#### Example Request:
```json
{
  "model": "x-ai/grok-4-fast:free",
  "messages": [
    {
      "role": "user",
      "content": "Determine if the following context is required to solve the task in the user's input in the chat session: \"Hello\"\nContext:\nLICENSE: \n```\nMIT License\n\nCopyright (c) 2025 Dmitry Zhavoronkov\n\nPermission is hereby granted, free of charge, to any person obtaining a copy\nof this software and associated documentation files (the \"Software\"), to deal\nin the Software without restriction, including without limitation the rights\nto use, copy, modify, merge, publish, distribute, sublicense, and/or sell\ncopies of the Software..."
    }
  ]
}
```

**Purpose:** AI Assistant asks: "Is the LICENSE file relevant to answering 'Hello'?"

**Result:** Usually "no" for simple greetings, but "yes" for questions about licensing.

---

### 3. **Sub-Request Generation for Code Search**

AI Assistant generates search queries to find relevant code in your project.

#### Example Request:
```json
{
  "model": "x-ai/grok-4-fast:free",
  "messages": [
    {
      "role": "user",
      "content": "You are given a user's request to JetBrains intellij AI Assistant. \nThis request is typically about code.\nYou need to create sub-requests, helping to resolve user's original request.\nThese requests will be processed to retrieval system -- it searches for method and classes, only by their names.\nPlace most important subrequest first.\n\nFor example:\n\nUser request:\n\nI want to implement a console inlay button that shows the current time\n\nYour response:\n\n1. InlayButton\n2. ConsoleView\n3. getCurrentTime\n4. InlayPresentation\n\nNow process this request:\n\nHow do I add a new model to the plugin?"
    }
  ]
}
```

**Purpose:** AI Assistant asks the model to generate search terms for finding relevant code.

**Result:** The model returns class/method names to search for (e.g., "ModelProvider", "addModel", "ModelConfiguration").

---

### 4. **Documentation Relevance Checking**

AI Assistant checks if documentation files are relevant.

#### Example Request:
```json
{
  "model": "x-ai/grok-4-fast:free",
  "messages": [
    {
      "role": "user",
      "content": "Determine if the following context is required to solve the task in the user's input in the chat session: \"How do I configure the plugin?\"\nContext:\nREADME.md: \n```\n# OpenRouter IntelliJ Plugin\n\nConnect IntelliJ IDEA to 400+ AI models through OpenRouter.ai\n\n## Features\n\n- Status bar widget with real-time quota information\n- API key management\n- Favorite models\n- AI Assistant integration\n..."
    }
  ]
}
```

**Purpose:** AI Assistant asks: "Is the README relevant to answering 'How do I configure the plugin?'"

**Result:** Usually "yes" for configuration questions.

---

## 📈 Real-World Example: Single User Question

### User Question: "Hello"

**AI Assistant's Internal Workflow:**

1. **Request 1:** Check if Project View is relevant → **No**
2. **Request 2:** Check if LICENSE is relevant → **No**
3. **Request 3:** Check if README is relevant → **No**
4. **Request 4:** Check if build.gradle is relevant → **No**
5. **Request 5:** Check if plugin.xml is relevant → **No**
6. **Request 6:** Check if recent files are relevant → **No**
7. **Request 7:** Check if open files are relevant → **No**
8. **Request 8:** Generate sub-requests for code search → **Empty (no code needed)**
9. **Request 9:** Check if user's previous messages are relevant → **Yes**
10. **Request 10:** **FINAL REQUEST** - Answer "Hello" with minimal context

**Total API Calls:** 10 requests for a simple "Hello"

**Cost Impact:** If each request costs \$0.0001, this single "Hello" costs \$0.001

---

## 📊 Typical Request Counts by Question Type

| User Question Type | Typical API Calls | Reason |
|-------------------|------------------|---------|
| **Simple greeting** ("Hello", "Hi") | 8-12 requests | Context checking (all return "no") |
| **General question** ("How are you?") | 10-15 requests | Context + history checking |
| **Code question** ("How do I add a feature?") | 15-25 requests | Context + sub-request generation + code search |
| **Specific code question** ("Fix this bug in MyClass") | 20-30 requests | Full context analysis + multiple file checks |
| **Complex refactoring** ("Refactor this module") | 30-50+ requests | Extensive context + multiple code searches |

---

## 💰 Cost Implications

### Example Cost Calculation:

**Scenario:** User asks 10 questions during a coding session

**Average API calls per question:** 20 requests

**Total API calls:** 200 requests

**Model:** `openai/gpt-4o-mini` (\$0.00015 per 1K input tokens, \$0.0006 per 1K output tokens)

**Average tokens per context request:**
- Input: ~500 tokens (context checking)
- Output: ~10 tokens (yes/no answer)

**Cost per context request:** ~\$0.00008

**Cost per user question:** 20 × \$0.00008 = **\$0.0016**

**Total session cost:** 10 × \$0.0016 = **\$0.016** (~1.6 cents)

**Monthly cost (20 sessions):** \$0.016 × 20 = **\$0.32**

---

## 🎯 Why AI Assistant Does This

### Benefits of Context Checking:

1. **Relevance:** Only includes context that's actually relevant to your question
2. **Token Efficiency:** Avoids sending unnecessary context in the final request
3. **Accuracy:** Better answers by focusing on relevant information
4. **Cost Optimization:** Final request is smaller and more focused

### The Trade-off:

- **More API calls** (10-30 per question)
- **Lower cost per call** (context checks are small)
- **Better final answer** (only relevant context included)

**Net Result:** Usually **cheaper and better** than sending all context in one huge request.

---

## 📝 What You'll See in Logs

### Plugin Logs (DEBUG Mode):

```
[OpenRouter] [Chat-000001] NEW CHAT COMPLETION REQUEST RECEIVED
[OpenRouter][DEBUG] [Chat-000001] Streaming request body: {"model":"x-ai/grok-4-fast:free","messages":[{"role":"user","content":"Determine if the following context is required..."}]}

[OpenRouter] [Chat-000002] NEW CHAT COMPLETION REQUEST RECEIVED
[OpenRouter][DEBUG] [Chat-000002] Streaming request body: {"model":"x-ai/grok-4-fast:free","messages":[{"role":"user","content":"Determine if the following context is required..."}]}

[OpenRouter] [Chat-000003] NEW CHAT COMPLETION REQUEST RECEIVED
[OpenRouter][DEBUG] [Chat-000003] Streaming request body: {"model":"x-ai/grok-4-fast:free","messages":[{"role":"user","content":"You are given a user's request to JetBrains intellij AI Assistant..."}]}
```

**What This Means:**
- Each `[Chat-XXXXXX]` is a separate API call
- Most are context relevance checks
- Some are sub-request generation
- Only the last one is your actual question

---

## 🔍 How to Identify Context Requests

### Context Relevance Requests:

**Pattern:** `"Determine if the following context is required to solve the task..."`

**Characteristics:**
- Short responses (usually "yes" or "no")
- Include project files, documentation, or code snippets
- Happen before the final answer

### Sub-Request Generation:

**Pattern:** `"You are given a user's request to JetBrains intellij AI Assistant..."`

**Characteristics:**
- Asks for search terms or class/method names
- Responses are lists of keywords
- Used for code retrieval

### Final Answer Request:

**Pattern:** Your actual question with selected context

**Characteristics:**
- Longest request (includes all relevant context)
- Happens last in the sequence
- Produces the actual answer you see

---

## 📊 OpenRouter Analytics Interpretation

### What You'll See:

**Dashboard View:**
```
Date: 2025-10-04
Total Requests: 247
Total Cost: $0.42

Top Models:
- x-ai/grok-4-fast:free: 247 requests
```

**Breakdown:**
- **User questions asked:** ~12
- **Average requests per question:** ~20
- **Context checks:** ~180 requests (73%)
- **Sub-request generation:** ~55 requests (22%)
- **Final answers:** ~12 requests (5%)

---

## ⚙️ Reducing API Call Volume

### Option 1: Use Simpler Models for Context Checking

AI Assistant uses the **same model** for context checking and final answers. You can't change this directly, but you can:

1. **Choose faster, cheaper models** (e.g., `openai/gpt-4o-mini` instead of `openai/gpt-4o`)
2. **Avoid expensive models** for casual chat (save them for complex coding tasks)

### Option 2: Be Specific in Your Questions

**Instead of:** "How do I do this?"

**Try:** "How do I add a new model to ModelsServlet.kt?"

**Why:** Specific questions require fewer context checks because AI Assistant knows exactly what to look for.

### Option 3: Disable AI Assistant Features

In AI Assistant settings, you can disable:
- **Project-wide context** (reduces file checking)
- **Documentation search** (reduces doc checking)
- **Code search** (reduces sub-request generation)

**Trade-off:** Less accurate answers, but fewer API calls.

---

## 🚨 When Model Is Unavailable

### What Happens:

If your selected model (e.g., `x-ai/grok-4-fast:free`) is unavailable:

1. **All context requests fail** (404 errors)
2. **AI Assistant can't determine relevance**
3. **AI Assistant can't generate search queries**
4. **Final answer request never happens**
5. **User sees error message**

### Example Log:
```
[OpenRouter] [Chat-000001] ❌ OpenRouter API Error: 404
[OpenRouter][DEBUG] Skipping duplicate notification for model: x-ai/grok-4-fast:free

[OpenRouter] [Chat-000002] ❌ OpenRouter API Error: 404
[OpenRouter][DEBUG] Skipping duplicate notification for model: x-ai/grok-4-fast:free

[OpenRouter] [Chat-000003] ❌ OpenRouter API Error: 404
[OpenRouter][DEBUG] Skipping duplicate notification for model: x-ai/grok-4-fast:free
```

**Solution:** Switch to an available model.

---

## 📚 Related Documentation

- **[AI Assistant Setup](AI_ASSISTANT_SETUP.md)** - How to configure AI Assistant with OpenRouter
- **[Production Logging](PRODUCTION_LOGGING.md)** - Understanding plugin logs

---

## ✅ Summary

### Key Takeaways:

1. **Multiple API calls are normal** - AI Assistant makes 10-30 requests per user question
2. **Most requests are context checks** - "Is this file/doc relevant?"
3. **This is actually efficient** - Smaller, focused final request saves tokens
4. **Cost is still low** - Context checks are cheap (~\$0.0001 each)
5. **You can't disable this** - It's how AI Assistant works internally

### Understanding Your OpenRouter Bill:

- **High request count** ✅ Normal (context checking)
- **Low cost per request** ✅ Expected (small requests)
- **Total cost reasonable** ✅ Usually cheaper than alternatives

### When to Worry:

- ❌ **Thousands of requests** for a single question (possible bug)
- ❌ **All requests failing** (model unavailable - switch models)
- ❌ **Unexpectedly high costs** (check model pricing)

---

## 🎯 Final Thoughts

The multiple API calls you see in OpenRouter analytics are **not a bug** - they're a feature of how JetBrains AI Assistant intelligently manages context. While it may seem wasteful at first, this approach actually:

- ✅ Provides more accurate answers
- ✅ Reduces token usage in final requests
- ✅ Keeps costs reasonable
- ✅ Improves response quality

**Bottom line:** Don't be alarmed by high request counts in OpenRouter analytics. Focus on the **total cost** and **answer quality** instead.


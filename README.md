# History Trivia Bot 🏛️

A command-line trivia chatbot built in **Scala** with a focus on functional programming principles — immutable state, pure functions, higher-order functions, currying, closures, and lazy evaluation.

Built as a university project for **C-CS219 Functional Programming** at **Egypt University of Informatics**.

---

## Features

- **20 trivia questions** spanning Ancient, Medieval, Early Modern, and Modern history across Africa, Europe, Asia, the Americas, and more
- **Smart recommendation engine** that learns your weak topics and eras and prioritises them
- **3-tier hint system** with point deductions per hint used
- **Streak detection** and live mood tracking based on your conversation history
- **Session summary** showing questions asked, answers given, hints used, and detected topics
- **Full final statistics** on exit including score, accuracy, and weak areas

---

## Functional Programming Concepts Used

| Concept | Where |
|---|---|
| Immutable state (`case class` + `.copy()`) | `ConversationState` passed through every function |
| Pure functions | `greetUser`, `parseInput`, `detectIntent`, `buildQuestionText` |
| Higher-order functions (`map`, `filter`, `flatMap`, `foldLeft`, `exists`) | `MemoryTracker`, `RecommendationEngine`, `ChatEngine` |
| Currying | `filterQuestions(getValue)(target)(questions)` in `ChatEngine` and `QuestionBank` |
| Closures | `difficultyFilter` in `QuestionBank` captures `difficulty` from outer scope |
| Lazy evaluation (`lazy val`) | `easyTriviaQuestions`, `hardTriviaQuestions`, `questionsByEra`, `questionsByTopic` in `QuestionBank` |
| Pattern matching | Intent routing, hint counting, score evaluation throughout |
| `Option` / `Some` / `None` | `currentQuestion`, `recommendedQuestion`, `headOption` calls |
| Tail recursion (`@tailrec`) | Main game loop in `Main.scala` |
| Algebraic data types (`sealed trait`) | `Intent` hierarchy in `Models.scala` |

---

## Project Structure

```
src/
├── Models.scala              # All shared data types (Intent, TriviaQuestion, UserProfile, ConversationState)
├── QuestionBank.scala        # 20 trivia questions + lazy vals + curried filters + closures
├── ChatEngine.scala          # Input parsing, intent detection, all command handlers
├── MemoryTracker.scala       # Conversation history, topic extraction, mood detection, summaries
├── RecommendationEngine.scala# Scoring and ranking unanswered questions by user profile
└── Main.scala                # Entry point, tail-recursive game loop, final statistics
```

---

## How to Run

**Requirements:** Scala 2.11+ and Java 8+

```bash
# Compile
scalac src/*.scala

# Run
scala Main
```

---

## Sample Session

```
========================================
   Welcome to the History Trivia Bot!
========================================

Your name > Eyad

Welcome, Eyad! Let's see how well you know your history!
Type 'start' to get your first question, or 'recommend' to get a suggested one.

You > start

Bot >
--- Question (EASY) ---
Era: ancient | Region: africa | Topic: wonders

Which ancient wonder was located in Alexandria, Egypt?

(Type your answer, or 'hint' for a clue)

You > hint
Bot > Hint 1: It helped ships navigate at night (-5 points if correct)

You > lighthouse
Bot > Correct! Well done, Eyad!
+10 points | Total: 10
Type 'start' for the next question or 'recommend' for a suggestion!

You > score
Bot > Score: 10 pts | Answered: 1 | Correct: 1 | Accuracy: 100% | You're a history genius!

You > quit
Bot > Thanks for playing, Eyad! Final score: 10. Goodbye!
```

---

## Commands

| Command | Action |
|---|---|
| `start` | Get the next question (uses recommendation engine) |
| `recommend` | Get a personalised question suggestion based on your weak areas |
| `hint` | Reveal a hint for the current question (-5 points per hint used) |
| `score` | See your current score, accuracy, and performance message |
| `summary` | Full conversation summary: intents used, topics detected |
| `quit` / `exit` | End the session and display final statistics |

---

## Scoring

| Difficulty | Base Points | With 1 Hint | With 2 Hints | With 3 Hints |
|---|---|---|---|---|
| Easy | 10 | 5 | 1 (min) | 1 (min) |
| Medium | 20 | 15 | 10 | 5 |
| Hard | 30 | 25 | 20 | 15 |

Minimum points per correct answer is always **1**, regardless of hints used.

---

## Authors

Developed by **Eyad** and team — Egypt University of Informatics, Year 2.

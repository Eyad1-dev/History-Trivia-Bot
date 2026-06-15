// ============================================================
//  ChatEngine.scala
//  The brain of the History Trivia Bot.
//  Parses user input, routes to the correct handler, and
//  returns (botResponse, newState) without mutating anything.
// ============================================================

object ChatEngine {

  // greetUser is a pure function: no inputs, always the same output,
  // no side effects. stripMargin removes the leading | from each line.
  def greetUser(): String =
    """
    |========================================
    |   Welcome to the History Trivia Bot!
    |========================================
    | Test your knowledge across:
    |   Ancient, Medieval and Modern History
    |   Regions: Africa, Europe, Asia and more
    |   Difficulties: Easy, Medium, Hard
    |
    | Commands:
    |   start     - get a new question
    |   hint      - get a hint (costs 5 points per hint)
    |   score     - see your current score
    |   summary   - see the conversation summary
    |   recommend - get a personalised question suggestion
    |   quit      - exit the bot
    |
    | What is your name?
    |========================================
    """.stripMargin


  // parseInput normalises raw user text into a list of lowercase tokens.
  // toLowerCase + trim make matching case-insensitive and whitespace-safe.
  // split("\\s+") handles multiple spaces between words.
  def parseInput(input: String): List[String] =
    input.toLowerCase.trim.split("\\s+").toList


  // filterQuestions is a curried function: it takes arguments in three
  // separate lists, enabling partial application.
  //
  //   filterQuestions(_.era)           -> needs target + list
  //   filterQuestions(_.era)("ancient")-> needs list only
  //
  // This is currying in action: one generic function becomes three
  // specialised helpers (filterByEra, filterByDifficulty, filterByRegion).
  def filterQuestions(
    getValue  : TriviaQuestion => String
  )(
    target    : String
  )(
    questions : List[TriviaQuestion]
  ): List[TriviaQuestion] =
    questions.filter(q => getValue(q) == target)

  val filterByEra       : String => List[TriviaQuestion] => List[TriviaQuestion] = filterQuestions(_.era)
  val filterByDifficulty: String => List[TriviaQuestion] => List[TriviaQuestion] = filterQuestions(_.difficulty)
  val filterByRegion    : String => List[TriviaQuestion] => List[TriviaQuestion] = filterQuestions(_.region)


  // detectIntent maps a list of tokens to a user Intent.
  // Pattern matching with guards (if conditions) is used instead of
  // a chain of if/else, which keeps each case readable and exhaustive.
  // Ordering matters: hint is checked before greeting to avoid misrouting
  // inputs like "I need a hint please".
  def detectIntent(tokens: List[String]): Intent = {
    val joined = tokens.mkString(" ")
    joined match {
      case s if s.contains("hint")                           => GetHint
      case s if s.contains("score")                          => GetScore
      case s if s.contains("summary")                        => GetSummary
      case s if s.contains("quit") || s.contains("exit")    => Quit
      case s if s.contains("recommend") || s.contains("suggest") => Recommend
      case s if s.contains("start") || s.contains("question") || s.contains("play") => AskQuestion
      case s if s.contains("hello") || s.contains("hey") || s.contains("hi") => Greeting
      case _                                                 => AnswerQuestion
    }
  }


  // handleUserInput is the top-level router.
  // It parses the input, detects the intent, and dispatches to the
  // matching private handler. Every handler returns (response, newState).
  def handleUserInput(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    val tokens = parseInput(input)
    val intent = detectIntent(tokens)

    intent match {
      case Greeting       => handleGreeting(input, state)
      case AskQuestion    => handleAskQuestion(input, state)
      case GetHint        => handleGetHint(input, state)
      case GetScore       => handleGetScore(input, state)
      case GetSummary     => handleGetSummary(input, state)
      case Recommend      => handleRecommend(input, state)
      case Quit           => handleQuit(input, state)
      case AnswerQuestion => handleAnswer(input, state)
      case Unknown        => handleUnknown(input, state)
    }
  }


  // generateResponse builds simple responses for intents that do not
  // need complex logic. More complex intents have their own handlers.
  def generateResponse(intent: Intent, state: ConversationState): String =
    intent match {
      case Greeting =>
        s"Hello ${state.user.name}! Ready to test your history knowledge?"
      case GetScore =>
        s"Score: ${state.user.score} pts | " +
        s"Answered: ${state.user.totalAnswered} | " +
        s"Correct: ${state.user.correctAnswered}"
      case Quit =>
        s"Thanks for playing, ${state.user.name}! " +
        s"Final score: ${state.user.score}. Goodbye!"
      case _ =>
        "Let's keep going!"
    }


  // ── Private handlers ────────────────────────────────────────
  // Each handler:
  //   1. Builds the bot response string
  //   2. Logs the interaction (with the REAL user input text)
  //   3. Returns (response, newState)
  //
  // FIX: All handlers now receive the raw `input` string and pass it
  // to logInteraction so MemoryTracker always stores what the user
  // actually typed, enabling accurate mood detection and topic tracking.

  private def handleGreeting(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    // FIX: if a question is currently active, acknowledge the greeting
    // but preserve the question so the player does not lose their turn.
    val response = state.currentQuestion match {
      case Some(q) =>
        s"Hey ${state.user.name}! Don't forget you still have an active question:\n" +
        buildQuestionText(q)
      case None =>
        generateResponse(Greeting, state)
    }
    val newState = MemoryTracker.logInteraction(input, response, Greeting, state)
    (response, newState)
  }


  private def handleAskQuestion(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {

    // FIX: if the player just used 'recommend', serve that exact question
    // so 'recommend' followed by 'start' is consistent.
    val questionPool = state.recommendedQuestion match {
      case Some(q) if !state.user.answeredIds.contains(q.id) => List(q)
      case _ =>
        // filter out already-answered questions, then rank the rest
        val unanswered = QuestionBank.TriviaQuestions.filter(q =>
          !state.user.answeredIds.contains(q.id)
        )
        RecommendationEngine.recommend(state.preferences, unanswered)
    }

    questionPool.headOption match {
      case None =>
        val response = "You've answered all questions! Type 'score' to see your final score."
        val newState = MemoryTracker.logInteraction(input, response, AskQuestion, state)
        (response, newState)

      case Some(q) =>
        val encouragement =
          if (state.user.weakEras.contains(q.era))
            s"\n(Psst! You struggled with the ${q.era} era before - great chance to improve!)"
          else if (state.user.weakTopics.contains(q.topic))
            s"\n(Psst! You struggled with ${q.topic} before - this is your chance!)"
          else if (state.user.correctAnswered > 0 && state.user.correctAnswered % 5 == 0)
            s"\n(You've answered ${state.user.correctAnswered} correctly so far - impressive!)"
          else ""

        val response = buildQuestionText(q) + encouragement

        // Store current question and clear the recommendation
        val updatedState = state.copy(
          currentQuestion     = Some(q),
          recommendedQuestion = None
        )
        val newState = MemoryTracker.logInteraction(input, response, AskQuestion, updatedState)
        (response, newState)
    }
  }


  private def handleAnswer(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    state.currentQuestion match {
      case None =>
        val response = "No active question! Type 'start' to get one."
        val newState = MemoryTracker.logInteraction(input, response, AnswerQuestion, state)
        (response, newState)

      case Some(q) =>
        // Normalise both strings before comparison so spacing differences do not matter
        val cleaned   = input.toLowerCase.trim.replace(" ", "")
        val expected  = q.answer.toLowerCase.replace(" ", "")
        val isCorrect = cleaned.contains(expected)

        if (isCorrect) {
          val hintsUsedForQ = state.user.hintsUsed.getOrElse(q.id, 0)
          val basePoints    = pointsForDifficulty(q.difficulty)
          val pointsEarned  = basePoints - (hintsUsedForQ * 5)
          val finalPoints   = math.max(pointsEarned, 1)  // never award 0 or negative

          val streakMsg =
            if ((state.user.correctAnswered + 1) % 3 == 0)
              s"\nYou're on a roll! ${state.user.correctAnswered + 1} correct answers, keep it up!"
            else ""

          val response =
            s"Correct! Well done, ${state.user.name}!\n" +
            s"+$finalPoints points | Total: ${state.user.score + finalPoints}$streakMsg\n" +
            s"Type 'start' for the next question or 'recommend' for a suggestion!"

          val newUser = state.user.copy(
            score           = state.user.score + finalPoints,
            totalAnswered   = state.user.totalAnswered + 1,
            correctAnswered = state.user.correctAnswered + 1,
            answeredIds     = q.id :: state.user.answeredIds
          )
          val newPrefs = RecommendationEngine.updatePreferences(
            "lastCorrectEra", q.era, state.preferences
          )
          val newState = MemoryTracker.logInteraction(
            input, response, AnswerQuestion,
            state.copy(user = newUser, preferences = newPrefs, currentQuestion = None)
          )
          (response, newState)

        } else {
          // FIX: weakNote now always refers to the CURRENT question's topic,
          // not state.user.weakTopics.head (which was a different, older topic).
          val weakNote =
            s"\nTip: brush up on ${q.topic} from the ${q.era} era to improve your score!"

          val response =
            s"Not quite, ${state.user.name}. The answer was: ${q.answer}\n" +
            s"Topic: ${q.topic} | Era: ${q.era}" + weakNote +
            s"\nDon't give up! Type 'start' for the next question or 'hint' before answering next time!"

          // Add this topic/era to weak areas if not already tracked
          val newWeakTopics =
            if (!state.user.weakTopics.contains(q.topic)) q.topic :: state.user.weakTopics
            else state.user.weakTopics

          val newWeakEras =
            if (!state.user.weakEras.contains(q.era)) q.era :: state.user.weakEras
            else state.user.weakEras

          val newUser = state.user.copy(
            totalAnswered = state.user.totalAnswered + 1,
            weakTopics    = newWeakTopics,
            weakEras      = newWeakEras,
            answeredIds   = q.id :: state.user.answeredIds
          )
          val newState = MemoryTracker.logInteraction(
            input, response, AnswerQuestion,
            state.copy(user = newUser, currentQuestion = None)
          )
          (response, newState)
        }
    }
  }


  private def handleGetHint(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    state.currentQuestion match {
      case None =>
        val response = "No active question! Type 'start' first."
        val newState = MemoryTracker.logInteraction(input, response, GetHint, state)
        (response, newState)

      case Some(q) =>
        val hintsUsedForQ = state.user.hintsUsed.getOrElse(q.id, 0)

        // Pattern match on hint count to select the right hint text
        // and update the map with the new count.
        val (hintText, newHintsMap) = hintsUsedForQ match {
          case 0 => (s"Hint 1: ${q.hint1} (-5 points if correct)",            state.user.hintsUsed + (q.id -> 1))
          case 1 => (s"Hint 2: ${q.hint2} (-5 more points if correct)",       state.user.hintsUsed + (q.id -> 2))
          case 2 => (s"Hint 3: ${q.hint3} (last hint! minimum points remain)",state.user.hintsUsed + (q.id -> 3))
          case _ => ("No more hints available for this question!",             state.user.hintsUsed)
        }

        val newUser  = state.user.copy(hintsUsed = newHintsMap)
        val newState = MemoryTracker.logInteraction(
          input, hintText, GetHint,
          state.copy(user = newUser)
        )
        (hintText, newState)
    }
  }


  private def handleGetScore(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    val accuracy = if (state.user.totalAnswered == 0) 0
      else (state.user.correctAnswered * 100) / state.user.totalAnswered

    val performanceMsg = accuracy match {
      case a if a >= 80 => "You're a history genius!"
      case a if a >= 50 => "Not bad, keep pushing!"
      case _            => "Keep practicing, you'll get there!"
    }

    val response =
      s"Score: ${state.user.score} pts | " +
      s"Answered: ${state.user.totalAnswered} | " +
      s"Correct: ${state.user.correctAnswered} | " +
      s"Accuracy: $accuracy% | $performanceMsg"

    val newState = MemoryTracker.logInteraction(input, response, GetScore, state)
    (response, newState)
  }


  private def handleGetSummary(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    val weakNote =
      if (state.user.weakTopics.nonEmpty)
        s"\nFocus on these topics: ${state.user.weakTopics.mkString(", ")}"
      else
        "\nNo weak areas yet - great job!"

    val response = MemoryTracker.summarizeConversation(state.history) + weakNote
    val newState = MemoryTracker.logInteraction(input, response, GetSummary, state)
    (response, newState)
  }


  private def handleRecommend(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    val unanswered  = QuestionBank.TriviaQuestions.filter(q => !state.user.answeredIds.contains(q.id))
    val recommended = RecommendationEngine.recommend(state.preferences, unanswered)

    recommended.headOption match {
      case None =>
        val response = s"Wow ${state.user.name}, you've answered everything! You're a history master!"
        val newState = MemoryTracker.logInteraction(input, response, Recommend, state)
        (response, newState)

      case Some(q) =>
        val reason =
          if (state.user.weakTopics.contains(q.topic))
            s"because you struggled with ${q.topic} before"
          else if (state.user.weakEras.contains(q.era))
            s"because the ${q.era} era is one of your weak spots"
          else
            "based on your history so far"

        val response =
          s"I recommend this question $reason:\n" +
          RecommendationEngine.explainRecommendation(q)

        // FIX: store the recommended question so handleAskQuestion serves
        // exactly this question when the player types 'start' next.
        val updatedState = state.copy(recommendedQuestion = Some(q))
        val newState     = MemoryTracker.logInteraction(input, response, Recommend, updatedState)
        (response, newState)
    }
  }


  private def handleQuit(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    val response = generateResponse(Quit, state)
    val newState = MemoryTracker.logInteraction(
      input, response, Quit,
      state.copy(isRunning = false)
    )
    (response, newState)
  }


  private def handleUnknown(
    input : String,
    state : ConversationState
  ): (String, ConversationState) = {
    val response = state.currentQuestion match {
      case Some(_) =>
        "Hmm, I didn't catch that. Was that your answer? Try rephrasing! " +
        "Or type 'hint' for a clue, or 'start' to skip."
      case None =>
        s"I didn't get that, ${state.user.name}. " +
        "Type 'start' to get a question, 'score' to check your points, or 'quit' to exit."
    }
    val newState = MemoryTracker.logInteraction(input, response, Unknown, state)
    (response, newState)
  }


  // ── Private helpers ──────────────────────────────────────────

  // buildQuestionText formats a question for display.
  private def buildQuestionText(q: TriviaQuestion): String =
    s"""
    |--- Question (${q.difficulty.toUpperCase}) ---
    |Era: ${q.era} | Region: ${q.region} | Topic: ${q.topic}
    |
    |${q.question}
    |
    |(Type your answer, or 'hint' for a clue)
    """.stripMargin

  // pointsForDifficulty maps difficulty strings to base point values.
  // Hint deductions are applied on top of this in handleAnswer.
  private def pointsForDifficulty(difficulty: String): Int =
    difficulty match {
      case "easy"   => 10
      case "medium" => 20
      case "hard"   => 30
      case _        => 10
    }
}

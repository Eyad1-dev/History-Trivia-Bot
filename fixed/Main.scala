// ============================================================
//  Main.scala
//  Entry point and main game loop for the History Trivia Bot.
// ============================================================

object Main {

  // initialState builds the starting ConversationState for a new player.
  // Called once at startup; every subsequent state is derived from this
  // via immutable .copy() calls inside the handler functions.
  def initialState(name: String): ConversationState =
    ConversationState(
      user = UserProfile(
        name            = name,
        score           = 0,
        totalAnswered   = 0,
        correctAnswered = 0,
        weakTopics      = List.empty,
        weakEras        = List.empty,
        answeredIds     = List.empty,
        hintsUsed       = Map.empty
      ),
      history             = List.empty,
      preferences         = Map.empty,
      currentQuestion     = None,
      recommendedQuestion = None,   // no recommendation yet
      isRunning           = true
    )


  // loop is the main game loop.
  // @annotation.tailrec guarantees the compiler converts this recursion into
  // a loop at bytecode level, so a long session will never overflow the stack.
  @annotation.tailrec
  def loop(state: ConversationState): Unit = {

    // Detect the player's overall mood from conversation history
    // and show a small mood indicator next to the prompt.
    val mood = MemoryTracker.getUserMood(state.history)
    val moodNote = mood match {
      case "positive" => " :)"
      case "negative" => " :( Hang in there!"
      case _          => ""
    }

    print(s"\nYou$moodNote > ")
    val input = scala.io.StdIn.readLine()

    // Handle Ctrl+Z / EOF gracefully
    if (input == null) {
      println("Goodbye!")
      return
    }

    // detectRepeatedQuery checks whether the user has asked something
    // similar before; if so, we acknowledge it before answering again.
    val isRepeated = MemoryTracker.detectRepeatedQuery(input, state.history)
    if (isRepeated) {
      println("(You've asked something similar before - let me answer again!)")
    }

    val (response, newState) = ChatEngine.handleUserInput(input, state)
    println(s"\nBot > $response")

    if (!newState.isRunning) {
      println(printFinalStats(newState))
    } else {
      loop(newState)
    }
  }


  // printFinalStats builds the end-of-session statistics banner.
  // It uses MemoryTracker.summarizeConversation to include a full
  // intent breakdown alongside the score summary.
  def printFinalStats(state: ConversationState): String = {
    val accuracy =
      if (state.user.totalAnswered == 0) 0
      else (state.user.correctAnswered * 100) / state.user.totalAnswered

    val mostDiscussed = MemoryTracker
      .getMostUsedIntents(state.history)
      .headOption
      .map { case (intent, count) => s"$intent ($count times)" }
      .getOrElse("none")

    s"""
    |========================================
    |           FINAL STATISTICS
    |========================================
    | Player         : ${state.user.name}
    | Final Score    : ${state.user.score}
    | Total Answered : ${state.user.totalAnswered}
    | Correct        : ${state.user.correctAnswered}
    | Accuracy       : $accuracy%
    | Weak Topics    : ${if (state.user.weakTopics.isEmpty) "none" else state.user.weakTopics.mkString(", ")}
    | Weak Eras      : ${if (state.user.weakEras.isEmpty) "none" else state.user.weakEras.mkString(", ")}
    | Most Used      : $mostDiscussed
    |========================================
    ${MemoryTracker.summarizeConversation(state.history)}
    """.stripMargin
  }


  def main(args: Array[String]): Unit = {

    println(ChatEngine.greetUser())

    print("Your name > ")
    val nameInput = scala.io.StdIn.readLine()
    val name      = if (nameInput == null || nameInput.trim.isEmpty) "Player" else nameInput.trim

    val startState = initialState(name)

    println(s"\nWelcome, ${startState.user.name}! Let's see how well you know your history!")
    println("Type 'start' to get your first question, or 'recommend' to get a suggested one.\n")

    loop(startState)
  }
}

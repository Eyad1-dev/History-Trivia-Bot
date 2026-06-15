// ============================================================
//  Models.scala
//  All shared data types for the History Trivia Bot.
//  Every type here is immutable (case class / sealed trait)
//  so the whole application shares one consistent data model.
// ============================================================

// Intent  represents every possible action the user can request.
// sealed  means all cases must be defined in this file,
// which lets the compiler warn us if we forget a branch in a match.
sealed trait Intent
case object Greeting       extends Intent
case object AskQuestion    extends Intent
case object AnswerQuestion extends Intent
case object GetHint        extends Intent
case object GetScore       extends Intent
case object GetSummary     extends Intent
case object Recommend      extends Intent
case object Quit           extends Intent
case object Unknown        extends Intent


// TriviaQuestion holds every field for a single quiz question.
// Using a case class gives us structural equality, copy(), and pattern matching for free.
case class TriviaQuestion(
  id         : Int,
  question   : String,
  answer     : String,
  era        : String,
  region     : String,
  difficulty : String,
  topic      : String,
  hint1      : String,
  hint2      : String,
  hint3      : String
)


// InteractionEntry records a single conversation turn (one user message + one bot reply).
// Storing the intent lets MemoryTracker build statistics without re-parsing text.
case class InteractionEntry(
  sequenceNum : Int,
  timestamp   : Long,
  userInput   : String,   // always the REAL text the user typed
  botResponse : String,
  intent      : Intent
)


// UserProfile tracks everything about the player's progress.
// weakTopics / weakEras grow as the player answers incorrectly,
// and are used by the RecommendationEngine to prioritise practice questions.
case class UserProfile(
  name            : String,
  score           : Int,
  totalAnswered   : Int,
  correctAnswered : Int,
  weakTopics      : List[String],
  weakEras        : List[String],
  answeredIds     : List[Int],
  hintsUsed       : Map[Int, Int]   // questionId -> number of hints used
)


// ConversationState is the single source of truth passed through every function.
// Nothing is stored in a global variable; every handler receives the old state
// and returns a brand-new state, which is the core FP principle of immutability.
//
// recommendedQuestion: stores the last question the bot recommended so that
// typing 'start' immediately after 'recommend' delivers exactly that question.
case class ConversationState(
  user                : UserProfile,
  history             : List[InteractionEntry],
  preferences         : Map[String, String],
  currentQuestion     : Option[TriviaQuestion],
  recommendedQuestion : Option[TriviaQuestion],  // tracks last recommendation
  isRunning           : Boolean
)

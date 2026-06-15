// ============================================================
//  MemoryTracker.scala
//  Tracks the full conversation history and derives insights
//  from it: mood, topics, repeated queries, and summaries.
//
//  All functions here are pure - they read state and return
//  new values without mutating anything.
// ============================================================

object MemoryTracker {

  // logInteraction records one conversation turn and returns a brand-new state.
  // We never modify the old state; we build a fresh one with .copy().
  // Prepending with :: is O(1) for a List; we reverse on read when order matters.
  def logInteraction(
    userInput   : String,
    botResponse : String,
    intent      : Intent,
    context     : ConversationState
  ): ConversationState = {
    val newEntry = InteractionEntry(
      sequenceNum = context.history.length + 1,
      timestamp   = System.currentTimeMillis(),
      userInput   = userInput,
      botResponse = botResponse,
      intent      = intent
    )
    context.copy(history = newEntry :: context.history)
  }


  // getConversationHistory returns the full log in chronological order
  // (oldest first). We store newest-first for O(1) prepend, so we reverse here.
  def getConversationHistory(state: ConversationState): List[InteractionEntry] =
    state.history.reverse


  // getLastNInteractions returns the N most recent turns without scanning the whole list.
  // take(n) is a built-in HOF that stops after n elements.
  def getLastNInteractions(n: Int, state: ConversationState): List[InteractionEntry] =
    state.history.take(n)


  // detectRepeatedQuery returns true when the current input shares 2 or more
  // meaningful words (length > 3) with any previous user message.
  // Using length > 3 avoids false matches on common short words like "the", "is".
  // exists is a HOF that short-circuits as soon as one match is found.
  def detectRepeatedQuery(input: String, history: List[InteractionEntry]): Boolean = {
    val inputTokens = input.toLowerCase.trim.split("\\s+").toList
    history.exists { entry =>
      val pastTokens  = entry.userInput.toLowerCase.split("\\s+").toList
      // filter keeps only tokens that appear in pastTokens AND are longer than 3 chars
      val sharedWords = inputTokens.filter(token => pastTokens.contains(token) && token.length > 3)
      sharedWords.length >= 2
    }
  }


  // extractTopics scans the conversation history for history-related keywords
  // and returns the distinct topics detected.
  //
  // FIX: we now use whole-word matching (splitting on spaces and comparing tokens)
  // instead of substring contains(), which previously caused "start" to match "art"
  // (culture) and similar false positives.
  //
  // flatMap is used because each history entry can match multiple topics;
  // flatMap collapses the resulting List[List[String]] into a flat List[String].
  def extractTopics(history: List[InteractionEntry]): List[String] = {
    val topicKeywords = Map(
      "rulers"      -> List("pharaoh", "king", "emperor", "president", "ruler", "leader", "founded", "khan"),
      "battles"     -> List("war", "battle", "fight", "crusade", "revolution", "hastings"),
      "culture"     -> List("art", "paint", "library", "music", "culture", "mona"),
      "mythology"   -> List("god", "goddess", "myth", "legend", "poseidon"),
      "exploration" -> List("discover", "explore", "voyage", "travel", "columbus"),
      "science"     -> List("space", "invention", "discovery", "scientist", "gagarin"),
      "politics"    -> List("vote", "election", "law", "constitution", "independence", "berlin", "wall"),
      "disasters"   -> List("plague", "disease", "famine", "disaster", "death")
    )

    val detectedTopics = history.flatMap { entry =>
      // Split into whole words so "start" does not match keyword "art"
      val words = entry.userInput.toLowerCase.split("\\s+").toSet

      // For each topic, check if any of its keywords exactly matches a word the user typed
      topicKeywords.keys.filter { topic =>
        val keywords = topicKeywords(topic)
        keywords.exists(kw => words.contains(kw))
      }.toList
    }

    detectedTopics.distinct
  }


  // summarizeConversation builds a human-readable session summary.
  // foldLeft processes each InteractionEntry one at a time, accumulating
  // a Map[intentName -> count]. Starting value is an empty map.
  def summarizeConversation(history: List[InteractionEntry]): String = {
    if (history.isEmpty) {
      "No conversation history yet!"
    } else {
      // acc is the accumulator map; we increment the count for each intent
      val intentCounts = history.foldLeft(Map[String, Int]()) { (acc, entry) =>
        val key = entry.intent.toString
        acc + (key -> (acc.getOrElse(key, 0) + 1))
      }

      val totalTurns     = history.length
      val questionsAsked = intentCounts.getOrElse("AskQuestion", 0)
      val hintsUsed      = intentCounts.getOrElse("GetHint", 0)
      val answersGiven   = intentCounts.getOrElse("AnswerQuestion", 0)
      val topics         = extractTopics(history)
      val topicsLine     = if (topics.isEmpty) "none detected yet" else topics.mkString(", ")

      s"""
      |--- Conversation Summary ---
      |Total turns     : $totalTurns
      |Questions asked : $questionsAsked
      |Answers given   : $answersGiven
      |Hints used      : $hintsUsed
      |Topics detected : $topicsLine
      """.stripMargin
    }
  }


  // getMostUsedIntents returns a list of (intentName, count) pairs sorted
  // by count descending, showing which commands the player used most.
  //
  // Renamed from getMostDiscussedTopics (which was misleading - it counted
  // intents, not discussion topics). The logic is identical.
  def getMostUsedIntents(history: List[InteractionEntry]): List[(String, Int)] = {
    val countMap = history.foldLeft(Map[String, Int]()) { (acc, entry) =>
      val key = entry.intent.toString
      acc + (key -> (acc.getOrElse(key, 0) + 1))
    }
    // sortBy with negated count gives descending order (sortBy is ascending by default)
    countMap.toList.sortBy { case (_, count) => -count }
  }


  // getUserMood performs simple keyword-based sentiment analysis.
  // We count positive vs negative words across all user messages.
  // The +2 buffer prevents a single word from flipping the mood.
  def getUserMood(history: List[InteractionEntry]): String = {
    val positiveWords = List(
      "great", "love", "amazing", "awesome",
      "good", "nice", "fantastic", "excellent",
      "correct", "yes", "thanks", "thank", "easy"
    )
    val negativeWords = List(
      "boring", "hate", "bad", "wrong",
      "terrible", "awful", "difficult",
      "hard", "confused", "lost", "no", "ugh"
    )

    // map each entry to its user input, join all text, split into words
    val allWords = history
      .map(_.userInput.toLowerCase)
      .mkString(" ")
      .split("\\s+")
      .toList

    val positiveCount = allWords.count(w => positiveWords.contains(w))
    val negativeCount = allWords.count(w => negativeWords.contains(w))

    if      (positiveCount > negativeCount + 2) "positive"
    else if (negativeCount > positiveCount + 2) "negative"
    else                                         "neutral"
  }
}

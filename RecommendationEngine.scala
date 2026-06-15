object RecommendationEngine {

  //getUserPreferences bet4of el user e5tar eh we beyraga3holo

  def getUserPreferences(state: ConversationState): Map[String, String] = {
    state.preferences
  }
  //law el user 8ayar mn easy le hard beyet3amel update bas el e5teyar ely e5taro fe el 2awal beyefdal mawgod
  def updatePreferences(key   : String, value : String, prefs : Map[String, String]): Map[String, String] = {
    prefs + (key -> value)
  }
// beya5od el preference el el user 3ayzo we beye5od el 2as2ela
  def recommend(preferences : Map[String, String], triviaQuestions: List[TriviaQuestion]): List[TriviaQuestion] = {

 //bey4of el user e5tar mn anhy era, region we el so3oba

    val preferredEra        = preferences.get("era")
    val preferredRegion     = preferences.get("region")
    val preferredDifficulty = preferences.get("difficulty")
    val lastCorrectEra      = preferences.get("lastCorrectEra")

    //beyedy el score le kol egaba sa7

    def scoreQuestion(q: TriviaQuestion): Int = {
      val eraScore = preferredEra.filter(_ == q.era).map(_ => 3).getOrElse(0)
      val regionScore= preferredRegion.filter(_ == q.region).map(_ => 2).getOrElse(0)
      val diffScore= preferredDifficulty.filter(_ == q.difficulty).map(_ => 2).getOrElse(0)
      val weakEraScore= lastCorrectEra.filter(_ != q.era).map(_ => 1).getOrElse(0)
      eraScore + regionScore + diffScore + weakEraScore
    }

    // sort el score mn el highest to lowest

    val scored = triviaQuestions.map(q => (q, scoreQuestion(q)))
    val sorted = scored.sortBy { case (_, score) => -score }

    //sort el so2al mn highest to lowest 3a4an el user ye3raf eh 2awha4 so2al gaweb feh
    sorted.map { case (q, _) => q }
  }

 //explainRecommendation bey5aly el output ely beyetla3 lel user bet2ary be este5dam stripmargin 3a4an bethotaha taht ba3d
  def explainRecommendation(q: TriviaQuestion): String = {
    val difficultyNote = q.difficulty match {
      case "easy"   => "This is a good warm-up question!"
      case "medium" => "This will challenge you a bit!"
      case "hard"   => "This is a tough one - good luck!"
      case _        => "Give this one a try!"
    }

    s"""Question ID ${q.id}:
       |"${q.question}"
       |Era: ${q.era} | Region: ${q.region} | Topic: ${q.topic}
       |Difficulty: ${q.difficulty.toUpperCase}
       |$difficultyNote
       |Type 'start' to get this question.
     """.stripMargin
  }
  def filterByEra(era: String, triviaQuestions : List[TriviaQuestion]): List[TriviaQuestion] = {
    triviaQuestions.filter(_.era == era)
  }
  def filterByDifficulty(difficulty : String, triviaQuestions  : List[TriviaQuestion]): List[TriviaQuestion] = {
    triviaQuestions.filter(_.difficulty == difficulty)
  }
  def filterByRegion(region: String, triviaQuestions : List[TriviaQuestion]): List[TriviaQuestion] = {
    triviaQuestions.filter(_.region == region)
  }
}
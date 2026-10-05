package com.rabidstudios.punchtheclown

enum class AchievementType {
    STANDARD,
    INCREMENTAL
}

enum class AchievementKey {
    STEP_RIGHT_UP,
    DOUBLE_FEATURE,
    CARNIVAL_REGULAR,
    MIDWAY_VETERAN,
    FIRST_PUNCH,
    NO_CLOWNING_AROUND,
    GOOD_MEMORY,
    STEEL_TRAP,
    CLOWN_SAVANT,
    PUNCH_DRUNK,
    HEAVY_HITTER,
    PUNCHING_MACHINE,
    FIRST_BEANING,
    BEAN_COUNTER,
    BEAN_THERE_DONE_THAT,
    BEAN_MACHINE,
    HOT_STREAK,
    ON_A_ROLL,
    DEADEYE_AT_THE_MIDWAY,
    CLOWN_CARNAGE
}

data class AchievementDefinition(
    val key: AchievementKey,
    val title: String,
    val description: String,
    val type: AchievementType,
    val targetSteps: Int? = null,
    val mode: GameMode? = null
)

object AchievementCatalog {
    val all: List<AchievementDefinition> = listOf(
        AchievementDefinition(AchievementKey.STEP_RIGHT_UP, "Step Right Up!", "Complete your first game in either mode.", AchievementType.STANDARD),
        AchievementDefinition(AchievementKey.DOUBLE_FEATURE, "Double Feature", "Play at least one game of Punch and one game of Beaning.", AchievementType.STANDARD),
        AchievementDefinition(AchievementKey.CARNIVAL_REGULAR, "Carnival Regular", "Play 25 total games.", AchievementType.INCREMENTAL, 25),
        AchievementDefinition(AchievementKey.MIDWAY_VETERAN, "Midway Veteran", "Play 100 total games.", AchievementType.INCREMENTAL, 100),

        AchievementDefinition(AchievementKey.FIRST_PUNCH, "First Punch", "Make your first correct punch.", AchievementType.STANDARD, mode = GameMode.PUNCH),
        AchievementDefinition(AchievementKey.NO_CLOWNING_AROUND, "No Clowning Around", "Complete your first full Punch sequence.", AchievementType.STANDARD, mode = GameMode.PUNCH),
        AchievementDefinition(AchievementKey.GOOD_MEMORY, "Good Memory", "Reach a longest Punch sequence of 5.", AchievementType.STANDARD, mode = GameMode.PUNCH),
        AchievementDefinition(AchievementKey.STEEL_TRAP, "Steel Trap", "Reach a longest Punch sequence of 10.", AchievementType.STANDARD, mode = GameMode.PUNCH),
        AchievementDefinition(AchievementKey.CLOWN_SAVANT, "Clown Savant", "Reach a longest Punch sequence of 20.", AchievementType.STANDARD, mode = GameMode.PUNCH),
        AchievementDefinition(AchievementKey.PUNCH_DRUNK, "Punch Drunk", "Make 100 correct punches.", AchievementType.INCREMENTAL, 100, GameMode.PUNCH),
        AchievementDefinition(AchievementKey.HEAVY_HITTER, "Heavy Hitter", "Make 500 correct punches.", AchievementType.INCREMENTAL, 500, GameMode.PUNCH),
        AchievementDefinition(AchievementKey.PUNCHING_MACHINE, "Punching Machine", "Make 1,000 correct punches.", AchievementType.INCREMENTAL, 1000, GameMode.PUNCH),

        AchievementDefinition(AchievementKey.FIRST_BEANING, "First Beaning", "Hit your first clown in Beaning.", AchievementType.STANDARD, mode = GameMode.BEANING),
        AchievementDefinition(AchievementKey.BEAN_COUNTER, "Bean Counter", "Hit 100 clowns.", AchievementType.INCREMENTAL, 100, GameMode.BEANING),
        AchievementDefinition(AchievementKey.BEAN_THERE_DONE_THAT, "Bean There, Done That", "Hit 500 clowns.", AchievementType.INCREMENTAL, 500, GameMode.BEANING),
        AchievementDefinition(AchievementKey.BEAN_MACHINE, "Bean Machine", "Hit 1,000 clowns.", AchievementType.INCREMENTAL, 1000, GameMode.BEANING),
        AchievementDefinition(AchievementKey.HOT_STREAK, "Hot Streak", "Reach a longest Beaning run of 10 hits.", AchievementType.STANDARD, mode = GameMode.BEANING),
        AchievementDefinition(AchievementKey.ON_A_ROLL, "On a Roll", "Reach a longest Beaning run of 25 hits.", AchievementType.STANDARD, mode = GameMode.BEANING),
        AchievementDefinition(AchievementKey.DEADEYE_AT_THE_MIDWAY, "Deadeye at the Midway", "Finish a Beaning game with at least 20 hits and zero misses.", AchievementType.STANDARD, mode = GameMode.BEANING),
        AchievementDefinition(AchievementKey.CLOWN_CARNAGE, "Clown Carnage", "Hit 50 clowns in a single Beaning game.", AchievementType.STANDARD, mode = GameMode.BEANING)
    )
}

# M4 / 0.4.x Leaderboard and Social Architecture

Status: APPROVED STARTING ARCHITECTURE FOR M4
Baseline: 0.3.21-m3-beta
Baseline commit: 8cd22312d7f17e7fdf8dbfffae962176f8ef3b75

## Objective

M4 adds the competitive/social layer without changing the frozen M3 gameplay.

Initial M4 scope:
- Google Play Games Services v2 platform authentication
- Punch the Clown online high-score leaderboard
- Beaning the Clowns online high-score leaderboard
- Top 50 display
- current player's score and rank
- scores near the current player's rank
- achievements later in M4
- preserve all existing local statistics and offline gameplay

M4 does not require a custom server for the initial leaderboard implementation.

## Technology Decision

Use Google Play Games Services v2.

Pin the Android dependency when implementation begins:

```
com.google.android.gms:play-services-games-v2:22.1.0
```

Do not use the deprecated Play Games Services v1 API.

Platform authentication will initialize at application startup. Authentication failure or lack of network access must never prevent either game mode from running.

## Leaderboards

Create two Play Games leaderboards:

1. Punch the Clown - High Score
2. Beaning the Clowns - High Score

Both use:
- numeric scores
- larger-is-better ordering
- public collection as the default
- Google Play daily, weekly, and all-time variants
- leaderboard tamper protection
- reasonable upper score limits configured in Play Console

Do not create extra leaderboards for every existing local statistic during the first M4 implementation. Highest level, longest sequence/run, total hits, total misses, games played, and similar counters remain local statistics for now.

## Local Data Remains Authoritative for Local Stats

The existing SharedPreferences keys must remain compatible.

Punch the Clown:
- high_score
- highest_level
- longest_sequence
- games_played
- correct_punches
- sequences_completed

Beaning the Clowns:
- beaning_high_score
- beaning_highest_level
- beaning_total_hits
- beaning_total_misses
- beaning_games_played
- beaning_longest_run

M4 must not rename, reset, migrate away, or corrupt these values merely to add online services.

## New Internal Model

Introduce an app-level game mode type rather than coupling online services to MainActivity's private ActiveGameMode.

Suggested model:

```kotlin
enum class GameMode {
    PUNCH,
    BEANING
}

data class GameResult(
    val mode: GameMode,
    val score: Long,
    val level: Int,
    val runMetric: Int,
    val hits: Int = 0,
    val misses: Int = 0
)
```

GameResult is the handoff object produced when a game ends. It lets local stats, online score submission, and future achievements consume the same result without duplicating gameplay logic.

## Component Boundaries

### LocalStatsRepository

Responsibilities:
- read/write existing SharedPreferences statistics
- preserve M3 key compatibility
- expose best scores for online synchronization
- store pending leaderboard submissions

MainActivity should gradually stop knowing individual preference keys directly.

### LeaderboardGateway

Create an interface so the game is not directly coupled to Google Play APIs.

Responsibilities:
- report authentication state
- request interactive sign-in when necessary
- submit a score
- load current player score/rank
- load top scores
- load player-centered scores
- open Google Play's native leaderboard UI when desired

### PlayGamesLeaderboardGateway

Google Play Games Services v2 implementation of LeaderboardGateway.

Use:
- PlayGamesSdk.initialize(...)
- PlayGames.getGamesSignInClient(...)
- PlayGames.getLeaderboardsClient(...)

No gameplay View should call Play Games APIs directly.

### Offline / Pending Submission Store

Maintain only the highest pending score for each leaderboard:

- pending_online_punch_score
- pending_online_beaning_score

If the player is offline or not authenticated at game over:
- preserve the best pending score locally
- continue normal gameplay/results immediately

When authentication/network becomes available:
- submit the pending score using submitScoreImmediate
- clear the pending value only after confirmed success

Because the online boards are larger-is-better high-score boards, keeping one maximum pending value per mode is sufficient.

## Submission Flow

Submit scores only at meaningful transitions, primarily game over.

Punch:
1. Existing local stats update.
2. Build GameResult.
3. If authenticated, submit the score.
4. If submission fails, retain the maximum score as pending.
5. Show results normally regardless of online outcome.

Beaning:
1. Existing local stats update.
2. Build GameResult.
3. Same asynchronous submission behavior.

Online calls must never block game-over rendering or navigation.

## Authentication Flow

At startup:
1. Initialize Play Games Services v2.
2. Let platform authentication run.
3. Query authentication status.
4. Do not interrupt normal gameplay if authentication is unavailable.

On resume:
- refresh authentication status because it may have changed while the Activity was inactive
- attempt to flush pending scores when authenticated

If the player opens online leaderboards while unauthenticated:
- offer the Play Games sign-in flow
- if sign-in is declined/fails, return cleanly to the game's local UI

Do not create a separate Punch the Clown account system for M4.

## Leaderboard Read Model

Use a neutral app model instead of exposing Google SDK objects to UI code:

```kotlin
data class RankedScore(
    val rank: Long,
    val displayName: String,
    val score: Long,
    val formattedScore: String,
    val isCurrentPlayer: Boolean
)

data class LeaderboardSnapshot(
    val mode: GameMode,
    val top50: List<RankedScore>,
    val playerScore: RankedScore?,
    val nearbyScores: List<RankedScore>
)
```

This keeps the carnival UI independent of Google SDK data buffers.

## Top 50 Retrieval

The Android leaderboard API fetches at most 25 scores per page.

To display the planned Top 50:
1. loadTopScores(..., maxResults = 25)
2. copy the returned data into RankedScore objects
3. loadMoreScores(..., maxResults = 25, PAGE_DIRECTION_NEXT)
4. merge/deduplicate
5. release Google leaderboard buffers immediately after copying data

If fewer than 50 public scores exist, display what is available.

## Player Rank and Nearby Scores

For the player's own rank:
- loadCurrentPlayerLeaderboardScore(...)

For the scores around the player:
- loadPlayerCenteredScores(...)
- request an odd-sized window, initially 11 entries
- highlight the current player in the custom UI

If the player has not submitted a score, show an appropriate no-ranking state rather than treating it as an error.

## M4 Leaderboard UI

Build a Punch the Clown themed leaderboard screen rather than relying only on the default Google UI.

Initial layout:
- Punch the Clown / Beaning the Clowns selector
- All-Time Top 50
- YOUR RANK card
- AROUND YOU section
- Refresh control
- Back to Game Select
- optional View in Play Games action

The online leaderboard screen must support:
- loading state
- signed-out state
- offline state
- empty leaderboard state
- API error state
- normal data state

Player display names must support Unicode and should use a font capable of rendering normal Google Play player names.

## Score Integrity

Enable Play Games leaderboard tamper protection.

Also configure plausible upper score bounds in Play Console.

Do not trust a client-only custom leaderboard database for competitive ranking when Play Games can manage score ranking and tamper filtering directly.

## Achievements Boundary

Achievements belong in M4 but should be layered after the leaderboard foundation works.

Define a future AchievementGateway with operations such as:
- unlock(id)
- increment(id, steps)
- showAchievements()

Achievement evaluation should consume GameResult and persisted local totals. Do not scatter achievement checks throughout touch handlers or rendering code.

Actual achievement names, thresholds, and Play Console IDs will be designed separately before implementation.

## 0.4.x Implementation Sequence

0.4.0:
- add Play Games Services v2 dependency
- add Play Games project configuration placeholders/resources
- initialize platform authentication
- add GameMode / GameResult
- add LocalStatsRepository
- add LeaderboardGateway and PlayGamesLeaderboardGateway
- no custom leaderboard screen required yet
- verify both M3 game modes still work offline

0.4.1:
- configure the two Play Console leaderboards
- wire generated leaderboard IDs
- submit Punch and Beaning scores at game over
- pending/offline score retry

0.4.2:
- custom Top 50 screen
- own rank
- nearby scores
- loading/offline/error states

0.4.3:
- achievement architecture and first achievement set

Later 0.4.x:
- UI polish
- Play Games native UI shortcuts
- additional achievements
- leaderboard/achievement regression testing

## Non-Goals for Initial M4

Do not add:
- subscriptions
- ads or rewarded ads
- custom account registration
- custom leaderboard backend
- cloud save unless separately approved
- gameplay balance changes
- M3 visual changes

Those are separate milestones or future decisions.

## Freeze Rule

The 0.3.21-m3-beta gameplay baseline is frozen.

M4 may add shared infrastructure around the games, but changes to Punch gameplay, Beaning gameplay, approved artwork, target behavior, M3 scoring rules, or established M3 layout require a specific bug fix or explicit approval.

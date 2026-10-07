# M4 Achievement Setup

Version foundation: 0.4.24-m4-alpha

The app-side achievement architecture is implemented and all 20 Google Play Console achievement IDs are wired into `app/src/main/res/values/games_ids.xml`.

## Achievement catalog

| Resource name | Display name | Type | Target / unlock rule |
| --- | --- | --- | --- |
| achievement_step_right_up | Step Right Up! | Standard | Complete first game in either mode |
| achievement_double_feature | Double Feature | Standard | Play at least one Punch and one Beaning game |
| achievement_carnival_regular | Carnival Regular | Incremental | 25 total games |
| achievement_midway_veteran | Midway Veteran | Incremental | 100 total games |
| achievement_first_punch | First Punch | Standard | First correct Punch input |
| achievement_no_clowning_around | No Clowning Around | Standard | Complete first Punch sequence |
| achievement_good_memory | Good Memory | Standard | Longest Punch sequence 5 |
| achievement_steel_trap | Steel Trap | Standard | Longest Punch sequence 10 |
| achievement_clown_savant | Clown Savant | Standard | Longest Punch sequence 20 |
| achievement_punch_drunk | Punch Drunk | Incremental | 100 correct Punch inputs |
| achievement_heavy_hitter | Heavy Hitter | Incremental | 500 correct Punch inputs |
| achievement_punching_machine | Punching Machine | Incremental | 1,000 correct Punch inputs |
| achievement_first_beaning | First Beaning | Standard | First Beaning hit |
| achievement_bean_counter | Bean Counter | Incremental | 100 Beaning hits |
| achievement_bean_there__done_that | Bean There, Done That | Incremental | 500 Beaning hits |
| achievement_bean_machine | Bean Machine | Incremental | 1,000 Beaning hits |
| achievement_hot_streak | Hot Streak | Standard | Longest Beaning run 10 |
| achievement_on_a_roll | On a Roll | Standard | Longest Beaning run 25 |
| achievement_deadeye_at_the_midway | Deadeye at the Midway | Standard | Finish with at least 20 hits and 0 misses |
| achievement_clown_carnage | Clown Carnage | Standard | 50 hits in one Beaning game |

## Play Console configuration

Create each achievement in the same Play Games Services project as the two leaderboards.

For incremental achievements, configure the exact number of steps shown in the table. All other achievements are standard one-time achievements.

All generated Play Games achievement IDs have been copied into `games_ids.xml`. The build can now submit unlocks and incremental progress to Google Play Games for tester accounts.

## Runtime behavior

- Existing local statistics remain authoritative.
- Standard achievements that can be derived from local totals are re-evaluated whenever Play Games authentication succeeds.
- Incremental achievements use `setSteps`, so synchronization is monotonic and does not double-count repeated syncs.
- Deadeye at the Midway and Clown Carnage are single-game conditions not represented by existing aggregate statistics, so the app stores local earned flags and syncs them later if the game was completed offline.

package com.rabidstudios.punchtheclown

interface AchievementGateway {
    fun unlock(key: AchievementKey)
    fun setSteps(key: AchievementKey, steps: Int)
}

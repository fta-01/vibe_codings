package com.example.workout.data.exercise

/**
 * 运动组合推荐 —— 从运动库中按「热身 → 主运动 → 拉伸」拼装合理套餐
 *
 * 组合中的 item 分两种：
 * - 运动动作（type = EXERCISE）
 * - 休息间隔（type = REST），自动插入高强度动作之间
 */
data class WorkoutPlan(
    val name: String,
    val description: String,
    val items: List<PlanItem>,
) {
    /** 组合总时长（秒），含运动 + 休息 */
    val totalSeconds: Int
        get() = items.sumOf {
            when (it.type) {
                PlanItemType.EXERCISE -> it.exercise?.defaultSeconds ?: 0
                PlanItemType.REST -> it.restSeconds ?: 0
            }
        }

    val totalMinutesDisplay: String
        get() {
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            return if (s == 0) "${m} 分钟" else "${m} 分 ${s} 秒"
        }

    /** 计时型运动数 */
    val timedCount: Int
        get() = items.count { it.type == PlanItemType.EXERCISE && it.exercise?.isTimed == true }

    /** 纯运动时长（不含休息） */
    val exerciseSeconds: Int
        get() = items.filter { it.type == PlanItemType.EXERCISE }
            .sumOf { it.exercise?.defaultSeconds ?: 0 }

    val exerciseMinutesDisplay: String
        get() {
            val m = exerciseSeconds / 60
            val s = exerciseSeconds % 60
            return if (s == 0) "${m} 分钟" else "${m} 分 ${s} 秒"
        }
}

enum class PlanItemType {
    EXERCISE, REST
}

data class PlanItem(
    val exercise: Exercise? = null,
    val type: PlanItemType = PlanItemType.EXERCISE,
    val order: Int,
    val label: String,
    val restSeconds: Int? = null,
)

object WorkoutPlanBuilder {

    /**
     * 根据运动库自动生成推荐组合
     *
     * 设计原则：
     * - 热身阶段：2-3 个低强度动作，无需休息间隔
     * - 主运动阶段：3-4 个动作，高强度有氧之间插 15-20s 休息，力量组之间插 30-45s 休息
     * - 拉伸阶段：2 个动作覆盖下肢 + 躯干，无需休息
     */
    fun buildPlans(exercises: List<Exercise>): List<WorkoutPlan> {
        val warmups = exercises.filter { it.category == "热身" }
        val strength = exercises.filter { it.category == "力量" }
        val cardio = exercises.filter { it.category == "有氧 · 跑动" }
        val stretches = exercises.filter { it.category == "拉伸 · 放松" }

        val plans = mutableListOf<WorkoutPlan>()

        // ── 组合 1：全身唤醒（低强度，适合新手/恢复日）──
        // 热身: 原地踏步60s + 肩部环绕 + 髋部环绕
        // 主运动: 臀桥(3×15) + 靠墙静蹲45s，中间休息30s
        // 拉伸: 婴儿式45s
        plans.add(buildPlan(
            name = "🌱 全身唤醒",
            description = "低强度热身 + 简单力量 + 放松拉伸，适合新手或恢复日",
            warmups = pickByPriority(warmups, listOf("march-in-place", "shoulder-circles", "hip-circles")),
            mains = pickByPriority(strength, listOf("glute-bridge", "wall-sit")),
            restBetweenMains = 30,
            stretches = pickByPriority(stretches, listOf("child-pose")),
        ))

        // ── 组合 2：燃脂心肺（中高强度，有氧间歇）──
        // 热身: 原地踏步60s + 手臂摆动30s + 高抬腿走30s
        // 主运动: 开合跳30s → 休息15s → 高抬腿30s → 休息15s → 后踢腿30s → 休息15s → 原地慢跑120s
        // 拉伸: 婴儿式45s
        plans.add(buildPlan(
            name = "🔥 燃脂心肺",
            description = "热身激活 + 高强度有氧间歇（含休息）+ 拉伸放松，适合减脂",
            warmups = pickByPriority(warmups, listOf("march-in-place", "arm-swings", "high-knee-walk")),
            mains = pickByPriority(cardio, listOf("jumping-jacks", "high-knees", "butt-kicks", "jog-in-place")),
            restBetweenMains = 15,
            stretches = pickByPriority(stretches, listOf("child-pose")),
        ))

        // ── 组合 3：力量塑形（力量为主，组间休息充分）──
        // 热身: 原地踏步60s + 弓步扩胸 + 体转
        // 主运动: 深蹲(3×15) → 休息45s → 臀桥(3×15) → 休息45s → 平板支撑45s
        // 拉伸: 婴儿式45s
        plans.add(buildPlan(
            name = "💪 力量塑形",
            description = "热身 + 下肢与核心力量训练（含充分组间休息）+ 拉伸",
            warmups = pickByPriority(warmups, listOf("march-in-place", "lunge-chest-opener", "torso-twist")),
            mains = pickByPriority(strength, listOf("squat", "glute-bridge", "plank")),
            restBetweenMains = 45,
            stretches = pickByPriority(stretches, listOf("child-pose")),
        ))

        // ── 组合 4：快速拉伸放松（短时间，适合睡前/休息日）──
        // 热身: 颈部环绕 + 猫式伸展
        // 拉伸: 婴儿式45s
        plans.add(buildPlan(
            name = "🧘 快速放松",
            description = "简短热身 + 拉伸放松，适合睡前或休息日",
            warmups = pickByPriority(warmups, listOf("neck-circles", "cat-stretch")),
            mains = emptyList(),
            restBetweenMains = 0,
            stretches = pickByPriority(stretches, listOf("child-pose")),
        ))

        return plans
    }

    /**
     * 构建组合：热身 → [主运动 休息]* → 拉伸
     * 休息只插在主运动之间（不在热身间、拉伸间）
     */
    private fun buildPlan(
        name: String,
        description: String,
        warmups: List<Exercise>,
        mains: List<Exercise>,
        restBetweenMains: Int,
        stretches: List<Exercise>,
    ): WorkoutPlan {
        val allItems = mutableListOf<PlanItem>()
        var order = 0

        // 热身阶段（无休息）
        warmups.forEach { ex ->
            allItems.add(PlanItem(exercise = ex, order = order++, label = "热身"))
        }

        // 主运动阶段（动作之间插休息）
        mains.forEachIndexed { index, ex ->
            allItems.add(PlanItem(exercise = ex, order = order++, label = "主运动"))
            // 在主运动之间插入休息（不在最后一个后面）
            if (index < mains.size - 1 && restBetweenMains > 0) {
                allItems.add(PlanItem(
                    type = PlanItemType.REST,
                    order = order++,
                    label = "休息",
                    restSeconds = restBetweenMains,
                ))
            }
        }

        // 拉伸阶段（无休息）
        stretches.forEach { ex ->
            allItems.add(PlanItem(exercise = ex, order = order++, label = "拉伸"))
        }

        return WorkoutPlan(name = name, description = description, items = allItems)
    }

    /** 按 preferredIds 顺序从 pool 中挑选运动，找不到就跳过 */
    private fun pickByPriority(pool: List<Exercise>, preferredIds: List<String>): List<Exercise> {
        val byId = pool.associateBy { it.id }
        return preferredIds.mapNotNull { byId[it] }
    }
}

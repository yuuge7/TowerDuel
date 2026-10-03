package com.towerduel.game.engine

import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.data.TargetPriority
import com.towerduel.game.data.TroopType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

private const val SPLASH_DAMAGE_FRACTION = 0.6f
private const val CHAIN_DAMAGE_FRACTION = 0.7f
private const val SELL_REFUND_FRACTION = 0.5f
private const val MULTI_SEND_GAP_MS = 350f

class GameEngine(
    val map: MapDef,
    val modifier: MatchModifier,
    playerDraft: List<TroopType>,
    aiDraft: List<TroopType>
) {
    private val segmentLengths = PathMath.segmentLengths(map.pathPoints)
    private val pathLength = segmentLengths.sum().coerceAtLeast(1f)
    val matchDurationSec: Int = modifier.matchDurationOverrideSec ?: GameData.MATCH_DURATION_SEC
    private val startingLives = modifier.livesOverride ?: GameData.STARTING_LIVES
    private val startingGold = GameData.STARTING_GOLD + modifier.startingGoldBonus

    val playerField = Battlefield("player", playerDraft, startingGold.toFloat(), startingLives)
    val aiField = Battlefield("ai", aiDraft, startingGold.toFloat(), startingLives)

    var elapsedMs: Float = 0f
        private set
    var outcome: MatchOutcome = MatchOutcome.ONGOING
        private set

    fun timeRemainingSec(): Int = ceil(matchDurationSec - elapsedMs / 1000f).toInt().coerceAtLeast(0)

    /** How far [tower] reaches in lane units: its attack range, or its aura for support towers. */
    fun towerReach(tower: TowerInstance): Float =
        if (tower.type.auraRange > 0f) tower.type.auraRange else tower.effectiveRange * modifier.rangeMultiplier

    fun sellRefund(tower: TowerInstance): Float {
        val spent = tower.type.cost + (if (tower.upgraded) tower.type.upgradeCost else 0)
        return spent * SELL_REFUND_FRACTION
    }

    // -------------------------------------------------------------------
    // Player / AI actions
    // -------------------------------------------------------------------

    fun placeTower(field: Battlefield, type: TroopType, x: Float, y: Float): PlaceResult {
        if (outcome != MatchOutcome.ONGOING) return PlaceResult.MATCH_OVER
        if (field.towers.size >= GameData.MAX_TOWERS_PER_LANE) return PlaceResult.LANE_FULL
        if (field.gold < type.cost) return PlaceResult.NOT_ENOUGH_GOLD
        val clampedX = x.coerceIn(4f, LaneSpace.WIDTH - 4f)
        val clampedY = y.coerceIn(4f, LaneSpace.HEIGHT - 4f)
        if (field.towers.any { dist(it.x, it.y, clampedX, clampedY) < GameData.MIN_TOWER_SPACING }) {
            return PlaceResult.TOO_CLOSE
        }
        field.gold -= type.cost
        field.towers.add(TowerInstance(field.nextInstanceId++, type, clampedX, clampedY))
        return PlaceResult.OK
    }

    fun upgradeTower(field: Battlefield, instanceId: Long): Boolean {
        if (outcome != MatchOutcome.ONGOING) return false
        val t = field.towers.find { it.instanceId == instanceId } ?: return false
        if (t.upgraded) return false
        if (field.gold < t.type.upgradeCost) return false
        field.gold -= t.type.upgradeCost
        t.upgraded = true
        return true
    }

    fun sellTower(field: Battlefield, instanceId: Long): Boolean {
        if (outcome != MatchOutcome.ONGOING) return false
        val t = field.towers.find { it.instanceId == instanceId } ?: return false
        field.gold += sellRefund(t)
        field.towers.remove(t)
        return true
    }

    fun sendEnemy(sourceField: Battlefield, targetField: Battlefield, sendType: EnemySendType): Boolean {
        if (outcome != MatchOutcome.ONGOING) return false
        if (sourceField.gold < sendType.cost) return false
        sourceField.gold -= sendType.cost
        spawn(targetField, sendType)
        for (i in 1 until sendType.count) {
            targetField.pendingSpawns.add(PendingSpawn(sendType, i * MULTI_SEND_GAP_MS))
        }
        return true
    }

    private fun spawn(field: Battlefield, type: EnemySendType) {
        val (startX, startY) = map.pathPoints.first()
        field.incomingEnemies.add(
            EnemyUnit(field.nextInstanceId++, type, hp = type.maxHp, x = startX, y = startY)
        )
    }

    // -------------------------------------------------------------------
    // Simulation tick
    // -------------------------------------------------------------------

    fun update(dtSeconds: Float) {
        if (outcome != MatchOutcome.ONGOING) return
        elapsedMs += dtSeconds * 1000f

        playerField.gold += GameData.BASE_INCOME_PER_SEC * modifier.incomeMultiplier * dtSeconds
        aiField.gold += GameData.BASE_INCOME_PER_SEC * modifier.incomeMultiplier * dtSeconds

        towerPass(playerField, dtSeconds)
        towerPass(aiField, dtSeconds)

        enemyPass(playerField, dtSeconds)
        enemyPass(aiField, dtSeconds)

        cleanupTracers(playerField, dtSeconds)
        cleanupTracers(aiField, dtSeconds)

        resolveOutcome()
    }

    private fun resolveOutcome() {
        val playerDead = playerField.lives <= 0
        val aiDead = aiField.lives <= 0
        outcome = when {
            playerDead && aiDead -> MatchOutcome.DRAW
            playerDead -> MatchOutcome.AI_WIN
            aiDead -> MatchOutcome.PLAYER_WIN
            elapsedMs / 1000f >= matchDurationSec -> when {
                playerField.lives > aiField.lives -> MatchOutcome.PLAYER_WIN
                aiField.lives > playerField.lives -> MatchOutcome.AI_WIN
                else -> MatchOutcome.DRAW
            }
            else -> MatchOutcome.ONGOING
        }
    }

    // -------------------------------------------------------------------
    // Towers: passive economy, auras, and attacks
    // -------------------------------------------------------------------

    private fun towerPass(field: Battlefield, dtSeconds: Float) {
        for (tower in field.towers) {
            // Passive economy tower (Gold Mine)
            if (tower.effectiveIncome > 0f) {
                field.gold += tower.effectiveIncome * dtSeconds
            }

            tower.cooldownMs -= dtSeconds * 1000f

            val effRange = tower.effectiveRange * modifier.rangeMultiplier

            // Continuous slow aura (Frost Spire). The strongest active slow wins.
            if (tower.effectiveSlow > 0f) {
                for (enemy in field.incomingEnemies) {
                    if (withinRange(tower, enemy, effRange)) {
                        val stillSlowed = elapsedMs < enemy.slowExpiresAtMs
                        enemy.slowFactor =
                            if (stillSlowed) maxOf(enemy.slowFactor, tower.effectiveSlow) else tower.effectiveSlow
                        enemy.slowExpiresAtMs = elapsedMs + 350f
                    }
                }
            }

            // Continuous poison aura (Poison Totem). The strongest active poison wins.
            if (tower.effectiveDotDps > 0f) {
                for (enemy in field.incomingEnemies) {
                    if (withinRange(tower, enemy, effRange)) {
                        val stillPoisoned = elapsedMs < enemy.dotExpiresAtMs
                        enemy.dotDps =
                            if (stillPoisoned) maxOf(enemy.dotDps, tower.effectiveDotDps) else tower.effectiveDotDps
                        enemy.dotExpiresAtMs = elapsedMs + tower.type.dotDurationMs
                    }
                }
            }

            if (!tower.type.isAttacker) continue
            if (tower.cooldownMs > 0f) continue

            val target = findTarget(tower, field.incomingEnemies, effRange) ?: continue
            fireAt(field, tower, target, effRange)
            tower.cooldownMs = tower.effectiveFireRateMs.toFloat()
        }
    }

    private fun findTarget(tower: TowerInstance, enemies: List<EnemyUnit>, effRange: Float): EnemyUnit? {
        // Units killed earlier this tick stay in the list until enemyPass, so skip them here.
        val inRange = enemies.filter { it.hp > 0f && withinRange(tower, it, effRange) }
        if (inRange.isEmpty()) return null

        val pool = if (tower.type.bonusDamageVsFlyerPct > 0f) {
            inRange.filter { it.type.damageResistancePct > 0f }.ifEmpty { inRange }
        } else inRange

        return when (tower.type.targeting) {
            TargetPriority.FIRST -> pool.maxByOrNull { it.progress }
            TargetPriority.STRONGEST -> pool.maxByOrNull { it.hp }
            TargetPriority.CLOSEST -> pool.minByOrNull { enemyDist(tower, it) }
        }
    }

    private fun fireAt(field: Battlefield, tower: TowerInstance, primary: EnemyUnit, effRange: Float) {
        val auraBonus = auraDamageMultiplier(field, tower)
        applyDamage(tower, primary, auraBonus)

        val px = primary.x
        val py = primary.y
        field.tracers.add(FxTracer(tower.x, tower.y, px, py))

        if (tower.type.splashRadius > 0f) {
            for (other in field.incomingEnemies) {
                if (other.instanceId == primary.instanceId) continue
                if (dist(px, py, other.x, other.y) <= tower.type.splashRadius) {
                    applyDamage(tower, other, auraBonus, fraction = SPLASH_DAMAGE_FRACTION)
                }
            }
        }

        if (tower.type.chainTargets > 0) {
            val already = mutableSetOf(primary.instanceId)
            var lastX = px; var lastY = py
            repeat(tower.type.chainTargets) {
                val next = field.incomingEnemies
                    .filter { it.instanceId !in already && it.hp > 0f }
                    .minByOrNull { dist(lastX, lastY, it.x, it.y) }
                if (next != null && dist(lastX, lastY, next.x, next.y) <= effRange) {
                    applyDamage(tower, next, auraBonus, fraction = CHAIN_DAMAGE_FRACTION)
                    field.tracers.add(FxTracer(lastX, lastY, next.x, next.y))
                    already.add(next.instanceId)
                    lastX = next.x; lastY = next.y
                }
            }
        }

        if (tower.type.stunChance > 0f && Random.nextFloat() < tower.type.stunChance) {
            primary.stunExpiresAtMs = elapsedMs + tower.type.stunDurationMs
        }
    }

    private fun applyDamage(
        tower: TowerInstance,
        target: EnemyUnit,
        auraBonus: Float,
        fraction: Float = 1f
    ) {
        var dmg = tower.effectiveDamage * modifier.damageMultiplier * (1f + auraBonus) * fraction
        dmg = if (tower.type.bonusDamageVsFlyerPct > 0f && target.type.damageResistancePct > 0f) {
            dmg * (1f + tower.type.bonusDamageVsFlyerPct / 100f)
        } else {
            dmg * (1f - target.type.damageResistancePct / 100f)
        }
        target.hp -= dmg.coerceAtLeast(0f)
    }

    private fun auraDamageMultiplier(field: Battlefield, tower: TowerInstance): Float {
        var bonus = 0f
        for (other in field.towers) {
            if (other.instanceId == tower.instanceId) continue
            if (other.effectiveAuraBonus > 0f && dist(other.x, other.y, tower.x, tower.y) <= other.type.auraRange) {
                bonus += other.effectiveAuraBonus / 100f
            }
        }
        return bonus
    }

    // -------------------------------------------------------------------
    // Enemies: spawns, movement, dot/heal ticks, leaks and deaths
    // -------------------------------------------------------------------

    private fun enemyPass(field: Battlefield, dtSeconds: Float) {
        val pending = field.pendingSpawns.iterator()
        while (pending.hasNext()) {
            val queued = pending.next()
            queued.delayMs -= dtSeconds * 1000f
            if (queued.delayMs <= 0f) {
                spawn(field, queued.type)
                pending.remove()
            }
        }

        // Healers pulse first so this tick's heal applies before hp checks
        for (healer in field.incomingEnemies) {
            if (healer.hp <= 0f || healer.type.healPerSecond <= 0f) continue
            for (target in field.incomingEnemies) {
                if (target.hp <= 0f) continue
                if (enemyDistProgress(healer, target) <= healer.type.healRadius / pathLength) {
                    target.hp = (target.hp + healer.type.healPerSecond * dtSeconds).coerceAtMost(target.type.maxHp)
                }
            }
        }

        val toRemove = mutableListOf<EnemyUnit>()
        for (enemy in field.incomingEnemies) {
            if (elapsedMs < enemy.dotExpiresAtMs) {
                enemy.hp -= enemy.dotDps * dtSeconds
            }

            if (enemy.hp <= 0f) {
                field.gold += enemy.type.bountyGold
                toRemove.add(enemy)
                continue
            }

            val stunned = elapsedMs < enemy.stunExpiresAtMs
            val slowed = elapsedMs < enemy.slowExpiresAtMs
            val speedFactor = if (stunned) 0f else if (slowed) (1f - enemy.slowFactor) else 1f
            val progressDelta = (enemy.type.speed * modifier.speedMultiplier * speedFactor * dtSeconds) / pathLength
            enemy.progress += progressDelta

            if (enemy.progress >= 1f) {
                field.lives = (field.lives - enemy.type.livesDamage).coerceAtLeast(0)
                toRemove.add(enemy)
                continue
            }

            val (x, y) = PathMath.pointAtProgress(map.pathPoints, segmentLengths, enemy.progress)
            enemy.x = x
            enemy.y = y
        }
        field.incomingEnemies.removeAll(toRemove)
    }

    private fun cleanupTracers(field: Battlefield, dtSeconds: Float) {
        for (t in field.tracers) t.ageMs += dtSeconds * 1000f
        field.tracers.removeAll { it.ageMs > 160f }
    }

    // -------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------

    private fun withinRange(tower: TowerInstance, enemy: EnemyUnit, effRange: Float): Boolean =
        enemyDist(tower, enemy) <= effRange

    private fun enemyDist(tower: TowerInstance, enemy: EnemyUnit): Float =
        dist(tower.x, tower.y, enemy.x, enemy.y)

    private fun enemyDistProgress(a: EnemyUnit, b: EnemyUnit): Float = abs(a.progress - b.progress)

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}

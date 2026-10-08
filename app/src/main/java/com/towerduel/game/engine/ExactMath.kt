package com.towerduel.game.engine

// The few functions the simulation needs beyond plain arithmetic, in the one form that gives the
// same bits on every phone. kotlin.math hands sin, cos, atan2 and pow to the processor's own
// routines, and those differ in the last digit from one chip to the next: two phones playing the
// same match command for command would drift apart within a minute. StrictMath is slower and the
// same everywhere. (Addition, multiplication, division and sqrt are exact by the language's own
// rules and need no such care.)
//
// Engine code calls these by their plain names. Do not import kotlin.math.sin, cos, atan2 or pow
// into anything under engine/: an import wins over these.

internal fun sin(x: Float): Float = StrictMath.sin(x.toDouble()).toFloat()

internal fun cos(x: Float): Float = StrictMath.cos(x.toDouble()).toFloat()

internal fun atan2(y: Float, x: Float): Float = StrictMath.atan2(y.toDouble(), x.toDouble()).toFloat()

internal fun Float.pow(n: Int): Float = StrictMath.pow(toDouble(), n.toDouble()).toFloat()

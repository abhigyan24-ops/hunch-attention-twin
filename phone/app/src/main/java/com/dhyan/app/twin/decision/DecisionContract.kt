package com.dhyan.app.twin.decision

const val DECISION_CONTRACT_VERSION = 1
enum class Action { ALLOW, DELAY, DIGEST, SILENT }
enum class ActuationStatus { HELD, SNOOZED, SCHEDULED, RELEASED, EXPIRED, FAILED_OPEN, ACTUATION_UNKNOWN }
enum class PolicyMode { OFF, DRY_RUN, LIVE }
enum class KillSource { USER_TILE, USER_NOTIFICATION, USER_SETTINGS, SYSTEM }

package com.dhyan.app.personas

/** Bundled fictional Stage 1 truth; no filesystem or Android assets needed at runtime. */
object PersonaCatalog {
    private val rahul = """
{
  "name": "Rahul",
  "role": "developer",
  "pickupProb": {
    "social_notif|coding": 0.62,
    "msg_known|coding": 0.55,
    "promo|coding": 0.12
  },
  "dwell": {
    "social": {
      "mu": 2.6,
      "sigma": 0.7
    },
    "short_video": {
      "mu": 2.8,
      "sigma": 0.9
    }
  },
  "resume": {
    "coding": {
      "mu": 2,
      "sigma": 0.6
    }
  },
  "fatigueMultiplier": [
    1,
    1.2,
    1.5
  ],
  "focusPeaks": [
    10,
    11,
    15,
    21
  ],
  "selfInitiatedPerHour": 1.4,
  "importantSenders": [
    "mom",
    "teammate_a"
  ]
}
""".trimIndent()
    private val anika = """
{
  "name": "Anika",
  "role": "student",
  "assumed": true,
  "_notes": "All numeric values are assumed test ground truth. lecture_watch pickup is a low-drift proxy; short_video models long Shorts dwell. Neither score is an observed probability for a real person.",
  "pickupProb": {
    "lecture_watch|studying": 0.04,
    "shorts|studying": 0.78,
    "social_notif|studying": 0.58,
    "msg_known|studying": 0.38,
    "promo|studying": 0.08
  },
  "dwell": {
    "learning": {
      "mu": 3,
      "sigma": 0.35
    },
    "short_video": {
      "mu": 3.2,
      "sigma": 0.85
    },
    "social": {
      "mu": 2.3,
      "sigma": 0.7
    }
  },
  "resume": {
    "studying": {
      "mu": 1.7,
      "sigma": 0.6
    }
  },
  "fatigueMultiplier": [
    1,
    1.15,
    1.4
  ],
  "focusPeaks": [
    21,
    22,
    23,
    0
  ],
  "selfInitiatedPerHour": 1.2,
  "importantSenders": [
    "family_a",
    "study_group_a"
  ]
}
""".trimIndent()
    private val kabir = """
{
  "name": "Kabir",
  "role": "gamer-dev",
  "assumed": true,
  "_notes": "Only selfInitiatedPerHour=4.5 is specified in 15.2. All other numeric values are assumed test ground truth, not observations or calibrated estimates.",
  "pickupProb": {
    "social_notif|coding": 0.1,
    "msg_known|coding": 0.12,
    "promo|coding": 0.03
  },
  "dwell": {
    "social": {
      "mu": 2,
      "sigma": 0.7
    },
    "short_video": {
      "mu": 2.3,
      "sigma": 0.8
    },
    "games": {
      "mu": 3.2,
      "sigma": 0.6
    }
  },
  "resume": {
    "coding": {
      "mu": 1.5,
      "sigma": 0.5
    },
    "gaming": {
      "mu": 0.8,
      "sigma": 0.4
    }
  },
  "fatigueMultiplier": [
    1,
    1.2,
    1.5
  ],
  "focusPeaks": [
    10,
    15,
    22
  ],
  "selfInitiatedPerHour": 4.5,
  "importantSenders": [
    "family_a",
    "teammate_a"
  ]
}
""".trimIndent()
    private val jsonByName = mapOf("Rahul" to rahul, "Anika" to anika, "Kabir" to kabir)
    fun byName(name: String): PersonaDef = PersonaLoader.load(jsonByName[name] ?: throw IllegalArgumentException("Unknown persona"))
    fun all(): List<PersonaDef> = frozen(jsonByName.keys.map(::byName))
}

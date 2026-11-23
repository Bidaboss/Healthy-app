package com.bidaboss.healthy.health.ui

/** « 7 h 30 », « 45 min ». */
internal fun hoursText(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h %02d".format(minutes % 60)

/** « 23 h 05 » : l'heure d'un coucher ou d'un réveil. */
internal fun clockText(hour: Int, minute: Int): String = "$hour h %02d".format(minute)

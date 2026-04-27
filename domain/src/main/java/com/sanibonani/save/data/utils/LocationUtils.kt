package com.sanibonani.save.data.utils

import kotlin.math.*

object LocationUtils {
    /**
     * Encodes a latitude and longitude into a geohash string.
     */
    fun encodeGeohash(latitude: Double, longitude: Double, precision: Int = 9): String {
        val base32 = "0123456789bcdefghjkmnpqrstuvwxyz"
        val latInterval = doubleArrayOf(-90.0, 90.0)
        val lonInterval = doubleArrayOf(-180.0, 180.0)
        val geohash = StringBuilder()
        var isEven = true
        var bit = 0
        var ch = 0

        while (geohash.length < precision) {
            val mid: Double
            if (isEven) {
                mid = (lonInterval[0] + lonInterval[1]) / 2
                if (longitude > mid) {
                    ch = ch or (1 shl (4 - bit))
                    lonInterval[0] = mid
                } else {
                    lonInterval[1] = mid
                }
            } else {
                mid = (latInterval[0] + latInterval[1]) / 2
                if (latitude > mid) {
                    ch = ch or (1 shl (4 - bit))
                    latInterval[0] = mid
                } else {
                    latInterval[1] = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(base32[ch])
                bit = 0
                ch = 0
            }
        }
        return geohash.toString()
    }
}

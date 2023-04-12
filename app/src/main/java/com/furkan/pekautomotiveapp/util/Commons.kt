package com.furkan.pekautomotiveapp.util

class Commons {
    companion object{
        fun intToIp(i: Int): String {
            return (i and 0xFF).toString() + "." +
                    (i shr 8 and 0xFF) + "." +
                    (i shr 16 and 0xFF) + "." +
                    (i shr 24 and 0xFF)
        }
    }
}
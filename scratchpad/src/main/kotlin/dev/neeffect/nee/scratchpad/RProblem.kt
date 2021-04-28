package dev.neeffect.nee.scratchpad

interface A1<T : A1<T>> {
    fun get(): T
    fun getVal(): String
    fun setVal(s: String): A1<T> = this.let { parent ->
        object : A1<T> {
            override fun get(): T = parent.get()
            override fun getVal(): String = s
        }
    }
}

interface A2<T : A2<T>> {
    fun get(): T
    fun getVal2(): String
    fun setVal2(s: String): A2<T> = this.let { parent ->
        object : A2<T> {
            override fun get(): T = parent.get()
            override fun getVal2(): String = s
        }
    }
}


fun main() {
    data class Test1(val x: String, val y: String) : A1<Test1>, A2<Test1> {
        override fun get(): Test1 = this

        override fun getVal(): String = x

        override fun getVal2(): String = y

    }

    val b1 = Test1("hello", "world")
    println(b1.getVal())
    println(b1.getVal2())
    val b2 = b1.setVal("uuu").get()
    println(b2.getVal())
    println(b2.getVal2())
    val b3 = b2.setVal("aaa").get()
    println(b3.getVal())
    println(b3.getVal2())
}
